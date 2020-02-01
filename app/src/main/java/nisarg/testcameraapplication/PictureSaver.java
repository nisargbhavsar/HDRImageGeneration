package nisarg.testcameraapplication;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Parameters;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;
import android.view.View;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Semaphore;

public class PictureSaver implements PictureCallback, Runnable, SurfaceHolder.Callback {

    private Parameters params;
    private Camera mCamera;
    private SurfaceTexture surfaceTexture;
    private SurfaceHolder surfaceHolder;

    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;

    public byte[] mCameraData;
    public int test = -1;
    private volatile int camCallback = 0;
    Semaphore sem;

    PictureSaver(){
        Log.d("PictureSaver(): ", "PictureSaver object created");
        //surfaceTexture = new SurfaceTexture(0);
    }

    public void updateCamParams(Parameters inputParams, Semaphore inputSem, SurfaceHolder inputCamView){
        params = inputParams;
        sem = inputSem;

        surfaceHolder = inputCamView;
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    private boolean safeCameraOpen(int id) {
        boolean qOpened = false;

        try {
            releaseCamera();
            mCamera = Camera.open(id);
            qOpened = (mCamera != null);
        } catch (Exception e) {
            e.printStackTrace();
            Log.d("safeCameraOpen", "Unable to open camera");
        }

        try {
            //mCamera.setPreviewDisplay(null);
            //mCamera.setPreviewTexture(surfaceTexture);
            //surfaceTexture = new SurfaceTexture(0);
            //mCamera.setPreviewTexture(surfaceTexture);
            mCamera.setPreviewDisplay(surfaceHolder);
            mCamera.startPreview();
        }catch (Exception e){
            e.printStackTrace();
            Log.d("safeCameraOpen", "Unable to start preview");
        }
        mCamera.startPreview();
        return qOpened;
    }

    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }

    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
        Log.d("onPictureTaken: ", "Hit breakpoint.");
        mCameraData = data;
        releaseCamera();

        /*File pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);
        if (pictureFile == null){
            Log.d("onPictureTaken: ", "Error creating media file, check storage permissions");
            return;
        }

        try {
            FileOutputStream fos = new FileOutputStream(pictureFile);
            fos.write(mCameraData);
            fos.close();
        } catch (FileNotFoundException e) {
            Log.d("Picture", "File not found: " + e.getMessage());
        } catch (IOException e) {
            Log.d("onPictureTaken: ", "Error accessing file: " + e.getMessage());
        }*/
        Log.d("onPictureTaken: ", "Succeeded in saving picture");
    }

    public void setupCamera(){
        sem.acquireUninterruptibly();
        //Obtain camera
        Log.d("setupCamera", "Some thread got the camera");
        safeCameraOpen(0); //TODO: Allow MainActivity to specify camera id
        mCamera.setParameters(params);
        mCamera.stopPreview();
        mCamera.startPreview();
        Log.d("setupCamera", "Some thread taking a pic");
        //mCamera.takePicture(this, this, null);
        mCamera.takePicture(null, this, null);
    }

    @Override
    public void run(){
        setupCamera();
        while(mCameraData == null){
            camCallback++;
            /*try {
                //Log.d("ThreadRun", "Thread is sleeping");
                //Thread.sleep(500);
            } catch (InterruptedException e) {
                Log.d("ThreadRun", "Error in sleeping.");
            }*/
        }
        sem.release();
        /*File pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);
        if (pictureFile == null){
            Log.d(TAG, "Error creating media file, check storage permissions");
            return;
        }

        try {
            FileOutputStream fos = new FileOutputStream(pictureFile);
            fos.write(mCameraData);
            fos.close();
        } catch (FileNotFoundException e) {
            Log.d("Picture", "File not found: " + e.getMessage());
        } catch (IOException e) {
            Log.d(TAG, "Error accessing file: " + e.getMessage());
        }*/


    }


    /** Create a file Uri for saving an image or video */
    private static Uri getOutputMediaFileUri(int type){
        return Uri.fromFile(getOutputMediaFile(type));
    }

    /** Create a File for saving an image or video */
    private static File getOutputMediaFile(int type){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "MyCameraApp");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d("MyCameraApp", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE){
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_"+ timeStamp + ".jpg");
        } else if(type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_"+ timeStamp + ".mp4");
        } else {
            return null;
        }

        return mediaFile;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (mCamera != null) {
            try {
                mCamera.setPreviewDisplay(holder);
                    mCamera.startPreview();
            } catch (IOException e) {
                //Toast.makeText("PictureSaver", "Unable to start camera preview.",Toast.LENGTH_LONG).show();
                Log.d("PictureSaver", "Unable to start camera preview");
            }
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }
}