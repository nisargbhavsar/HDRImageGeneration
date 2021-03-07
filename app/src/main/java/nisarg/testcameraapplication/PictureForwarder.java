package nisarg.testcameraapplication;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;

public class PictureForwarder extends Thread {
    private static final String TAG = "PictureForwarder";
    public byte[] mCameraData;
    InetAddress addr = InetAddress.getByName("192.168.0.17");
    public URL url  = new URL("http://"+addr.getHostAddress()+":8000");

    PictureForwarder(byte[] inputCameraData) throws MalformedURLException, UnknownHostException {
        this.mCameraData = inputCameraData;
    }

    @Override
    public void run(){
        Log.e(TAG, "run: " + url.toString());
        HttpURLConnection urlConnection = null;
        try {
            urlConnection = (HttpURLConnection) url.openConnection();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty("Content-Type", "image/jpeg;");
            urlConnection.setRequestProperty("Content-Length", ""+mCameraData.length);
            urlConnection.setDoOutput(true);
            urlConnection.setChunkedStreamingMode(0);

            OutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());
            writeStream(out);
            out.flush();
            out.close();
            //InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            //readStream(in);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            urlConnection.disconnect();
        }
    }

     private void writeStream(OutputStream outputStream){
         try {
             outputStream.write(mCameraData);
         } catch (IOException e) {
             e.printStackTrace();
         }
     }
}