package edu.uga.engr.vel.unityuvcplugin;

import static android.content.Context.RECEIVER_NOT_EXPORTED;
import static android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED;

import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.app.PendingIntent;
import android.app.Activity;
import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;


public class UnityUVCPlugin {

    class UnityUVCDevice {
        UnityUVCDevice(UsbDeviceConnection connection, UsbDevice device){
            this.connection = connection;
            this.device = device;
        }
        private byte[] frameData = new byte[0];
        private byte[] jpegData = new byte[0];
        private int jpegDataLength = 0;
        public boolean hasFrameData = false;
        int width;
        int height;


        UsbDeviceConnection connection;
        UsbDevice device;

        int nativeIndex;
        String[] open(){

            String deviceName = device.getDeviceName(); //this encodes the bus number and device number
            String[] parts = deviceName.split("/");


            int bus = 0;
            int dev = 0;
            String usbfs = "";
            if (parts.length > 2) {
                for (int i = 0; i < parts.length - 2; i++) {
                    usbfs = usbfs + "/" + parts[i];
                }
                bus = Integer.parseInt(parts[parts.length - 2]);
                dev = Integer.parseInt(parts[parts.length - 1]);
                int res = openCamera(device.getVendorId(),
                        device.getProductId(),
                        connection.getFileDescriptor(),
                        bus, dev, usbfs);
                if (res >= 0) {
                    nativeIndex = res;
                    //get all supported resolutions
                    String resolutions = getDescriptor(nativeIndex);

                    return resolutions.split("\\n");
                }
                else{
                    return null;
                }
            }
            return null;


        }
        int start(int width, int height, int fps, int mode, float bandwidth){
            int res = startCamera(nativeIndex,width,height,fps,mode, bandwidth);
            if(res == 0){
                this.width = width;
                this.height = height;
                return 0;
            }
            return res;
        }
        byte[] getFrameData(){
            if(frameData.length  < width*height*3){
                frameData = new byte[width*height*3];
            }
            getFrame(nativeIndex,frameData);

            return frameData;
        }
        byte[] getJpegData(){
            if(jpegData.length < width*height*3){
                jpegData = new byte[width*height*3]; //this should be much bigger than the maximum jpeg
            }
            jpegDataLength = getJpegFrame(nativeIndex,jpegData);
            return jpegData;
        }
        int getJpegDataLength(){
            return jpegDataLength;
        }
    }
    private static final String ACTION_USB_PERMISSION =
            "edu.uga.engr.vel.unityuvcplugin.USB_PERMISSION";
    static {
            System.loadLibrary("jpeg-turbo1500");
            System.loadLibrary("usb100");
            System.loadLibrary("uvc");
            System.loadLibrary("UVCCamera");
    }

    PendingIntent permissionIntent;
    public static Activity _unityActivity;
    public HashMap<String,UnityUVCDevice> openedDevices = new HashMap<String,UnityUVCDevice>();
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

    String NullDefault(String v){
        if(v == null){return "NULL";}
        return v;
    }

    public String[] GetUSBDevices(){ //this sets things up, gets the currently connected usb devices
        UsbManager manager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        return deviceList.keySet().toArray(new String[0]);
    }

    public String GetUSBDeviceInfo(String deviceID){
        UsbManager manager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();

        UsbDevice device = deviceList.get(deviceID);
        StringBuilder deviceInfo = new StringBuilder();
        deviceInfo.append("Manufacturer Name: ").append(NullDefault(device.getManufacturerName())).append("\n");
        deviceInfo.append("Product Name: ").append(NullDefault(device.getProductName())).append("\n");
        deviceInfo.append("Device Name: ").append(NullDefault(device.getDeviceName())).append("\n");
        deviceInfo.append("Vendor ID: ").append(device.getVendorId()).append("\n");
        deviceInfo.append("Product ID: ").append((device.getProductId())).append("\n");
        deviceInfo.append("Device Class: ").append((device.getDeviceClass())).append("\n");
        deviceInfo.append("Device Subclass: ").append((device.getDeviceSubclass())).append("\n");
        deviceInfo.append("Device Protocol: ").append((device.getDeviceProtocol())).append("\n");
        deviceInfo.append("Device ID: ").append((device.getDeviceId())).append("\n");

        try{
            deviceInfo.append("Serial: ").append(NullDefault(device.getSerialNumber())).append("\n");
        }catch(SecurityException se){
            deviceInfo.append("Serial: No permission");
        }


        return deviceInfo.toString();
    }

    //this is necessary to listen for usb permission events
    public void Init(){
        UsbManager usbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
        permissionIntent = PendingIntent.getBroadcast(getActivity(), 0,
                new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        getActivity().registerReceiver(usbReceiver, filter, RECEIVER_NOT_EXPORTED);

    }

    //this handles usb permission events
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                            //do nothing.  Another function will check on permission
                        }
                    }
                    else {
                        Log.d("UVCPlugin", "permission denied for device " + device);
                    }


                }
            }
            if (ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    if(openedDevices.containsKey(device.getDeviceName())){
                        Close(device.getDeviceName());

                    }
                }
            }
        }
    };
    public void ObtainPermission(String camera){
        UsbManager manager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        if(deviceList.containsKey(camera)){
            manager.requestPermission(deviceList.get(camera),permissionIntent);
        }
    }
    public boolean hasPermission(String camera){
        UsbManager manager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        if(deviceList.containsKey(camera)){
            return manager.hasPermission((deviceList.get(camera)));
        }
        return false;
    }

    public String[] Open(String camera){
        UsbManager manager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        ArrayList<String> descriptorList = new ArrayList<String>();
        if(deviceList.containsKey(camera)){
            UsbDevice cameraDevice = deviceList.get(camera);
            UsbDeviceConnection connection = manager.openDevice(cameraDevice);

            if(connection != null) {
                UnityUVCDevice d = new UnityUVCDevice(connection,cameraDevice);
                openedDevices.put(camera,d);
                return d.open();
            }
        }
        return null;
    }

    public int Close(String camera){
        UnityUVCDevice d = openedDevices.get(camera);
        if(d!= null){
            Log.d("asdf",""+d.nativeIndex);
            closeCamera(d.nativeIndex);
            d.connection.close();
            openedDevices.remove(camera);
            return d.nativeIndex;
        }
        return 0;
    }

    public int Start(String camera, int width, int height, int fps, int mode, float bandwidth){
        return openedDevices.get(camera).start(width,height,fps, mode, bandwidth);
    }
    public int GetFrameNumber(String camera){
        return getFrameNumber(openedDevices.get(camera).nativeIndex);
    }
    public byte[] GetFrameData(String camera){
        return openedDevices.get(camera).getFrameData();

    }
    public int GetJpegDataLength(String camera){
        return openedDevices.get(camera).getJpegDataLength();
    }
    public byte[] GetJpegData(String camera){
        return openedDevices.get(camera).getJpegData();
    }

    public int SetExposure(String camera, int value){
        setExposure(openedDevices.get(camera).nativeIndex,value);
        return 0;
    }

    public int SetGain(String camera, int value){
        setGain(openedDevices.get(camera).nativeIndex,value);
        return 0;
    }

    public int SetAutoExposure(String camera, int value){
        setAutoExposure(openedDevices.get(camera).nativeIndex,value);
        return 0;
    }


    private native int setExposure (int cameraIndex, int value);
    private native int setGain (int cameraIndex, int value);
    private native int setAutoExposure (int cameraIndex, int value);
    private native int openCamera(  int vendorId,
                                     int productId,
                                     int fileDescriptor,
                                     int busNum,
                                     int devAddr,
                                     String usbfs
                                     );
    private native int startCamera(int cameraIndex,
                                   int width,
                                           int height,
                                           int fps,
                                           int mode,
                                            float bandwidth);

    private native int closeCamera(int cameraIndex);
    private native int getFrameNumber(int cameraIndex);
    private native int getFrame(int cameraIndex, byte[] frame_bytes);
    private native int getJpegFrame(int cameraIndex, byte[] jpeg_bytes);

    private native String getDescriptor(int cameraIndex);
}
