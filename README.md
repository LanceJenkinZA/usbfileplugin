# USB File Cordova Plugin
A plugin to access files on a USB device in Android.

Uses LIBAUMS (https://github.com/magnusja/libaums) to handle communications with the USB
device.  The library only supports FAT32 partitions.

## Installation
Install the plugin from Github:
```
cordova plugin add https://github.com/LanceJenkinZA/usbfileplugin
```

## Usage:

Getting notified when a USB mass storage device is attached
```javascript
cordova.plugins.UsbFile.register(this.handleUSBConnected);
```

Getting list of directory on the USB device
```javascript
cordova.plugins.UsbFile.listDir("", function(entries){
    infoDiv.innerHTML += "<ul>";
    
    for(var i = 0; i < entries.length; i++){
        infoDiv.innerHTML += "<li>" + entries[i].name + "</li>";
    }

    infoDiv.innerHTML += "</ul>";
}, console.error);
```

Reading a file a text file
```javascript
cordova.plugins.UsbFile.readAsText("README.md", function(contents){
console.log(contents);
}, console.error);
```