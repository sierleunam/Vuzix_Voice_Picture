package com.sysdevmobile.vuzixvoicepicture;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static com.sysdevmobile.vuzixvoicepicture.MainActivity.DOWNLOADS_FOLDER;
import static com.sysdevmobile.vuzixvoicepicture.MainActivity.IMAGE_FILENAME;

class FileUtils {
    private static final String TAG = "FileUtils";

    /**
     * Helper method to create a file in the Downloads folder
     *
     * @param fileName file to be created
     * @return File
     */
    static File getPublicDownloadsStorageFile(String fileName) {

        File file = null;
        if (isExternalStorageWritable()) {
            // Get the directory for the user's public downloads directory.
            file = new File(DOWNLOADS_FOLDER, fileName);

        } else
            Log.d(TAG, "getPublicDownloadsStorageFile: Folder not Writable!!");

        return file;
    }

    /**
     * Check if the storage is writable
     */
    private static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    /**
     * creates a text file
     *
     * @param text text to be written
     */
    static void writeTextToFile(String text) {

        File file = getPublicDownloadsStorageFile(IMAGE_FILENAME);
        FileWriter fw;
        try {
            fw = new FileWriter(file);
            fw.write(text);
            fw.flush();
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Deletes a file from Download Folder
     *
     * @param fileName file to be deleted
     */
    static void deleteFilename(String fileName) {
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);
        if (file.exists()) {
            boolean st = file.delete();
            if (st)
                Log.d(TAG, "deleteFile() called with: fileName = [" + fileName + "], Deleting: " + file.toString());
        }
    }
}
