package com.vydia.RNUploader

import android.content.Context
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter
import net.gotev.uploadservice.data.UploadInfo
import net.gotev.uploadservice.network.ServerResponse
import net.gotev.uploadservice.observer.request.RequestObserverDelegate

class GlobalRequestObserverDelegate(reactContext: ReactApplicationContext) : RequestObserverDelegate {

  private class ProgressEvent(var time: Long, var progress: Int) {
  }

  private val TAG = "UploadReceiver"

  private var reactContext: ReactApplicationContext = reactContext

  private val lastProgressEventRecords: HashMap<String, ProgressEvent> = HashMap()

  override fun onCompleted(context: Context, uploadInfo: UploadInfo) {
  }

  override fun onCompletedWhileNotObserving() {
  }

  override fun onError(context: Context, uploadInfo: UploadInfo, exception: Throwable) {
    val params = Arguments.createMap()
    params.putString("id", uploadInfo.uploadId)

    // Make sure we do not try to call getMessage() on a null object
    if (exception != null) {
      params.putString("error", exception.message)
    } else {
      params.putString("error", "Unknown exception")
    }

    resetLastProgressEventRecord(uploadInfo.uploadId);
    sendEvent("error", params, context)
  }

  override fun onProgress(context: Context, uploadInfo: UploadInfo) {
    val params = Arguments.createMap()
    params.putString("id", uploadInfo.uploadId)
    params.putInt("progress", uploadInfo.progressPercent) //0-100

    if (canDispatchProgressEvent(uploadInfo.uploadId, uploadInfo.progressPercent)) {
      sendEvent("progress", params, context)
    }
  }

  override fun onSuccess(context: Context, uploadInfo: UploadInfo, serverResponse: ServerResponse) {
    val headers = Arguments.createMap()
    for ((key, value) in serverResponse.headers) {
      headers.putString(key, value)
    }
    val params = Arguments.createMap()
    params.putString("id", uploadInfo.uploadId)
    params.putInt("responseCode", serverResponse.code)
    params.putString("responseBody", serverResponse.bodyString)
    params.putMap("responseHeaders", headers)

    resetLastProgressEventRecord(uploadInfo.uploadId);
    sendEvent("completed", params, context)
  }

  /**
   * Sends an event to the JS module.
   */
  private fun sendEvent(eventName: String, params: WritableMap?, context: Context) {
    reactContext?.getJSModule(RCTDeviceEventEmitter::class.java)?.emit("RNFileUploader-$eventName", params)
            ?: Log.e(TAG, "sendEvent() failed due reactContext == null!")
  }

  /**
   * dispatch progress event on progress changes and only after 1 second to last event
   */
  private fun canDispatchProgressEvent(uploadId: String, progressPer: Int): Boolean {
    var progress = 1;
    if (progressPer > 1) {
      progress = progressPer;
    }
    val lastEventRecord = lastProgressEventRecords[uploadId];
    var lastProgress: Int = 0;
    var lastTime: Long = 0;
    if (lastEventRecord != null) {
      lastProgress = lastEventRecord.progress;
      lastTime = lastEventRecord.time;
    }
    val currentTime = System.currentTimeMillis()
    if (lastEventRecord == null || lastProgress > progress || (lastProgress <= progress - 1 && currentTime - lastTime > 1 * 1000)) {
      lastProgressEventRecords[uploadId] = ProgressEvent(currentTime, progress);
      return true;
    }
    return false;
  }

  /**
   * reset upload progress event record
   */
  private fun resetLastProgressEventRecord(uploadId: String) {
    lastProgressEventRecords.remove(uploadId);
  }
}
