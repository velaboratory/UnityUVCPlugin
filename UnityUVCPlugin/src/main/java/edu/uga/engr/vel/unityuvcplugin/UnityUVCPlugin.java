package edu.uga.engr.vel.unityuvcplugin;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.opengl.GLES20;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.widget.Toast;

import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class UnityUVCPlugin {


    static {
            System.loadLibrary("jpeg-turbo1500");
            System.loadLibrary("usb100");
            System.loadLibrary("uvc");
            System.loadLibrary("UVCCamera");
    }

    UVCCamera cam;

    int width;
    int height;
    int fps;
    Handler handler = new Handler();


    Lock l = new ReentrantLock();
    private USBMonitor mUSBMonitor;
    public static Activity _unityActivity;

    private byte[] frameData = new byte[0];
    private byte[] jpegData = new byte[0];
    private int jpegLen = 0;
    public boolean hasFrameData = false;

    boolean callUnity(String gameObjectName, String  function, String args){
        try{
            Class<?> classtype = Class.forName("com.unity3d.player.UnityPlayer");
            Method method = classtype.getMethod("UnitySendMessage",String.class, String.class, String.class);
            method.invoke(classtype, gameObjectName, function,args);
            return true;
        }catch (ClassNotFoundException e){
            e.printStackTrace();
        }catch (IllegalAccessException e){
            e.printStackTrace();
        }catch (NoSuchMethodException e){
            e.printStackTrace();
        }catch (InvocationTargetException e){
            e.printStackTrace();
        }
        return false;
    }

    Activity getActivity(){
        if(null == _unityActivity){
            try{
                Class<?> classtype = Class.forName("com.unity3d.player.UnityPlayer");
                Activity activity = (Activity) classtype.getDeclaredField("currentActivity").get(classtype);
                _unityActivity = activity;
            }catch (ClassNotFoundException e){
                e.printStackTrace();
            }catch (IllegalAccessException e){
                e.printStackTrace();
            }catch (NoSuchFieldException e){
                e.printStackTrace();
            }
        }
        return _unityActivity;
    }

    public boolean CreateUSBCamera(int width, int height, int fps){



        this.width = width;
        this.height = height;
        this.fps = fps;


        mUSBMonitor = new USBMonitor(getActivity(), mOnDeviceConnectListener);
        mUSBMonitor.register();
        //callUnity("Main Camera","fromPlugin","here");
        return true;
    }

    public class ConnectRunnable implements Runnable {
        private UsbDevice device;
        public ConnectRunnable(UsbDevice device) {
            this.device = device;
        }
        @Override
        public void run() {
            if(mUSBMonitor.requestPermission(device)){
                //callUnity("Main Camera","fromPlugin","request device failed");
            }else{
                //callUnity("Main Camera","fromPlugin","request device succeeded");
            }
        }
    }

    public byte[] GetFrameData(){

        if(frameData.length  < width*height*3){
            frameData = new byte[width*height*3];
        }
        if(jpegData.length  < width*height*3){
            jpegData = new byte[width*height*3];
        }
        jpegLen = getFrame(frameData,jpegData);

        return frameData;
    }

    public byte[] GetJpegData(){
        return jpegData;
    }
    public int GetJpegLen(){
        return jpegLen;
    }




    private native int openCamera(  int vendorId,
                                     int productId,
                                     int fileDescriptor,
                                     int busNum,
                                     int devAddr,
                                     String usbfs
                                     );
    private native int startCamera(int width,
                                           int height,
                                           int min_fps,
                                           int max_fps,
                                           int mode,
                                           float bandwidth);

    private native int getFrame(byte[] frame_bytes, byte[] jpeg_bytes);

    private final String getUSBFSName(final USBMonitor.UsbControlBlock ctrlBlock) {
        String result = null;
        final String name = ctrlBlock.getDeviceName();
        final String[] v = !TextUtils.isEmpty(name) ? name.split("/") : null;
        if ((v != null) && (v.length > 2)) {
            final StringBuilder sb = new StringBuilder(v[0]);
            for (int i = 1; i < v.length - 2; i++)
                sb.append("/").append(v[i]);
            result = sb.toString();
        }

        return result;
    }

    private final USBMonitor.OnDeviceConnectListener mOnDeviceConnectListener = new USBMonitor.OnDeviceConnectListener()
    {
        public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {

            int res = openCamera(ctrlBlock.getVenderId(),
                    ctrlBlock.getProductId(),
                    ctrlBlock.getFileDescriptor(),
                    ctrlBlock.getBusNum(),
                    ctrlBlock.getDevNum(),
                    getUSBFSName(ctrlBlock));
            callUnity("Main Camera","fromPlugin",getUSBFSName(ctrlBlock)+res);

            res = startCamera(width, height, 1, fps, 0, 1.0f);
            callUnity("Main Camera","fromPlugin","here4"+res);

            //cam.setFrameCallback(mIFrameCallback, UVCCamera.PIXEL_FORMAT_RAW/*UVCCamera.PIXEL_FORMAT_NV21*/);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {


                }
            }, 2000);


        }

        @Override
        public void onDisconnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock) {

        }
        @Override
        public void onAttach(final UsbDevice device) {
            //callUnity("Main Camera","fromPlugin",device.getDeviceName());
            handler.postDelayed(new ConnectRunnable(device), 2000);
        }
        @Override
        public void onDettach(final UsbDevice device) {

        }

        @Override
        public void onCancel(final UsbDevice device) {
            //callUnity("Main Camera","fromPlugin","cancelled");
        }
    };

}
