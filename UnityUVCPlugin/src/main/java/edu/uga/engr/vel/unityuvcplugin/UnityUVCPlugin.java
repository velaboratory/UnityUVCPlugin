package edu.uga.engr.vel.unityuvcplugin;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.opengl.GLES20;
import android.os.Handler;
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
    UVCCamera cam;

    int width;
    int height;
    int fps;
    int[][] textures = {{1}};
    int[] textureIds = {0};

    public SurfaceTexture mSurfaceTexture;
    public Surface mPreviewSurfaces;
    Handler handler = new Handler();

    SurfaceTexture texture;
    Lock l = new ReentrantLock();
    private USBMonitor mUSBMonitor;
    public static Activity _unityActivity;

    private byte[] frameData = new byte[0];
    private byte[] frameData2 = new byte[0];
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
        GLES20.glGenTextures(1, textures[0], 0);
        textureIds[0] = textures[0][0];
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        mSurfaceTexture = new SurfaceTexture(textureIds[0]);
        mPreviewSurfaces = new Surface(mSurfaceTexture);

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
        if(!hasFrameData){
            return null;
        }
        l.lock();
        try{
            if(frameData.length != frameData2.length){
                frameData2 = new byte[frameData.length];
            }
            System.arraycopy(frameData,0,frameData2,0,frameData.length);
            hasFrameData = false;
        }
        finally{
            l.unlock();
        }
        return frameData;

    }

    private final IFrameCallback mIFrameCallback = new IFrameCallback() {
        @Override
        public void onFrame(final ByteBuffer frame) {



            l.lock();
            try {
                int len = frame.capacity();
                if(len != frameData.length) {
                    frameData = new byte[len];
                }
                frame.get(frameData);
                hasFrameData = true;
            } finally {
                l.unlock();
            }

        }
    };

    private final USBMonitor.OnDeviceConnectListener mOnDeviceConnectListener = new USBMonitor.OnDeviceConnectListener()
    {
        public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {

            callUnity("Main Camera","fromPlugin","here3");

            cam = new UVCCamera();
            cam.open(ctrlBlock);

            try {
                cam.setPreviewSize(width, height, UVCCamera.FRAME_FORMAT_MJPEG);
            } catch (final IllegalArgumentException e) {
                // fallback to YUV mode
                try {
                    cam.setPreviewSize(width, height, 1, fps, UVCCamera.FRAME_FORMAT_YUYV,1);
                } catch (final IllegalArgumentException e1) {
                    cam.destroy();
                    return;
                }
            }

            cam.setFrameCallback(mIFrameCallback, UVCCamera.PIXEL_FORMAT_RGB565/*UVCCamera.PIXEL_FORMAT_NV21*/);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    cam.setPreviewTexture(mSurfaceTexture);
                    cam.startPreview();
                    callUnity("Main Camera","fromPlugin","here4");
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
