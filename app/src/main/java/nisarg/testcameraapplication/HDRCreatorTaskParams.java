package nisarg.testcameraapplication;

import org.opencv.core.Mat;

import java.util.ArrayList;

public class HDRCreatorTaskParams {
    ArrayList<byte[]> paramsPics;
    ArrayList<Float> paramsExposures;
    android.util.Size paramsImageDimension;

    public HDRCreatorTaskParams(ArrayList<byte[]> pics,
                                ArrayList<Integer> input_exposures,
                                android.util.Size imageDimension) {

        paramsPics = new ArrayList<>();
        paramsExposures = new ArrayList<>();
        //Do a deep copy of the pics ArrayList
        for(int i = 0; i < pics.size(); i++){
            int picSize = pics.get(i).length;
            byte[] singlePic = new byte[picSize];
            System.arraycopy(pics.get(i),0, singlePic, 0, picSize);
            this.paramsPics.add(singlePic);
        }

        //Do a deep copy of the exposures ArrayList
        for(int i = 0; i < input_exposures.size(); i++){
            int exposure = input_exposures.get(i);
            this.paramsExposures.add((float) (exposure / 1000000000.0));
        }

        //Shallow copying
        /*
        this.paramsPics = (ArrayList<byte[]>) pics.clone();
        this.paramsExposures = (ArrayList<Integer>) input_exposures.clone();
         */
        this.paramsImageDimension = imageDimension;
    }
}
