package nisarg.testcameraapplication;

import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Parameters;

import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.IOException;
import java.lang.Math;
import java.util.concurrent.Semaphore;


public class MainActivity extends AppCompatActivity implements PictureCallback, SurfaceHolder.Callback, Camera.ShutterCallback {

    private Camera mCamera;
    private ImageView mCameraImage;
    private SurfaceView mCameraPreview;
    private Button mCaptureImageButton;
    private byte[] mCameraData;
    private boolean mIsCapturing;
    private boolean safeToCapture;
    private Parameters mCameraParams;
    private Semaphore mCameraSemaphore;
    private SurfaceHolder surfaceHolder;
    private int picSaved = 0;

    private PictureSaver picHandler;

    private int test = 0;

    private OnClickListener mCaptureImageButtonClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            mCameraParams = mCamera.getParameters();

            int minExposure = mCameraParams.getMinExposureCompensation();
            int maxExposure = mCameraParams.getMaxExposureCompensation();
            int numExposure = Math.abs(maxExposure)  + Math.abs(minExposure) + 1;
            //Thread[] threads= new Thread[numExposure];
            int thCount = 0;
            mCamera.startPreview();
            try {
                mCamera.reconnect();
            } catch (IOException e) {

            }
            mCamera.startPreview();
            //Release camera to allow background thread to take picture
            //releaseCamera();


            for(int i = minExposure; i <= maxExposure; i += 1){
                mCameraParams.setExposureCompensation(i);
                safeToCapture = false;
                mCamera.setParameters(mCameraParams);
                Log.d("setupCamera", "Taking a pic");
                //mCamera.takePicture(this, this, null);
                //mCamera.takePicture(null, null, null);
                picHandler.updateCamParams(mCameraParams);
                //mCamera.takePicture(picHandler, MainActivity.this, null);
                captureImage();
                mCamera.startPreview();
                while(safeToCapture == false){};
                //while(picHandler.picSaved == 0){};
                //mCamera.stopPreview();

                //Log.d("OnClickListener", "Exposure: " + Integer.toString(mCameraParams.getExposureCompensation()));

                /*picHandler.updateCamParams(mCameraParams, mCameraSemaphore, surfaceHolder);
                threads[thCount] = new Thread(picHandler);
                threads[thCount].start();
                thCount++;*/
                /*
                Thread thread = new Thread(picHandler);

                try {
                    thread.join();
                }
                catch (Exception e) {
                    Log.d("OnClickListener", "Created thread won't join");
                }*/


                /*mCamera.setParameters(mCameraParams);

                mCamera.startPreview();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Log.d("OnClickListener", "Thread's sleep f-ed up.");
                }
                captureImage();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Log.d("OnClickListener", "Thread's sleep f-ed up.");
                }
                mCamera.stopPreview();*/
            }
            /*for(int i = 0; i < numExposure; i++ ){
                try {
                    threads[i].join();
                }
                catch (Exception e) {
                    Log.d("OnClickListener", "Created thread won't join");
                }
            }*/

            if(safeCameraOpen(0)){
                mCamera.startPreview();
            }
            Log.d("OnClickListener", "onClick finished");
            Log.d("OnClickListener", "test: " + Integer.toString(test));

        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mCameraPreview = (SurfaceView) findViewById(R.id.preview_view);
        surfaceHolder = mCameraPreview.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        mCaptureImageButton = (Button) findViewById(R.id.capture_image_button);
        mCaptureImageButton.setOnClickListener(mCaptureImageButtonClickListener);

        picHandler = new PictureSaver();
        mIsCapturing = true;
        mCameraSemaphore = new Semaphore(1);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if(safeCameraOpen(0)){
            mCamera.startPreview();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        releaseCamera();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (mCamera != null) {
            try {
                mCamera.setPreviewDisplay(holder);
                if (mIsCapturing) {
                    mCamera.startPreview();
                    safeToCapture = true;
                }
            } catch (IOException e) {
                Toast.makeText(MainActivity.this, "Unable to start camera preview.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    private boolean safeCameraOpen(int id) {
        boolean qOpened = false;

        try {
            releaseCamera();
            mCamera = Camera.open(id);
            qOpened = (mCamera != null);
        } catch (Exception e) {
            Log.e(getString(R.string.app_name), "failed to open Camera");
            e.printStackTrace();
            Toast.makeText(MainActivity.this, "Failed to open Camera.", Toast.LENGTH_SHORT).show();
        }

        return qOpened;
    }

    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }

    private void captureImage() {
        mCamera.takePicture(picHandler, picHandler, null);
    }

    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
        Log.d("OnClickListener", "Exposure: " + Integer.toString(mCameraParams.getExposureCompensation()));
        mCameraData = data;
        safeToCapture = true;
    }

    @Override
    public void onShutter() {
        Log.d("PictureSaver", "Inside shutter callback");
    }
}
