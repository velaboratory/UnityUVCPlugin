#include <jni.h>
#include "libUVCCamera.h"
#include <cstring>
#include <pthread.h>
//this is where we are going to start the thread

//char *mUsbFs;
uvc_context_t *mContext;
//int mFd;
uvc_device_t *mDevice;
uvc_device_handle_t *mDeviceHandle;
uvc_frame_desc_t *frame_desc;
uvc_stream_ctrl_t ctrl; //set the requested format
uvc_frame_t * frame_transfer_buffer;
uvc_frame_t * jpeg_transfer_buffer;
pthread_mutex_t lock;

int running = 0;

void uvc_preview_frame_callback(uvc_frame_t *frame, void *vptr_args){
    pthread_mutex_lock(&lock);
    uvc_duplicate_frame(frame,jpeg_transfer_buffer);
    uvc_mjpeg2rgb(frame,frame_transfer_buffer);
    pthread_mutex_unlock(&lock);
}
#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT int JNICALL Java_edu_uga_engr_vel_unityuvcplugin_UnityUVCPlugin_getFrame(
                        JNIEnv* env, jobject thisObject,
                        jbyteArray frame_buffer, jbyteArray jpeg_buffer){

                            if(running == 0){
                                return -1;
                            }

                            jsize frame_len = env->GetArrayLength(frame_buffer);
                            jbyte* frame_bufferPtr = env->GetByteArrayElements(frame_buffer, NULL);

                            jsize jpeg_len = env->GetArrayLength(jpeg_buffer);
                            jbyte* jpeg_bufferPtr = env->GetByteArrayElements(jpeg_buffer, NULL);

                            //lock the transfer buffer, do the copy
                            pthread_mutex_lock(&lock);
                            std::memcpy(frame_bufferPtr,frame_transfer_buffer->data,frame_transfer_buffer->actual_bytes);
                            std::memcpy(jpeg_bufferPtr,jpeg_transfer_buffer->data,jpeg_transfer_buffer->actual_bytes);
                            pthread_mutex_unlock(&lock);
                            return jpeg_transfer_buffer->actual_bytes;

                        }

JNIEXPORT int JNICALL Java_edu_uga_engr_vel_unityuvcplugin_UnityUVCPlugin_openCamera(
                        JNIEnv* env, jobject thisObject,
                        jint vid,
                        jint pid,
                        jint fd,
                        jint busNum,
                        jint devAddr,
                        jstring usbfs_str){

    uvc_error_t result;
    const char *usbfs = env->GetStringUTFChars(usbfs_str,
                                               JNI_FALSE);
    result = uvc_init2(&mContext,
                       NULL,
                       usbfs);

    result = uvc_get_device_with_fd(mContext,
                                    &mDevice,
                                    vid,
                                    pid,
                                    NULL,
                                    fd,
                                    busNum,
                                    devAddr); //get the device
    result = uvc_open(mDevice, &mDeviceHandle); //open the device (not much here)
    return result;

}

JNIEXPORT int JNICALL Java_edu_uga_engr_vel_unityuvcplugin_UnityUVCPlugin_setExposure(
        JNIEnv* env, jobject thisObject,
        jint value){
        int r;
        if(running){

                //r = uvc_set_ae_mode(mDeviceHandle, 0/* & 0xff*/);
                r = uvc_set_exposure_abs(mDeviceHandle, value/* & 0xff*/);
                //r = uvc_set_gain(mDeviceHandle, gain);

        }
        return 0;
}

JNIEXPORT int JNICALL Java_edu_uga_engr_vel_unityuvcplugin_UnityUVCPlugin_setGain(
        JNIEnv* env, jobject thisObject,
        jint value){
int r;
if(running){

//r = uvc_set_ae_mode(mDeviceHandle, 0/* & 0xff*/);
//r = uvc_set_exposure_abs(mDeviceHandle, exposure/* & 0xff*/);
r = uvc_set_gain(mDeviceHandle, value);

}
return 0;
}

JNIEXPORT int JNICALL Java_edu_uga_engr_vel_unityuvcplugin_UnityUVCPlugin_setAutoExposure(
        JNIEnv* env, jobject thisObject,
        jint value){
int r;
if(running){

r = uvc_set_ae_mode(mDeviceHandle, value/* & 0xff*/);
//r = uvc_set_exposure_abs(mDeviceHandle, exposure/* & 0xff*/);
//r = uvc_set_gain(mDeviceHandle, gain);

}
return 0;
}

JNIEXPORT int JNICALL Java_edu_uga_engr_vel_unityuvcplugin_UnityUVCPlugin_startCamera(
                        JNIEnv* env, jobject thisObject,
                        jint width, jint height, jint min_fps, jint max_fps,
                        jint mode, jfloat bandwidth){

    uvc_error_t result;

    result = uvc_get_stream_ctrl_format_size_fps(mDeviceHandle,
                                                 &ctrl,
                                                 UVC_FRAME_FORMAT_MJPEG,
                                                 width, height, 1, max_fps);
   result = uvc_get_frame_desc(mDeviceHandle,
                               &ctrl,
                               &frame_desc);  //get the actual format

   //allocate the transfer buffer
   frame_transfer_buffer = uvc_allocate_frame(frame_desc->wWidth*frame_desc->wHeight*3);
   jpeg_transfer_buffer = uvc_allocate_frame(frame_desc->wWidth*frame_desc->wHeight*3);
   pthread_mutex_init(&lock, NULL);
   result = uvc_start_streaming(mDeviceHandle,
                                                      &ctrl,
                                                      uvc_preview_frame_callback,
                                                      (void *) 12345,
                                                      0);
      if(result < 0){
      return 2;
      }
    running = 1;
    return 0;
}



#ifdef __cplusplus
}
#endif