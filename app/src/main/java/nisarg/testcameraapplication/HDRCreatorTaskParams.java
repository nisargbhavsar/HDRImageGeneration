package nisarg.testcameraapplication;

import java.util.ArrayList;
import android.util.Size;

public class HDRCreatorTaskParams {
    ArrayList<byte[]> paramsPics;
    ArrayList<Integer> paramsExposures;
    android.util.Size paramsImageDimension;

    public HDRCreatorTaskParams(ArrayList<byte[]> pics,
                                ArrayList<Integer> input_exposures,
                                android.util.Size imageDimension) {
        this.paramsPics = (ArrayList<byte[]>) pics.clone();
        this.paramsExposures = (ArrayList<Integer>) input_exposures.clone();
        this.paramsImageDimension = imageDimension;
    }
}
