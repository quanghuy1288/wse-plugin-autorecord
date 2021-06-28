package com.wowza.wms.plugin;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import org.joda.time.DateTime;
import org.json.simple.JSONObject;
import com.wowza.wms.livestreamrecord.manager.StreamRecorder;

@DatabaseTable(tableName = "file_recording")
public class FileRecording {
	@DatabaseField(generatedId = true)
    protected Integer id;

    @DatabaseField(columnName = "app_instance_name", canBeNull = true)
    protected String appInstanceName;
    
    @DatabaseField(columnName = "start_time", canBeNull = true)
    protected DateTime startTime;
    
    @DatabaseField(columnName = "end_time", canBeNull = true)
    protected DateTime endTime;

    @DatabaseField(columnName = "updated_time", canBeNull = true)
    protected DateTime updatedTime;
    
    @DatabaseField(columnName = "file_duration_millis", canBeNull = true)
    protected long fileDurationMillisecond;
    
    @DatabaseField(columnName = "uuid", canBeNull = true)
    protected String uuid;
        
    @DatabaseField(columnName = "app_name", canBeNull = true)
    protected String appName;
    
    @DatabaseField(columnName = "stream_name", canBeNull = true)
    protected String streamName;

    @DatabaseField(columnName = "source_stream_name", canBeNull = true)
    protected String sourceStreamName;
    
    @DatabaseField(columnName = "file_path", canBeNull = true)
    protected String filePath;
    
    @DatabaseField(columnName = "file_size", canBeNull = true)
    protected long fileSize;
        
    @DatabaseField(columnName = "status", canBeNull = true)
    protected FileRecordingStatus status;
    
    @DatabaseField(columnName = "error", canBeNull = true)
    protected String error;

    public FileRecording() {
        this.id = null;
        this.appName = null;
        this.appInstanceName = null;
        this.streamName = null;
        this.sourceStreamName = null;

        this.startTime = null;
        this.endTime = null;
        this.filePath = null;
        this.fileSize = 0;
        this.fileDurationMillisecond = 0;
        this.status = FileRecordingStatus.RECORDING;
        this.startTime = DateTime.now();
        this.updatedTime = DateTime.now();
        this.error = null;
    }

    public FileRecording(StreamRecorder recorder) {
        if (recorder == null)
            return;

        this.id = null;
        this.appName = recorder.getAppInstance().getApplication().getName();
        this.appInstanceName = recorder.getAppInstance().getName();
        this.streamName = recorder.getStreamName();
        this.sourceStreamName = getSourceStreamName(this.streamName);

        this.startTime = DateTime.now();
        this.updatedTime = DateTime.now();
        this.endTime = null;
        this.filePath = recorder.getFilePath();
        this.fileSize = recorder.getCurrentSize();
        this.fileDurationMillisecond = recorder.getCurrentDuration();
        this.status = FileRecordingStatus.RECORDING;
        this.error = recorder.getErrorString();
    }

    public void startAppend(StreamRecorder recorder) {
        update(recorder);
        this.status = FileRecordingStatus.RECORDING;
    }

    private void update(StreamRecorder recorder) {
        this.filePath = recorder.getFilePath();
        this.error = recorder.getErrorString();
        this.updatedTime = DateTime.now();
    }

    public void updateOnFinish(StreamRecorder recorder) {
        update(recorder);
        this.endTime = DateTime.now();
        this.fileDurationMillisecond += recorder.getCurrentDuration();
        this.fileSize = recorder.getCurrentSize();
        if (error == null)
            status = FileRecordingStatus.SUCCESS;
        else
            status = FileRecordingStatus.FAILED;
    }

    @SuppressWarnings("unchecked")
	public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("uuid", uuid);
        obj.put("appName", appName);
        obj.put("appInstanceName", appInstanceName);
        obj.put("streamName", streamName);
        if (startTime != null)
            obj.put("startTime", startTime.toString("yyyy-MM-dd-HH.mm.ss.SSS"));
        else
            obj.put("startTime", "");
        if (endTime != null)
            obj.put("endTime", endTime.toString("yyyy-MM-dd-HH.mm.ss.SSS"));
        else
            obj.put("endTime", "");
        obj.put("filePath", filePath);
        obj.put("fileSizeByte", fileSize);
        obj.put("fileDurationMillisecond", fileDurationMillisecond);
        obj.put("status", status);
        obj.put("error", error);
        return obj;
    }

    public String toString() {
        return toJson().toString();
    }

    public enum FileRecordingStatus {
        RECORDING, SUCCESS, FAILED
    }

    public FileRecordingStatus getStatus() {
        return status;
    }

    private String getSourceStreamName(String streamName){
        String divider = "__";
        int divIndex = streamName.lastIndexOf(divider);
        if(divIndex == -1){
            return streamName;
        } else{
            return streamName.substring(0, divIndex);
        }
    }
}
