package nisarg.testcameraapplication;

import android.os.AsyncTask;
import android.util.Log;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.CalibrateDebevec;
import org.opencv.photo.MergeDebevec;
import org.opencv.photo.MergeMertens;
import org.opencv.photo.Photo;
import org.opencv.photo.Tonemap;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("deprecation")
public class HDRCreatorTask extends AsyncTask<HDRCreatorTaskParams, Void, Mat> {

    public PictureSaver saveFcn;

    private static final String TAG = "HDRCreatorTask";
    private List<Mat> bracketedPictures = new ArrayList<Mat>();

    //List of exposure times in ns
    private List<Integer> exposures = new ArrayList<Integer>();
    private Size picSizePixels;
    private CameraBridgeViewBase mOpenCvCameraView;

    @Override
    protected Mat doInBackground(HDRCreatorTaskParams... params) {
        System.loadLibrary("opencv_java3");
        Log.e(TAG, "doInBackground HDRCreatorTask ");

        picSizePixels = new Size(params[0].paramsImageDimension.getWidth(), params[0].paramsImageDimension.getHeight());
        Log.i(TAG, "picSizePixels.height: " +picSizePixels.height + " picSizePixels.width " + picSizePixels.width);

        for(int i = 0; i < params[0].paramsPics.size(); i++){
            Mat img = new MatOfByte(params[0].paramsPics.get(i));
            Imgproc.resize(img, img, picSizePixels);
            bracketedPictures.add(img);
        }
        Log.i(TAG, "bracketedPictures.size(): "+ bracketedPictures.size());
        exposures = (List<Integer>) params[0].paramsExposures.clone();

        Log.i(TAG, "bracketedPictures.size(): "+ bracketedPictures.size());
        Mat response = new Mat();
        Mat hdr = new Mat();
        Mat ldr = new Mat();
        Mat fusion = new Mat();

        CalibrateDebevec calibrate = Photo.createCalibrateDebevec();
        Mat matTimes = new Mat(exposures.size(), 1, CvType.CV_32F);
        float[] arrayTimes = new float[(int) (matTimes.total()*matTimes.channels())];
        for (int i = 0; i < exposures.size(); i++) {
            arrayTimes[i] = exposures.get(i);
        }
        matTimes.put(0, 0, arrayTimes);
        calibrate.process(bracketedPictures, response, matTimes);

        MergeDebevec mergeDebevec = Photo.createMergeDebevec();
        mergeDebevec.process(bracketedPictures, hdr, matTimes);

        Tonemap tonemap = Photo.createTonemap(2.2f);
        tonemap.process(hdr, ldr);

        MergeMertens mergeMertens = Photo.createMergeMertens();
        mergeMertens.process(bracketedPictures, fusion);
        Core.multiply(fusion, new Scalar(255,255,255), fusion);
        Core.multiply(ldr, new Scalar(255,255,255), ldr);
        return ldr.clone();
    }

    @Override
    protected void onPostExecute(Mat generatedHDRImage){
        saveFcn.saveImage(generatedHDRImage);
    }
}
