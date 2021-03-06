package nisarg.testcameraapplication;

import android.Manifest;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements PictureSaver {
    private static final String TAG = "MainActivity";
    private Button takePictureButton;
    private TextureView textureView;

    //exposure time in ns
    private int minExposureTime = 2000, maxExposureTime = 2000000, numImages = 10;
    private int exposureStep = (maxExposureTime - minExposureTime) / numImages, picIndex = 0;
    private List<Integer> exposures = new ArrayList<Integer>();
    private ArrayList<byte[]> pics = new ArrayList<byte[]>();
    private int numPicsTaken = 0;

    //Flag for AsyncTask to be created+ran
    private Boolean generateHDR = false;
    private HDRCreatorTask asyncHDRTask;
    private HDRCreatorTaskParams asyncHDRTaskParams;


    private String cameraId;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setRequestedOrientation (ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textureView = (TextureView) findViewById(R.id.texture);
        assert textureView != null;
        textureView.setSurfaceTextureListener(textureListener);
        takePictureButton = (Button) findViewById(R.id.capture_image_button);
        assert takePictureButton != null;
        takePictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "Taking bracketed exposure pictures.", Toast.LENGTH_SHORT).show();
                takePicture();
            }
        });

        // Initialize the array of exposures we want (in ns)
        // TODO: increment bracketed exposures by powers of 2
        for (int i = 0; i < numImages; i++){
            exposures.add(minExposureTime + exposureStep * i);
        }

    }
    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TAG, "onResume");
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }
    @Override
    protected void onPause() {
        Log.e(TAG, "onPause");
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera();
        }
        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            closeCamera();
            return true;
        }
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            Log.i(TAG, "Camera opened");
            cameraDevice = camera;
            createCameraPreview();
        }
        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
        }
        @Override
        public void onError(CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }
    protected void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    protected void takeMultiplePictures(final List<CaptureRequest> captureBuilderList, ImageReader reader, List<Surface> outputSurfaces) throws CameraAccessException {
        ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    Log.i(TAG, "onImageAvailable imageDimension.getHeight():" + imageDimension.getHeight() + " imageDimension.getWidth(): " + imageDimension.getWidth());
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.capacity()];
                    buffer.get(bytes);
                    pics.add(bytes);
                    Log.i(TAG, "bytes.length: " + bytes.length + " image.getPlanes()[0].getPixelStride(): " + image.getPlanes()[0].getPixelStride() + " image.getPlanes()[0].getRowStride(): " + image.getPlanes()[0].getRowStride());
                    Log.i(TAG, "image.getFormat(): " + image.getFormat());
                    numPicsTaken += 1;
                    if(numPicsTaken == numImages){
                        generateHDR = true;
                    }
                    //DEBUG
                    //Uncomment to save bracketed exposure pictures
                    /*
                    try {
                        save(bytes);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }*/
                } finally {
                    if (image != null) {
                        image.close();
                    }
                }
            }
            private void save(byte[] bytes) throws IOException {
                OutputStream output = null;
                try {

                    String imageFileName = "JPEG_" + exposures.get(picIndex) + "_";
                    picIndex += 1;
                    Log.e(TAG, "inside save " + bytes.length+ " picIndex: " + picIndex+ " imageFileName: "+ imageFileName);
                    File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                    File image = File.createTempFile(imageFileName,".jpg",storageDir);
                    Log.e(TAG, image.getAbsolutePath());
                    output = new FileOutputStream(image);
                    output.write(bytes);
                    //PictureForwarder p = new PictureForwarder(bytes);
                    //p.start();
                } finally {
                    if (null != output) {
                        output.close();
                    }
                }
            }
        };

        reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);
        final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                super.onCaptureCompleted(session, request, result);
                Toast.makeText(MainActivity.this, "Saved:", Toast.LENGTH_SHORT).show();
                createCameraPreview();
            }
            @Override
            public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure){
                super.onCaptureFailed(session, request, failure);
                Log.e(TAG, "inside onCaptureFailed");
            }
        };
        cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(CameraCaptureSession session) {
                try {
                    session.captureBurst(captureBuilderList, captureListener, mBackgroundHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
            @Override
            public void onConfigureFailed(CameraCaptureSession session) {
            }
        }, mBackgroundHandler);
    }

    protected void takeOnePicture(final CaptureRequest captureBuilderRequest, ImageReader reader, List<Surface> outputSurfaces) throws CameraAccessException {
        ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.capacity()];
                    buffer.get(bytes);
                    save(bytes);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (image != null) {
                        image.close();
                    }
                }
            }
            private void save(byte[] bytes) throws IOException {
                OutputStream output = null;
                try {
                    Log.e(TAG, "inside save " + bytes.length);
                    String imageFileName = "JPEG_";
                    File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                    File image = File.createTempFile(imageFileName,".jpg",storageDir);
                    Log.e(TAG, image.getAbsolutePath());
                    output = new FileOutputStream(image);
                    output.write(bytes);
                } finally {
                    if (null != output) {
                        output.close();
                    }
                }
            }
        };

        reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);
        final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                super.onCaptureCompleted(session, request, result);
                Toast.makeText(MainActivity.this, "Saved:", Toast.LENGTH_SHORT).show();

                //createCameraPreview();
            }
            @Override
            public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure){
                super.onCaptureFailed(session, request, failure);
                Log.e(TAG, "inside onCaptureFailed");
            }
        };
        cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(CameraCaptureSession session) {
                try {
                    //session.captureBurst(captureBuilderList, captureListener, mBackgroundHandler);
                    //captureBurst
                    session.capture(captureBuilderRequest, captureListener, mBackgroundHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
            @Override
            public void onConfigureFailed(CameraCaptureSession session) {
            }
        }, mBackgroundHandler);
    }
    protected void takePicture() {
        //Reset number of Pics and image list
        numPicsTaken = 0;
        pics.clear();

        picIndex = 0;
        if(null == cameraDevice) {
            Log.e(TAG, "cameraDevice is null");
            return;
        }
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        List<CaptureRequest> captureBuilderList = new ArrayList<>();
        ImageReader reader;
        List<Surface> outputSurfaces = new ArrayList<Surface>();

        try {
            CameraCharacteristics characteristics = null;
            try {
                characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            Size[] jpegSizes = null;
            if (characteristics != null) {
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            }
            int width = 640;
            int height = 480;
            if (jpegSizes != null && 0 < jpegSizes.length) {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }

            //ImageReader with ImageFormat = JPEG
            reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            //reader = ImageReader.newInstance(width, height, ImageFormat.RAW10, 1);
            //reader = ImageReader.newInstance(width, height, ImageFormat.FLEX_RGB_888, 1);

            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));

            for(int i = 0; i < numImages; i++){
                final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL);
                captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
                captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, (long) (exposures.get(i)));
                captureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY,1600);
                captureBuilder.addTarget(reader.getSurface());
                captureBuilderList.add(captureBuilder.build());
            }

            takeMultiplePictures(captureBuilderList, reader, outputSurfaces);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    protected void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback(){
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    //The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }
                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }
                @Override
                public void onReady(@NonNull CameraCaptureSession cameraCaptureSession) {
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.i(TAG, "opening Camera");
        try {
            cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            // Add permission for camera and let user grant the permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                return;
            }
            manager.openCamera(cameraId, stateCallback, null);
            Log.i(TAG, "opened Camera");
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    protected void updatePreview() {

        // If we have taken all the necessary pictures in `TakePicture()`
        //      go ahead and start up the AsyncTask to generate the HDR image
        if(generateHDR){
            asyncHDRTask = new HDRCreatorTask();
            asyncHDRTask.saveFcn = this;
            asyncHDRTaskParams = new HDRCreatorTaskParams(pics, (ArrayList<Integer>) exposures, imageDimension);
            asyncHDRTask.execute(asyncHDRTaskParams);
            generateHDR = false;
            numPicsTaken = 0;
            pics.clear();
        }
        if(null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

        //Hack to fix a camera open crash after taking a burst of pictures
        // TODO: investigate a proper fix
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
                } catch (CameraAccessException e) {
                    Log.e(TAG, "Failed to start camera preview because it couldn't access camera", e);
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Failed to start camera preview.", e);
                }
            }
        }, 50);

    }
    private void closeCamera() {
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != imageReader) {
            imageReader.close();
            imageReader = null;
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(MainActivity.this, "Sorry!!!, you can't use this app without granting permission", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    public void saveImage(Mat image) {
        Log.i(TAG, "Trying to save image");
        String imageFileName = "HDR_";

        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        Log.i(TAG, storageDir.getAbsolutePath());
        File imgFile = null;
        try {
            imgFile = File.createTempFile(imageFileName,".hdr",storageDir);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Log.i(TAG, imgFile.getAbsolutePath());
        Boolean saved = Imgcodecs.imwrite(imgFile.getAbsolutePath(), image);
        Log.i(TAG, "saving ? " + saved);
    }
}

