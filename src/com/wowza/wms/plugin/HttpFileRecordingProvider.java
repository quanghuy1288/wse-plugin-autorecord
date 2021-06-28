package com.wowza.wms.plugin;

import com.wowza.wms.http.HTTProvider2Base;
import com.wowza.wms.http.IHTTPRequest;
import com.wowza.wms.http.IHTTPResponse;
import com.wowza.wms.vhost.IVHost;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class HttpFileRecordingProvider extends HTTProvider2Base {
    private DbManager db = null;

    public void onHTTPRequest(IVHost vhost, IHTTPRequest req, IHTTPResponse resp) {
        if (!doHTTPAuthentication(vhost, req, resp))
            return;

        if (db == null) {
            String DATABASE_URL = "jdbc:sqlite:" + vhost.getHomePath() + "/autorecord.db";
            db = DbManager.getInstance(DATABASE_URL);
        }

        resp.setHeader("Content-Type", "application/json");
        String streamName = req.getParameter("stream");
        String sourceStreamName = req.getParameter("source_stream");
        try {
            List<FileRecording> files;
            if (streamName != null && !streamName.trim().isEmpty())
                files = db.getRecordingFileListByStreamName(streamName);
            else if (sourceStreamName != null && !sourceStreamName.trim().isEmpty())
                files = db.getRecordingFileListBySourceStream(sourceStreamName);
            else
                files = db.getAllRecordingFileList();

            JSONObject result = new JSONObject();
            JSONArray fileJsonArray = new JSONArray();
            for (FileRecording file:  files){
                fileJsonArray.add(file.toJson());
            }
            result.put("files", fileJsonArray);
            result.put("files_count", files.size());

            resp.setResponseCode(200);
            OutputStream out = resp.getOutputStream();
            byte[] outBytes = result.toJSONString().getBytes();
            out.write(outBytes);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
