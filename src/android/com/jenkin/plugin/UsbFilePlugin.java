package com.jenkin.plugin;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;

import com.github.mjdev.libaums.UsbMassStorageDevice;
import com.github.mjdev.libaums.fs.FileSystem;
import com.github.mjdev.libaums.fs.FileSystemFactory;
import com.github.mjdev.libaums.fs.UsbFile;
import com.github.mjdev.libaums.fs.UsbFileStreamFactory;

import com.github.magnusja.libaums.javafs.JavaFsFileSystemCreator;

/**
 * Plugin to communicate with USB devices.
 *
 * Currently only handles the first partition on the first usb device.
 *
 * Supports the following functions:
 * * list directory
 * * read file as text
 *
 * Uses Libaums to access the USB devicehttps://github.com/magnusja/libaums
 */
public class UsbFilePlugin extends CordovaPlugin {

    /**
     * The tag used in log messages
     */
    public static final String TAG = "UsbFile";

    /**
     * The name of the USB permission
     */
    private static final String ACTION_USB_PERMISSION = "com.jenkin.plugin.usbfileplugin.USB_PERMISSION";

    /**
     * The active mass storage device
     */
    private UsbMassStorageDevice massStorageDevice;

    /**
     * The call back to notify when mass storage device is attached
     */
    private CallbackContext usbEventCallback;

    /**
     * The broadcast receiver to handle incoming intents
     */
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {

        /**
         * Handle incoming intents.
         *
         * The following intents are handled:
         * 1) USB permission updated: setup the device.
         * 2) USB device attached: search for any attached mass storage devices.
         * 3) USB device detached: search for any attached mass storage devices.
         *
         * @param context The Context in which the receiver is running.
         * @param intent The Intent being received.
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (ACTION_USB_PERMISSION.equals(action)) {
                Log.d(TAG, "Permission updated for USB device");

                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    if (device != null) {
                        setupDevice();
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                Log.d(TAG, "USB device attached");

                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                // determine if connected device is a mass storage devuce
                if (device != null) {
                    discoverDevice();
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                Log.d(TAG, "USB device detached");

                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                if (device != null) {
                    // determine if connected device is a mass storage device
                    if (UsbFilePlugin.this.massStorageDevice != null) {
                        UsbFilePlugin.this.massStorageDevice.close();
                    }

                    // check if there are other devices or set action bar title
                    // to no device if not
                    discoverDevice();
                }
            }
        }
    };

    /**
     * Initialize the plugin by setting up the intents that the plugin will handle.  Also scan for any devices
     * mass storage devices already attached.
     *
     * @param cordova A reference to the active cordova interface
     * @param webView A reference to the webview that the application is running in
     */
    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        FileSystemFactory.registerFileSystem(JavaFsFileSystemCreator());
        Context context = this.cordova.getActivity().getApplicationContext();
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        context.registerReceiver(usbReceiver, filter);
        discoverDevice();
    }

    /**
     * The routing function to handle actions from Cordova.
     * Handles the following actions:
     * 1) register - Register the callback that will be called when a mass storage device is attached.
     * 2) listDir - List a directory on the mass storage device
     * 3) readAsText - Read a file on the USB device as a text file.
     *
     * @param action The action that the plugin will perform
     * @param args The arguments to pass to the function
     * @param callbackContext The callback context to pass data back to the calling function
     * @return Flag indicating if the action is handled
     * @throws JSONException Exception thrown when there is an error with the JSON object
     */
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        Log.d(TAG, "Executing: " + action);

        if ("register".equals(action)) {
            this.usbEventCallback = callbackContext;

            return true;
        }

        if ("listDir".equals(action)) {
            try {
                listDir(args.getString(0), callbackContext);
            } catch (Exception e) {
                callbackContext.error("Error listing directory: " + e.getMessage());
            }

            return true;
        }

        if ("readAsText".equals(action)) {
            try {
                readAsText(args.getString(0), callbackContext);
            } catch (Exception e) {
                callbackContext.error("Error reading file: " + e.getMessage());
            }

            return true;
        }

        if ("exists".equals(action)) {
            try {
                exists(args.getString(0), callbackContext);
            } catch (Exception e) {
                callbackContext.error("Error checking file: " + e.getMessage());
            }

            return true;
        }

        return false;
    }

    private void exists(String fileName, CallbackContext callbackContext) throws JSONException, IOException {
        Log.d(TAG, "Checking if file exists: " + fileName);

        if(massStorageDevice == null){
            callbackContext.error("Device not set up");
            return;
        }

        FileSystem currentFs = massStorageDevice.getPartitions().get(0).getFileSystem();
        UsbFile root = currentFs.getRootDirectory();
        if(fileName.startsWith("/")){
            fileName = fileName.substring(1);
        }
        UsbFile file = root.search(fileName);

        callbackContext.success(file != null);
    }

    /**
     * List directory on the mass storage device.
     *
     * @param dirName The directory name to list on the device
     * @param callbackContext The callback to pass back the list of files in the directory.
     * @throws JSONException Error thrown if there is problem with the JSON object
     * @throws IOException Error thrown if there is a problem reading a file.
     */
    private void listDir(String dirName, CallbackContext callbackContext) throws JSONException, IOException {
        Log.d(TAG, "Listing directory: " + dirName);

        if (massStorageDevice == null) {
            callbackContext.error("Device not set up");
            return;
        }
        FileSystem currentFs = massStorageDevice.getPartitions().get(0).getFileSystem();

        UsbFile root = currentFs.getRootDirectory();
        UsbFile path;

        if(dirName.startsWith("/")){
            dirName = dirName.substring(1);
        }
        if (!dirName.equals("")){
            path = root.search(dirName);
            if (path == null) {
                callbackContext.error("Couldn't find path");
                return;
            }
        }else{
            path = root;
        }

        JSONArray fileList = new JSONArray();

        UsbFile[] files = path.listFiles();
        for (UsbFile file : files) {
            JSONObject fileObject = new JSONObject();
            fileObject.put("name", file.getName());
            fileObject.put("full_path", dirName + "/" + file.getName());
            fileObject.put("is_directory", file.isDirectory());
            fileObject.put("created_at", file.createdAt());
            fileObject.put("last_modified", file.lastModified());
            fileObject.put("last_accessed", file.lastAccessed());
            if(!file.isDirectory()){
                fileObject.put("size", file.getLength());
            }
            fileList.put(fileObject);
        }

        callbackContext.success(fileList);
    }

    /**
     * Read a file as a text file and pass the contents back to the call back function
     *
     * @param fileName The full path to the file on the device
     * @param callbackContext The callback to pass contents back to Cordova
     * @throws JSONException Error thrown if there is problem with the JSON object
     * @throws IOException Error thrown if there is a problem reading a file.
     */
    private void readAsText(String fileName, CallbackContext callbackContext) throws JSONException, IOException {
        if (massStorageDevice == null) {
            callbackContext.error("Device not set up");
            return;
        }

        FileSystem currentFs = massStorageDevice.getPartitions().get(0).getFileSystem();
        UsbFile root = currentFs.getRootDirectory();
        if(fileName.startsWith("/")){
            fileName = fileName.substring(1);
        }
        UsbFile file = root.search(fileName);
        if (file == null) {
            callbackContext.error("Could not find file");
            return;
        }

        String fileContents = "";
        InputStream is = UsbFileStreamFactory.createBufferedInputStream(file, currentFs);
        byte[] buffer = new byte[4096];
        int count;
        while ((count = is.read(buffer)) != -1) {
            fileContents = fileContents.concat(new String(buffer));
        }

        callbackContext.success(fileContents);
    }

    /**
     * Searches for connected mass storage devices, and initializes them if it
     * could find some.
     */
    private void discoverDevice() {
        Activity activity = this.cordova.getActivity();
        Context context = activity.getApplicationContext();
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        UsbMassStorageDevice[] massStorageDevices = UsbMassStorageDevice.getMassStorageDevices(context);

        if (massStorageDevices.length == 0) {
            Log.w(TAG, "no device found!");
            return;
        }

        // we only use the first device
        massStorageDevice = massStorageDevices[0];

        UsbDevice usbDevice = (UsbDevice) activity.getIntent().getParcelableExtra(UsbManager.EXTRA_DEVICE);

        if (usbDevice != null && usbManager.hasPermission(usbDevice)) {
            Log.d(TAG, "received usb device via intent");
            // requesting permission is not needed in this case
            setupDevice();
        } else {

            PendingIntent permissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(
                    ACTION_USB_PERMISSION), 0);
            usbManager.requestPermission(massStorageDevice.getUsbDevice(), permissionIntent);
        }
    }

    /**
     * Sets the device up and shows the contents of the root directory.
     */
    private void setupDevice() {
        try {
            Log.d(TAG, "setting up device");
            massStorageDevice.init();
            if (this.usbEventCallback != null) {
                Log.d(TAG, "sneding reuslt");
                PluginResult result = new PluginResult(PluginResult.Status.OK);
                result.setKeepCallback(true);
                this.usbEventCallback.sendPluginResult(result);
            } else {
                Log.d(TAG, "no callback");
            }
        } catch (IOException e) {
            Log.e(TAG, "error setting up device", e);
        }
    }

    /**
     * Clean up plugin when application is closed.
     *
     * Closes the active mass storage device.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        Context context = this.cordova.getActivity().getApplicationContext();
        if (massStorageDevice != null) {
            massStorageDevice.close();
        }
        context.unregisterReceiver(usbReceiver);
    }
}

