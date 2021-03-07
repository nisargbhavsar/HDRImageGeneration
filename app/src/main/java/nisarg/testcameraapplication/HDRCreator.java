package nisarg.testcameraapplication;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.os.Bundle;
import android.os.Environment;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.CalibrateDebevec;
import org.opencv.photo.MergeDebevec;
import org.opencv.photo.MergeMertens;
import org.opencv.photo.Photo;
import org.opencv.photo.Tonemap;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

import static android.content.Context.*;
import static org.opencv.core.CvType.CV_8UC3;

public class HDRCreator extends Thread {
    private static final String TAG = "HDRCreator";
    private List<Mat> bracketedPictures = new ArrayList<Mat>();

    //List of exposure times in ns
    private List<Integer> exposures = new ArrayList<Integer>();
    private Size picSizePixels;
    private CameraBridgeViewBase mOpenCvCameraView;

    /*
    public HDRCreator(ArrayList<byte[]> pics, Size picSizePixels) {
        bracketedPictures = (ArrayList<byte[]>) pics.clone();
        Log.e(TAG, "bracketedPictures.size(): "+ bracketedPictures.size());
        Log.e(TAG, "picSizePixels.getHeight():" + picSizePixels.getHeight() + " picSizePixels.getWidth(): " + picSizePixels.getWidth());
    }
    */
    public HDRCreator(ArrayList<byte[]> pics, ArrayList<Integer> input_exposures, android.util.Size imageDimension) {
        System.loadLibrary("opencv_java3");
        picSizePixels = new Size(imageDimension.getWidth(), imageDimension.getHeight());
        Log.e(TAG, "picSizePixels.height: " +picSizePixels.height + " picSizePixels.width " + picSizePixels.width);

        for(int i = 0; i < pics.size(); i++){
            //Mat img = new Mat(picSizePixels, CV_8UC3);
            //img.put(0,0,pics.get(i));
            Mat img = new MatOfByte(pics.get(i));
            Imgproc.resize(img, img, picSizePixels);
            bracketedPictures.add(img);
        }
        Log.e(TAG, "bracketedPictures.size(): "+ bracketedPictures.size());
        exposures = (List<Integer>) input_exposures.clone();
    }

    @Override
    public void run(){
        Log.e(TAG, "run HDRCreator ");
        Log.e(TAG, "bracketedPictures.size(): "+ bracketedPictures.size());
        Mat response = new Mat();

        CalibrateDebevec calibrate = Photo.createCalibrateDebevec();
        Mat matTimes = new Mat(exposures.size(), 1, CvType.CV_32F);
        float[] arrayTimes = new float[(int) (matTimes.total()*matTimes.channels())];
        for (int i = 0; i < exposures.size(); i++) {
            arrayTimes[i] = exposures.get(i);
        }
        matTimes.put(0, 0, arrayTimes);
        calibrate.process(bracketedPictures, response, matTimes);
        Mat hdr = new Mat();
        MergeDebevec mergeDebevec = Photo.createMergeDebevec();
        mergeDebevec.process(bracketedPictures, hdr, matTimes);
        Mat ldr = new Mat();
        Tonemap tonemap = Photo.createTonemap(2.2f);
        tonemap.process(hdr, ldr);
        Mat fusion = new Mat();
        MergeMertens mergeMertens = Photo.createMergeMertens();
        mergeMertens.process(bracketedPictures, fusion);
        Core.multiply(fusion, new Scalar(255,255,255), fusion);
        Core.multiply(ldr, new Scalar(255,255,255), ldr);

        //Imgcodecs.imwrite("fusion.png", fusion);
        //Imgcodecs.imwrite("ldr.png", ldr);
        //Imgcodecs.imwrite("hdr.hdr", hdr);

        String imageFileName = "HDR_";
        File storageDir = Environment.getExternalStorageDirectory();
        //File storageDir = Context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        Log.e(TAG, storageDir.getAbsolutePath());
        File image = null;
        try {
            image = File.createTempFile(imageFileName,".jpg",storageDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.e(TAG, image.getAbsolutePath());
        OutputStream output = null;
        try {
            output = new FileOutputStream(image);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        int bufferSize = hdr.channels()*hdr.cols()*hdr.rows();
        byte [] b = new byte[bufferSize];
        hdr.get(0,0,b); // get all the pixels

        try {
            output.write(b);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
