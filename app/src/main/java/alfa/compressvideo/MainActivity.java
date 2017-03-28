package alfa.compressvideo;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.netcompss.ffmpeg4android.CommandValidationException;
import com.netcompss.ffmpeg4android.GeneralUtils;
import com.netcompss.ffmpeg4android.Prefs;
import com.netcompss.loader.LoadJNI;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    public static final String APP_NAME = "DemoVideoCompress";
    public static final int CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE = 200;


    String demoVideoFolder=null;
    String workFolder=null;
    String vkLogPath=null;
    String outputVideoPath=null;

    Button btn_captureVideo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


        btn_captureVideo=(Button)findViewById(R.id.btn_captureVideo);

        btn_captureVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                captureVideo();

            }
        });

    }


    private  void captureVideo()
    {
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        // create a file to save the video
        Uri fileUri = getOutputMediaFileUri();
        // set the  file name
        intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
        //set time limit......
        intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT,20);
        // set the video image quality to high
        intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
        // start the Video Capture Intent
        startActivityForResult(intent,CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE);
    }


    private  Uri getOutputMediaFileUri(){
        return Uri.fromFile(getOutputMediaFile());
    }

    private File getOutputMediaFile(){

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile = new File(getStorageDir().getPath() + File.separator +
                "video-"+ timeStamp + ".mp4");
        return mediaFile;
    }

    private File getStorageDir() {
        File storageDir = null;

        storageDir = new File(Environment.getExternalStorageDirectory(), "/" + APP_NAME);

        if (storageDir != null) {
            if (!storageDir.mkdirs()) {
                if (!storageDir.exists()) {
                    Log.d("CameraSample", "failed to create directory");
                    return null;
                }
            }
        }
        return storageDir;
    }

    private File getImageFile() {
        String Path = Environment.getExternalStorageDirectory() + "/" + APP_NAME;
        File f = new File(Path);
        File imageFiles[] = f.listFiles();

        if (imageFiles == null || imageFiles.length == 0) {
            return null;
        }

        File lastModifiedFile = imageFiles[0];
        for (int i = 1; i < imageFiles.length; i++) {
            if (lastModifiedFile.lastModified() < imageFiles[i].lastModified()) {
                lastModifiedFile = imageFiles[i];
            }
        }
        return lastModifiedFile;
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE) {

            if (resultCode == RESULT_OK) {

                File imageFile = getImageFile();
                String demoVideoPath=imageFile.getAbsolutePath();

                demoVideoFolder=Environment.getExternalStorageDirectory() + "/" + APP_NAME+"/";
                workFolder = getApplicationContext().getFilesDir().getAbsolutePath() + "/";
                vkLogPath = workFolder + "vk.log";


                GeneralUtils.copyLicenseFromAssetsToSDIfNeeded(this, workFolder);
                GeneralUtils.copyDemoVideoFromAssetsToSDIfNeeded(this, demoVideoFolder);

                outputVideoPath=Environment.getExternalStorageDirectory() + "/" + APP_NAME+"/compressedVideo_"+System.currentTimeMillis()+".mp4";
                String commandStr="ffmpeg -y -i "+demoVideoPath+
                        " -strict experimental -vf transpose=1 -s 160x120 -r 30 -aspect 4:3 -ab 48000 -ac 2 -ar 22050 -b 2097k "+outputVideoPath;
                if (GeneralUtils.checkIfFileExistAndNotEmpty(demoVideoPath)) {
                    new TranscdingBackground(MainActivity.this,commandStr).execute();
                }
                else {
                    Toast.makeText(getApplicationContext(), demoVideoPath + " not found", Toast.LENGTH_LONG).show();
                }
            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "User cancelled the video capture.",
                        Toast.LENGTH_LONG).show();

            } else {

                // Video capture failed, advise user
                Toast.makeText(this, "Video capture failed.",
                        Toast.LENGTH_LONG).show();
            }
        }

    }


    private boolean commandValidationFailedFlag = false;
    public class TranscdingBackground extends AsyncTask<String, Integer, Integer>
    {

        ProgressDialog progressDialog;
        Activity _act;
        String commandStr;

        public TranscdingBackground (Activity act,String commandStr) {
            _act = act;
            this.commandStr=commandStr;
        }

        @Override
        protected void onPreExecute() {

            progressDialog = new ProgressDialog(_act);
            progressDialog.setMessage("Video Compresssion is in progress...");
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.show();

        }

        protected Integer doInBackground(String... paths) {
            Log.i(Prefs.TAG, "doInBackground started...");

            // delete previous log
            boolean isDeleted = GeneralUtils.deleteFileUtil(workFolder + "/vk.log");
            Log.i(Prefs.TAG, "vk deleted: " + isDeleted);

            PowerManager powerManager = (PowerManager)_act.getSystemService(Activity.POWER_SERVICE);
            PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VK_LOCK");
            Log.d(Prefs.TAG, "Acquire wake lock");
            wakeLock.acquire();
            LoadJNI vk = new LoadJNI();
            try {
                vk.run(GeneralUtils.utilConvertToComplex(commandStr), workFolder, getApplicationContext());
                GeneralUtils.copyFileToFolder(vkLogPath, demoVideoFolder);

            } catch (CommandValidationException e) {
                Log.e(Prefs.TAG, "vk run exeption.", e);
                commandValidationFailedFlag = true;

            } catch (Throwable e) {
                Log.e(Prefs.TAG, "vk run exeption.", e);
            }
            finally {
                if (wakeLock.isHeld())
                    wakeLock.release();
                else{
                    Log.i(Prefs.TAG, "Wake lock is already released, doing nothing");
                }
            }
            Log.i(Prefs.TAG, "doInBackground finished");
            return Integer.valueOf(0);
        }

        protected void onProgressUpdate(Integer... progress) {
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
        }


        @Override
        protected void onPostExecute(Integer result) {
            Log.i(Prefs.TAG, "onPostExecute");
            progressDialog.dismiss();
            super.onPostExecute(result);

            String rc = null;
            if (commandValidationFailedFlag) {
                rc = "Command Vaidation Failed";
            }
            else {
                rc = GeneralUtils.getReturnCodeFromLog(vkLogPath);
            }
            final String status = rc;
            MainActivity.this.runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(MainActivity.this, "Video compresion completed successfully", Toast.LENGTH_LONG).show();

                    if (status.equals("Transcoding Status: Failed")) {
                        Toast.makeText(MainActivity.this, "Check: " + vkLogPath + " for more information.", Toast.LENGTH_LONG).show();
                    }
                }
            });
        }

    }

}
