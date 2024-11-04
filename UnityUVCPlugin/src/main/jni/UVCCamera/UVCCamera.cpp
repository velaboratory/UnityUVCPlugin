#include "libuvc.h"
#include <jni.h>
#include <cstring>
#include <pthread.h>
#include <sstream>
//this is where we are going to start the thread

class UVCCamera {
public:
    uvc_context_t *mContext;
    uvc_device_t *mDevice;
    uvc_device_handle_t *mDeviceHandle;
    uvc_frame_desc_t *frame_desc;
    uvc_stream_ctrl_t ctrl;
    uvc_frame_t *frame_transfer_buffer = NULL;
    uvc_frame_t *jpeg_transfer_buffer = NULL;
    pthread_mutex_t lock;
    int running = 0;
    int numFrames = 0;
    bool opened = false;
};

UVCCamera* cameras[100] = {NULL};
int lastCameraIndex = 0;

void uvc_preview_frame_callback(uvc_frame_t *frame, void *vptr_args){
    UVCCamera * cam = (UVCCamera *)vptr_args;
    pthread_mutex_lock(&cam->lock);
    cam->numFrames++;

    uvc_error_t result = uvc_mjpeg2rgb(frame, cam->frame_transfer_buffer);
    uvc_error_t result2 = uvc_duplicate_frame(frame,cam->jpeg_transfer_buffer);
    memcpy(cam->jpeg_transfer_buffer->data,frame->data,frame->actual_bytes);
    if(result == 0 && result2 == 0) {
        cam->running = 1;
    }

    pthread_mutex_unlock(&cam->lock);

}
#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT int JNICALL Java_edu_uga_engr_vel_unityuvcplugin_UnityUVCPlugin_getFrame(
JNIEnv* env, jobject thisObject, int cameraIndex,
jbyteArray frame_buffer){
    UVCCamera * cam = cameras[cameraIndex];
    if(cam->running == 0){
        return -1;
    }

    jsize frame_len = env->GetArrayLength(frame_buffer);
    jbyte* frame_bufferPtr = env->GetByteArrayElements(frame_buffer, NULL);


    //lock the transfer buffer, do the copy
    pthread_mutex_lock(&cam->lock);
    std::memcpy(frame_bufferPtr,cam->frame_transfer_buffer->data,cam->frame_transfer_buffer->actual_bytes);
    pthread_mutex_unlock(&cam->lock);
    return 0;
}

JNIEXPORT int JNICALL Java_edu_uga_engr_vel_unityuvcplugin_UnityUVCPlugin_getJpegFrame(
        JNIEnv* env, jobject thisObject, int cameraIndex,
        jbyteArray frame_buffer){
    UVCCamera * cam = cameras[cameraIndex];
    if(cam->running == 0){
        return -1;
    }

    jsize frame_len = env->GetArrayLength(frame_buffer);
    jbyte* frame_bufferPtr = env->GetByteArrayElements(frame_buffer, NULL);


    //lock the transfer buffer, do the copy
    pthread_mutex_lock(&cam->lock);
    std::memcpy(frame_bufferPtr,cam->jpeg_transfer_buffer->data,cam->jpeg_transfer_buffer->actual_bytes);
    pthread_mutex_unlock(&cam->lock);
    return cam->jpeg_transfer_buffer->actual_bytes;

}
JNIEXPORT jstring JNICALL Java_edu_uga_engr_vel_unityuvcplugin_UnityUVCPlugin_getDescriptor(JNIEnv* env, jobject thisObject, int cameraIndex){
    UVCCamera * cam = cameras[cameraIndex];
    const uvc_format_desc_t* format_desc = uvc_get_format_descs(cam->mDeviceHandle);
    char toReturn[10000]=""; // big enough
    int i=0;
    while(true){
        if(format_desc == NULL){

            break;
        }
        uvc_frame_desc * frame_desc = format_desc->frame_descs;

        //sprintf(toReturn,"%Type: %d", toReturn, format_desc->bDescriptorSubtype);
        while(true){
            if(frame_desc == NULL){
                break;
            }


            uint32_t* interval = frame_desc->intervals;
            while(*interval){
                if(i++>0){
                    strcat(toReturn,"\n"); //add a newline
                }
                int fps = (int)(1e7/(*interval));
                sprintf(toReturn,"%s%d,%d,%d,%d",toReturn,(int)(format_desc->bDescriptorSubtype),frame_desc->wWidth,frame_desc->wHeight,fps);
                interval++;
            }

            frame_desc = frame_desc->next;
        }
        format_desc = format_desc->next;
    }

    return env->NewStringUTF(toReturn);
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
    UVCCamera *cam = new UVCCamera();
    const char *usbfs = env->GetStringUTFChars(usbfs_str,
                                               JNI_FALSE);
    result = uvc_init2(&cam->mContext,
                       NULL,
                       usbfs);
    if(result != 0){
        return -2;
    }

    result = uvc_get_device_with_fd(cam->mContext,
                                    &cam->mDevice,
                                    vid,
                                    pid,
                                    NULL,
                                    fd,
                                    busNum,
                                    devAddr); //get the device
    if(result != 0){
        return -4;
    }

    result = uvc_open(cam->mDevice, &cam->mDeviceHandle);

    if(result == 0){
        int cameraIndex = lastCameraIndex++;
        cameras[cameraIndex] = cam;
        cam->opened = true;
        return cameraIndex;
    }
    return -3;
}

JNIEXPORT int JNICALL Java_edu_uga_engr_vel_unityuvcplugin_UnityUVCPlugin_setExposure(
JNIEnv* env, jobject thisObject,int cameraIndex,
jint value){
    UVCCamera * cam = cameras[cameraIndex];
    int r;
    if(cam->running){

            //r = uvc_set_ae_mode(mDeviceHandle, 0/* & 0xff*/);
            r = uvc_set_exposure_abs(cam->mDeviceHandle, value/* & 0xff*/);
            //r = uvc_set_gain(mDeviceHandle, gain);
    }
    return 0;
}

JNIEXPORT int JNICALL Java_edu_uga_engr_vel_unityuvcplugin_UnityUVCPlugin_setGain(
        JNIEnv* env, jobject thisObject, int cameraIndex,
        jint value){
UVCCamera * cam = cameras[cameraIndex];
int r;
if(cam->running){

//r = uvc_set_ae_mode(mDeviceHandle, 0/* & 0xff*/);
//r = uvc_set_exposure_abs(mDeviceHandle, exposure/* & 0xff*/);
r = uvc_set_gain(cam->mDeviceHandle, value);

}
return 0;
}

JNIEXPORT int JNICALL Java_edu_uga_engr_vel_unityuvcplugin_UnityUVCPlugin_setAutoExposure(
        JNIEnv* env, jobject thisObject, int cameraIndex,
        jint value){
UVCCamera * cam = cameras[cameraIndex];
int r;
if(cam->running){

r = uvc_set_ae_mode(cam->mDeviceHandle, value/* & 0xff*/);
//r = uvc_set_exposure_abs(mDeviceHandle, exposure/* & 0xff*/);
//r = uvc_set_gain(mDeviceHandle, gain);

}
return 0;
}

JNIEXPORT int JNICALL Java_edu_uga_engr_vel_unityuvcplugin_UnityUVCPlugin_startCamera(
                        JNIEnv* env, jobject thisObject, int cameraIndex,
                        jint width, jint height, jint min_fps, jint max_fps,
                        jint mode, jfloat bandwidth){
    UVCCamera * cam = cameras[cameraIndex];
    uvc_error_t result;

    result = uvc_get_stream_ctrl_format_size_fps(cam->mDeviceHandle,
                                                 &cam->ctrl,
                                                 (uvc_frame_format)mode,
                                                 width, height, bandwidth, max_fps);
    if(result < 0){
        return 4;
    }
    result = uvc_get_frame_desc(cam->mDeviceHandle,
                                &cam->ctrl,
                                &cam->frame_desc);  //get the actual format
    if(result < 0){
        return 3;
    }


    cam->frame_transfer_buffer = uvc_allocate_frame(cam->frame_desc->wWidth*cam->frame_desc->wHeight*3);
    cam->jpeg_transfer_buffer = uvc_allocate_frame(cam->frame_desc->wWidth*cam->frame_desc->wHeight*3);
    pthread_mutex_init(&cam->lock, NULL);

   result = uvc_start_streaming(cam->mDeviceHandle,&cam->ctrl,uvc_preview_frame_callback,(void *) cam, 0);
   if(result < 0){
      return 2;
   }
   return 0;
}



#ifdef __cplusplus
}
#endif