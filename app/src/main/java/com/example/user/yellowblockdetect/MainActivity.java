package com.example.user.yellowblockdetect;

import android.annotation.TargetApi;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Toast;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity
        implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "opencv";
    private JavaCameraView mOpenCvCameraView;
    private Mat matInput;
    private Mat matResult;
    public native int[] ProcessFrame(long matAddrInput, long matAddrResult);
    static {
        System.loadLibrary("opencv_java3");
        System.loadLibrary("native-lib");
    }

    int[] jni_data = new int[40+2];
    String log_txt = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!hasPermissions(PERMISSIONS)) {

                requestPermissions(PERMISSIONS, PERMISSIONS_REQUEST_CODE);
            }
        }

        mOpenCvCameraView = (JavaCameraView) findViewById(R.id.camera);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.setCameraIndex(0); // front-camera(1),  back-camera(0)

        mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        mMainHandler = new SendMassgeHandler();
    }
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    // <핸들러>
    private static final int SEND_THREAD_INFOMATION = 0;
    private static final int SEND_THREAD_STOP_MESSAGE = 1;

    private SendMassgeHandler mMainHandler = null;
    class SendMassgeHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            switch (msg.what) {
                // ** 여기에 장애인 알림 서비스 메소드 추가 **
                case SEND_THREAD_INFOMATION:
                    startService(new Intent(MainActivity.this, TTSService.class).putExtra("str","slope = " + msg.arg2));
                    Toast.makeText(getApplicationContext(), "slope = " + msg.arg2 + "  \n" + msg.obj, Toast.LENGTH_SHORT).show();
                    //debugText.setText("현재각도 = " + msg.arg1 + "\n" +"slope = " + msg.arg2 + "  \n" + msg.obj);
                    break;
                case SEND_THREAD_STOP_MESSAGE:
                    Toast.makeText(getApplicationContext(), "Thread가 중지 되었습니다.", Toast.LENGTH_LONG).show();
                    //debugText.setText("Thread가 중지 되었습니다.");
                    break;
                default:
                    break;
            }
        }
    };
    // </핸들러>

    // <C++ 영상처리 함수 호출 영역>
    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        matInput = inputFrame.rgba();
        if ( matResult != null ) matResult.release();
        matResult = new Mat(matInput.rows(), matInput.cols(), matInput.type());
        jni_data = ProcessFrame(matInput.getNativeObjAddr(), matResult.getNativeObjAddr());

        // 핸들러에 메세지 가공해서 전달하는 영역

        if(jni_data[0] > 0) {
            int x = matInput.cols();
            int y = matInput.rows()/2;
            double[] slope = new double[20];
            double averageSlope = 0.0f;
            boolean check = false;
            for (int i = 0; i < jni_data[0]; i++) {
                if(jni_data[i * 2 + 1] >= x * 0.9)
                    check = true;
                if (jni_data[i * 2 + 2] != y)
                    slope[i] = ((double) jni_data[i * 2 + 1] - x) / (jni_data[i * 2 + 2] - y);
                else
                    slope[i] = jni_data[i * 2 + 1] - x;
                averageSlope += slope[i];
            }

            averageSlope /= jni_data[0];

            Arrays.sort(slope, 0, jni_data[0]);

            Message msg = mMainHandler.obtainMessage();
            msg.what = SEND_THREAD_INFOMATION;
            msg.arg1 = 30;
            msg.arg2 = (int)jni_data[41];

            final double[] angle = {7.111, 4, 1.1851};
            String hi;
            if (slope[0] * slope[jni_data[0] - 1] < 0)
            {
                hi = new String("전방 ["+averageSlope+"]");
                if(angle[1] <= averageSlope && averageSlope < angle[0])
                    hi += "1시 방향";
                else if(angle[2] <= averageSlope && averageSlope < angle[1])
                    hi += "2시 방향";
                else if(averageSlope < angle[2])
                    hi += "3시 방향";
                else if(-angle[1] > averageSlope && averageSlope >= -angle[0])
                    hi += "11시 방향";
                else if(-angle[2] > averageSlope && averageSlope >= -angle[1])
                    hi += "10시 방향";
                else if(averageSlope >= -angle[2])
                    hi += "9시 방향";
                msg.obj = hi;
            }
            else if (slope[0] > 0)
            {
                hi = new String("오른쪽 ["+averageSlope+"]");
                if(angle[1] <= averageSlope && averageSlope < angle[0])
                    hi += "1시 방향";
                else if(angle[2] <= averageSlope && averageSlope < angle[1])
                    hi += "2시 방향";
                else if(averageSlope < angle[2])
                    hi += "3시 방향";
                msg.obj = hi;
            }
            else
            {
                hi = new String("왼쪽 ["+averageSlope+"]");
                if(-angle[1] > averageSlope && averageSlope >= -angle[0])
                    hi += "11시 방향";
                else if(-angle[2] > averageSlope && averageSlope >= -angle[1])
                    hi += "10시 방향";
                else if(averageSlope >= -angle[2])
                    hi += "9시 방향";
                msg.obj = hi;
            }
            Log.d("Debug :: " , hi+"/"+check);
//            mMainHandler.sendMessage(msg);

//            try { Thread.sleep(1000); }
//            catch (InterruptedException e) { e.printStackTrace(); }
        }
        // </핸들러메세지>
        //Log.d("좌표 : ", ""+matInput.rows() + ", " + matInput.cols());

        /*
        int midX = matInput.rows()/2;
        int maxY = matInput.cols();

        if(jni_data[0] > 0) {
            double[] slope = new double[20];
            for (int i = 0; i < jni_data[0]; i++) {
                if (jni_data[i * 2 + 1] != midX)
                    slope[i] = ((double) jni_data[i * 2 + 2] - maxY) / (jni_data[i * 2 + 1] - midX);
                else
                    slope[i] = jni_data[i * 2 + 2] - maxY;
            }
            Arrays.sort(slope);

            if (slope[0] * slope[jni_data[0] - 1] < 0)
                Log.d("방향 : ", "전방 전방 전방");

                //debugText.setText("전방에 있다!!!!");
            else if (slope[0] < 0)
                Log.d("방향 : ", "오른쪽 오른쪽 오른쪽");

                //debugText.setText("오른쪽에 쏠려 있다!!!!");
            else
                Log.d("방향 : ", "전방 전방 전방");

            //debugText.setText("왼쪽에 쏠려 있다!!!!");
        }
        */
        return matResult;
    }
    // </C++ 영상처리 함수 호출 영역>

    // < 카메라 - 수정 X >
    @Override
    public void onCameraViewStarted(int width, int height) {}
    @Override
    public void onCameraViewStopped() {}
    // </ 카메라 - 수정 X >
    // < 퍼미션 - 수정 X >
    static final int PERMISSIONS_REQUEST_CODE = 1000;
    String[] PERMISSIONS  = {"android.permission.CAMERA"};
    private boolean hasPermissions(String[] permissions) {
        int result;
        for (String perms : permissions){

            result = ContextCompat.checkSelfPermission(this, perms);

            if (result == PackageManager.PERMISSION_DENIED){
                return false;
            }
        }
        return true;
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,@NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch(requestCode){

            case PERMISSIONS_REQUEST_CODE:
                if (grantResults.length > 0) {
                    boolean cameraPermissionAccepted = grantResults[0]
                            == PackageManager.PERMISSION_GRANTED;

                    if (!cameraPermissionAccepted)
                        showDialogForPermission("앱을 실행하려면 퍼미션을 허가하셔야합니다.");
                }
                break;
        }
    }
    @TargetApi(Build.VERSION_CODES.M)
    private void showDialogForPermission(String msg) {
        AlertDialog.Builder builder = new AlertDialog.Builder( MainActivity.this);
        builder.setTitle("알림");
        builder.setMessage(msg);
        builder.setCancelable(false);
        builder.setPositiveButton("예", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id){
                requestPermissions(PERMISSIONS, PERMISSIONS_REQUEST_CODE);
            }
        });
        builder.setNegativeButton("아니오", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface arg0, int arg1) {
                finish();
            }
        });
        builder.create().show();
    }
    // </ 퍼미션 - 수정 X >
    // <액티비티 - 수정 X >
    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }
    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "onResume :: Internal OpenCV library not found.");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_2_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "onResum :: OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }
    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
        ActivityCompat.finishAffinity(this);
        System.runFinalizersOnExit(true);
        System.exit(0);
    }
    // </액티비티 - 수정 X >
}
