var exec = require('cordova/exec');

/**
 * Register the callback function to be called when a USB device is attached
 *
 * @param success The callback function that will be called when the USB mass storage device is attached
 * @param error Not used
 */
exports.register = function(success, error) {
    exec(success, error, "UsbFilePlugin", "register");
};

/**
 * List a directory on the USB device.
 *
 * @param dirName The path to the directory on the USB device - omit the leading forward slash.  Directories are
 *  seperated with "/".
 * @param success The function called with the contents of the directory
 * @param error The function called if an error occurs
 */
exports.listDir = function(dirName, success, error) {
    exec(success, error, "UsbFilePlugin", "listDir", [dirName]);
};

/**
 * Read a file on the USB device as a text
 *
 * @param fileName The full path to the file on the USB device.
 * @param success The function called with the contents of the file
 * @param error The function called if an error occurs
 */
exports.readAsText = function(fileName, success, error) {
    exec(success, error, "UsbFilePlugin", "readAsText", [fileName]);
};
