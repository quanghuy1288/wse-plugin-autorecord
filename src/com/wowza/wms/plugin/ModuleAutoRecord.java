/*
 * This code and all components (c) Copyright 2006 - 2019, Wowza Media Systems, LLC. All rights reserved.
 * This code is licensed pursuant to the Wowza Public License version 1.0, available at www.wowza.com/legal.
 */
package com.wowza.wms.plugin;

import java.util.List;
import java.util.regex.PatternSyntaxException;

import com.wowza.util.StringUtils;
import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.livestreamrecord.manager.IStreamRecorder;
import com.wowza.wms.livestreamrecord.manager.StreamRecorderParameters;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.logging.WMSLoggerIDs;
import com.wowza.wms.module.ModuleBase;
import com.wowza.wms.stream.IMediaStream;
import com.wowza.wms.stream.MediaStreamActionNotifyBase;
import com.wowza.wms.vhost.IVHost;
import com.wowza.wms.livestreamrecord.manager.StreamRecorder;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.json.simple.JSONObject;

public class ModuleAutoRecord extends ModuleBase
{
	public static final String CLASSNAME = "ModuleAutoRecord";
	public static String DATABASE_URL = null;

	private enum RecordType
	{
		all, source, transcoder, allow, whitelist, deny, blacklist, none;
	}

	private WMSLogger logger = null;
	private IApplicationInstance appInstance = null;
	private IVHost vhost = null;
	private StreamListener actionNotify = new StreamListener();
	

	private String namesStr = null;
	private String namesStrDelimiter = "(\\||,)";
	private RecordType recordType = RecordType.all;

	private Boolean debugLog = false;
	private StreamRecorderParameters recordParams = null;
	private boolean shutDownRecorderOnUnPublish = true;
	private String callBackUrl = null;
	private DbManager db = null;

	public void onAppCreate(IApplicationInstance appInstance)
	{
		logger = WMSLoggerFactory.getLoggerObj(appInstance);
		logger.info(CLASSNAME + ".onAppCreate[" + appInstance.getContextStr() + "] Build #6", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);

		this.appInstance = appInstance;
		vhost = appInstance.getVHost();

		DATABASE_URL = "jdbc:sqlite:" + appInstance.getVHost().getHomePath() + "/autorecord.db";
		db = DbManager.getInstance(DATABASE_URL);

		// main streamRecorder debugLog property
		debugLog = appInstance.getStreamRecorderProperties().getPropertyBoolean("streamRecorderDebugEnable", debugLog);

		// local debugLog property
		debugLog = appInstance.getStreamRecorderProperties().getPropertyBoolean("streamRecorderAutoRecordDebugLog", debugLog);

		if (logger.isDebugEnabled())
			debugLog = true;

		String streamType = appInstance.getStreamType();
		if(streamType.contains("-record"))
		{
			String newStreamType = streamType.replace("-record", "");
			appInstance.setStreamType(newStreamType);

			logger.info(CLASSNAME + ".onAppCreate[" + appInstance.getContextStr() + "] Application has " + streamType + " stream type set. Changing to " + newStreamType + " stream type to prevent conflict.", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
		}

		try
		{
			recordType = RecordType.valueOf(appInstance.getStreamRecorderProperties().getPropertyStr("streamRecorderRecordType", recordType.toString()).toLowerCase());
		}
		catch (Exception e)
		{
			logger.warn(CLASSNAME + ".onAppCreate[" + appInstance.getContextStr() + "] streamRecorderRecordType value not correct. Disabling Automatic recording", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
			recordType = RecordType.none;
		}
		namesStr = appInstance.getStreamRecorderProperties().getPropertyStr("streamRecorderStreamNames", null);
		namesStrDelimiter = appInstance.getStreamRecorderProperties().getPropertyStr("streamRecorderStreamNamesDelimiter", namesStrDelimiter);
		callBackUrl = appInstance.getStreamRecorderProperties().getPropertyStr("streamRecorderCallbackUrl", null);

		// Create a new StreamRecorderParameters object with defaults set via StreamRecorder Properties in the application.
		recordParams = new StreamRecorderParameters(appInstance);

		boolean recordAllStreams = appInstance.getStreamRecorderProperties().getPropertyBoolean("streamRecorderRecordAllStreams", recordType == RecordType.all);
		recordAllStreams = appInstance.getProperties().getPropertyBoolean("streamRecorderRecordAllStreams", recordAllStreams);
		if(recordAllStreams)
			recordType = RecordType.all;

		if (recordType == RecordType.all)
		{
			// Automatically record all streams as they are published.
			// Recorders will only be created when a stream is first published.
			//appInstance.getVHost().getLiveStreamRecordManager().startRecording(appInstance, recordParams);
			logger.info(CLASSNAME + ".onAppCreate[" + appInstance.getContextStr() + "] recording all streams", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
		}

		// Recorders will normally remain in memory after the stream is unpublished so they can easily be reused.
		// Use streamRecorderStopRecorderOnUnPublish to stop the recorder when the stream is unpublished.
		// The recorder will then be recreated when the stream is restarted.
		shutDownRecorderOnUnPublish = appInstance.getStreamRecorderProperties().getPropertyBoolean("streamRecorderShutDownRecorderOnUnPublish", shutDownRecorderOnUnPublish);
		shutDownRecorderOnUnPublish = appInstance.getProperties().getPropertyBoolean("streamRecorderShutDownRecorderOnUnPublish", shutDownRecorderOnUnPublish);
		logger.info(CLASSNAME + ".onAppCreate[" + appInstance.getContextStr() + "] recorders will " + (!shutDownRecorderOnUnPublish ? "not " : "") + "be shut down on unpublish", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
	}

	class StreamListener extends MediaStreamActionNotifyBase
	{
		@Override
		public void onPublish(IMediaStream stream, String streamName, boolean isRecord, boolean isAppend)
		{
			logger.info(CLASSNAME + ".onPublish: " + streamName);
			if(vhost.getLiveStreamRecordManager().getRecorder(appInstance, streamName) != null)
				return;

			boolean matchFound = false;
			boolean canRecord = false;
			switch (recordType)
			{
			case all:
				canRecord = true;
				break;

			case allow:
				matchFound = checkNames(streamName);
				if (matchFound)
					canRecord = true;
				break;

			case deny:
				matchFound = checkNames(streamName);
				if (!matchFound)
					canRecord = true;
				break;

			case source:
				if (!stream.isTranscodeResult())
					canRecord = true;
				break;

			case transcoder:
				if (stream.isTranscodeResult())
					canRecord = true;
				break;

			case none:
			default:
				break;
			}

			if (canRecord)
			{
				if (debugLog)
					logger.info(CLASSNAME + ".onPublish [" + appInstance.getContextStr() + "/" + streamName + "] starting recording. RecordType: " + recordType, WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
				vhost.getLiveStreamRecordManager().startRecording(appInstance, streamName, recordParams);

				// TODO find old matched file recording
				logger.info(CLASSNAME + " start recording: ");
				StreamRecorder recorder = (StreamRecorder)vhost.getLiveStreamRecordManager().getRecorder(appInstance, streamName);
				FileRecording file = null;
				try {
					file = db.getFileRecording(recorder.getFilePath());
					logger.warn(CLASSNAME + " file path: " + recorder.getFilePath());
					if (file == null){
						logger.warn(CLASSNAME + " no fileRecording found for path: " + recorder.getFilePath());
						file = new FileRecording(recorder);
						db.createFileRecording(file);
						logger.warn(CLASSNAME + " createFileRecording: " + file.toString());
						callback(buildFileRecordingJson(file, "create"));
					} else{
						file.startAppend(recorder);
						db.updateFileRecording(file);
						logger.warn(CLASSNAME + " updateFileRecording: " + file.toString());
						callback(buildFileRecordingJson(file, "update"));
					}
					logger.info(CLASSNAME + " createRecordingFile");
				} catch (Exception e) {
					e.printStackTrace();
					logger.warn(e.getMessage());
				}
			}
			else if(debugLog)
				logger.info(CLASSNAME + ".onPublish [" + appInstance.getContextStr() + "/" + streamName + "] not starting recording. RecordType: " + recordType, WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
		}

		private boolean checkNames(String streamName)
		{
			boolean matchFound = false;

			if (!StringUtils.isEmpty(namesStr))
			{
				while (true)
				{
					if (namesStr.equals("*"))
					{
						if (debugLog)
							logger.info(CLASSNAME + ".checkNames [" + appInstance.getContextStr() + "/" + streamName + "] match found against *", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
						matchFound = true;
						break;
					}
					// Read a pipe (|) or comma separated list of names from properties and start recorder if it matches
					String[] names = namesStr.split(namesStrDelimiter);
					for (String name : names)
					{
						name = name.trim();
						if (debugLog)
							logger.info(CLASSNAME + ".checkNames [" + appInstance.getContextStr() + "/" + streamName + "] regex check against " + name, WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
						// wildcard suffix match
						if (name.startsWith("*"))
						{
							if (debugLog)
								logger.info(CLASSNAME + ".checkNames [" + appInstance.getContextStr() + "/" + streamName + "] wildcard suffix check against " + name, WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
							name = name.substring(1);
							if (streamName.endsWith(name))
							{
								if (debugLog)
									logger.info(CLASSNAME + ".checkNames [" + appInstance.getContextStr() + "/" + streamName + "] wildcard suffix match found against " + name, WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
								matchFound = true;
								break;
							}
						}
						// wildcard prefix match
						if (name.endsWith("*"))
						{
							if (debugLog)
								logger.info(CLASSNAME + ".checkNames [" + appInstance.getContextStr() + "/" + streamName + "] wildcard prefix check against " + name, WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
							name = name.substring(0, name.length() - 1);
							if (streamName.startsWith(name))
							{
								if (debugLog)
									logger.info(CLASSNAME + ".checkNames [" + appInstance.getContextStr() + "/" + streamName + "] wildcard prefix match found against " + name, WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
								matchFound = true;
								break;
							}
						}
						// regex match
						try {
							if (streamName.matches(name))
							{
								if (debugLog)
									logger.info(CLASSNAME + ".checkNames [" + appInstance.getContextStr() + "/" + streamName + "] regex match found against " + name, WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
								matchFound = true;
								break;
							}
						} catch (PatternSyntaxException e) {
							if (debugLog)
								logger.warn(CLASSNAME + ".checkNames [" + appInstance.getContextStr() + "/" + streamName + "] exception: " + e.getMessage(), WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
						}
					}
					break;
				}
			}
			else
			{
				if (debugLog)
					logger.info(CLASSNAME + ".checkNames [" + appInstance.getContextStr() + "/" + streamName + "] streamRecorderStreamNames list is empty", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
			}

			return matchFound;
		}

		@Override
		public void onStop(IMediaStream stream) {
			logger.info(CLASSNAME + " StreamListener.onStop stream: " + stream.getName());
		}

		@Override
		public void onUnPublish(IMediaStream stream, String streamName, boolean isRecord, boolean isAppend)
		{
			logger.info(CLASSNAME + " onUnPublish");
			if(true)
			{
				logger.info(CLASSNAME + " onUnPublish2");
				if (debugLog)
					logger.info(CLASSNAME + ".onUnPublish [" + appInstance.getContextStr() + "/" + streamName + "] shutting down recorder", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);

				try {
					logger.info(CLASSNAME + " onUnPublish3: " + streamName);
					List<FileRecording> files = db.getRecordingFileListByStreamName(streamName);

					if (!files.isEmpty()){
						logger.info(CLASSNAME + " onUnPublish4");
						IStreamRecorder recorder = vhost.getLiveStreamRecordManager().getRecorder(appInstance, streamName);
						FileRecording file = files.get(0);
						file.updateOnFinish((StreamRecorder) recorder);
						db.updateFileRecording(file);
						callback(buildFileRecordingJson(file, "finish"));
						logger.info(CLASSNAME + " onUnPublish updateRecordingFile");
					}
				} catch (Exception e) {
					e.printStackTrace();
					logger.warn(e.getMessage());
				}
				vhost.getLiveStreamRecordManager().stopRecording(appInstance, streamName);
			}
		}
	}

	public void onStreamCreate(IMediaStream stream)
	{
		logger.info(CLASSNAME + ".onStreamCreate addClientListener");
		stream.addClientListener(actionNotify);
	}

	private JSONObject buildFileRecordingJson(FileRecording file, String type){
		JSONObject content = new JSONObject();
		content.put("type", type);
		content.put("file", file.toJson());
		return content;
	}

	private void callback(JSONObject jsonObj){
		if(callBackUrl == null || callBackUrl.equalsIgnoreCase("")) {
			logger.warn(CLASSNAME + "cannot callback to empty url: " + callBackUrl);
			return;
		}
		
		HttpClient httpClient = HttpClientBuilder.create().build();
		try {
			HttpPost request = new HttpPost(callBackUrl);
			StringEntity params = new StringEntity(jsonObj.toJSONString());
			request.addHeader("content-type", "application/json");
			request.setEntity(params);
			HttpResponse response = httpClient.execute(request);
			logger.warn(CLASSNAME + "callback: " + callBackUrl + jsonObj.toJSONString() + " ----- " + response.getStatusLine() + " ----- " + response.toString());
		} catch (Exception ex) {
			logger.warn(ex.getMessage());
		} finally {
			// @Deprecated httpClient.getConnectionManager().shutdown();
		}
	}
}
