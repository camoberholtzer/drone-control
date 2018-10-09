package cs.msoevoicecontrol.edu.msoevoicecontrol;
/**
 * This Activity contains the main GUI for use when the drone is in flight.  The GUI
 * shows the livestream of the video and allows users to control the drone
 * @author Derek Riley
 */

import android.Manifest;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.TextureView.SurfaceTextureListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;

import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJIError;
import dji.common.flightcontroller.CompassCalibrationState;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.common.product.Model;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;
import dji.sdk.flightcontroller.Compass;
import dji.sdk.flightcontroller.FlightController;
import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;

public class MainActivity extends Activity implements SurfaceTextureListener,OnClickListener,RecognitionListener{

    private static final String TAG = MainActivity.class.getName();
    protected VideoFeeder.VideoDataCallback mReceivedVideoDataCallBack = null;

    private final float sensitivity = (float) 0.5;
    private static final String DIRECTIONS_COMMANDS = "directions";
    private static final String ALL_DONE = "finish";
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;
    private SpeechRecognizer speechRecognizer;
    private HashMap<String, Integer> captions;

    private static double LEFT_ROTATION_AMOUNT = -8.0;
    private static double RIGHT_ROTATION_AMOUNT = 8.0;
    private static double FORWARD_MOVE_DISTANCE = -4.0;
    private static double BACK_MOVE_DISTANCE = 4.0;
    private static double UP_MOVE_DISTANCE = 0.2;
    private static double DOWN_MOVE_DISTANCE = -0.2;

    private float pitch=0;
    private float roll=0;
    private float yaw=0;
    private float throttle=0;

    private DJICodecManager mCodecManager = null;
    private TextureView mVideoSurface = null;
    private Button mTakeOffBtn, mLandBtn,mLeftBtn,mRightBtn,mForwardBtn,mBackBtn,mUpBtn,mDownBtn,mCalibrateBtn,mSpinBtn;
    private ToggleButton mVoiceControlBtn;
    private TextView heading;

    private FlightController flightController;
    private Handler handler;
    private Compass compass;

    /**
     * This method is called when this activity is first starting (created)
     * It sets up the objects, GUI, and listeners for the application.
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(R.layout.activity_main);
        handler = new Handler();
        initUI();

        captions = new HashMap<>();
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
            return;
        }
        new SetupTask(this).execute();

        // The callback for receiving the raw H264 video data for camera live view
        mReceivedVideoDataCallBack = new VideoFeeder.VideoDataCallback() {
            @Override
            public void onReceive(byte[] videoBuffer, int size) {
                if (mCodecManager != null) {
                    mCodecManager.sendDataToDecoder(videoBuffer, size);
                }
            }
        };

        //Setting up the flight controller parameters
        if (ModuleVerificationUtil.isFlightControllerAvailable()) {
            flightController = FPVApplication.getAircraftInstance().getFlightController();
            flightController.setStateCallback(new FlightControllerState.Callback() {
                @Override
                public void onUpdate(@NonNull FlightControllerState flightControllerState) {
                    String orientationMode = flightControllerState.getOrientationMode().name();
                    System.err.println("Orientation mode changed " + orientationMode);
                }
            });
            flightController.setVirtualStickModeEnabled(true, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (null == djiError) {
                        showToast("Success enabling virtual stick");
                    } else {
                        showToast("failure "+ djiError.getDescription());
                    }
                }
            });
            flightController.setRollPitchControlMode(RollPitchControlMode.ANGLE);
            flightController.setYawControlMode(YawControlMode.ANGLE);
            flightController.setVerticalControlMode(VerticalControlMode.VELOCITY);
            flightController.setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY);
            compass = flightController.getCompass();
        }
    }

    /**
     * This method connects the GUI elements to the code for viewing and manipulation
     */
    private void initUI() {
        //Registration
        mVideoSurface = (TextureView)findViewById(R.id.video_previewer_surface);
        if (null != mVideoSurface) {
            mVideoSurface.setSurfaceTextureListener(this);
        }
        mTakeOffBtn = (Button) findViewById(R.id.btn_take_off);
        mLandBtn = (Button) findViewById(R.id.btn_land);
        mLeftBtn = (Button) findViewById(R.id.btn_left);
        mRightBtn = (Button) findViewById(R.id.btn_right);
        mForwardBtn = (Button) findViewById(R.id.btn_forward);
        mBackBtn = (Button) findViewById(R.id.btn_back);
        mUpBtn = (Button) findViewById(R.id.btn_up);
        mDownBtn = (Button) findViewById(R.id.btn_down);
        mCalibrateBtn = (Button) findViewById(R.id.btn_calibrate);
        mVoiceControlBtn = (ToggleButton) findViewById(R.id.btn_voice_control);
        mSpinBtn = (Button) findViewById(R.id.spinButton);
        heading = (TextView) findViewById(R.id.heading);

        //Event handler listeners
        mTakeOffBtn.setOnClickListener(this);
        mLandBtn.setOnClickListener(this);
        mLeftBtn.setOnClickListener(this);
        mRightBtn.setOnClickListener(this);
        mForwardBtn.setOnClickListener(this);
        mBackBtn.setOnClickListener(this);
        mUpBtn.setOnClickListener(this);
        mDownBtn.setOnClickListener(this);
        mCalibrateBtn.setOnClickListener(this);
        mSpinBtn.setOnClickListener(this);

        mVoiceControlBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v){
                startVoiceControl();
            }
        });

    }

    /**
     * This helper method allows setup of the voice recognition to happen in the background
     */
    private static class SetupTask extends AsyncTask<Void, Void, Exception> {
        WeakReference<MainActivity> activityReference;
        SetupTask(MainActivity activity) {
            this.activityReference = new WeakReference<>(activity);
        }
        @Override
        protected Exception doInBackground(Void... params) {
            try {
                Assets assets = new Assets(activityReference.get());
                File assetDir = assets.syncAssets();
                activityReference.get().setupRecognizer(assetDir);
            } catch (IOException e) {
                return e;
            }
            return null;
        }
        @Override
        protected void onPostExecute(Exception result) {
        }
    }

    /**
     * This method is only called if the drone type changes in this activity
     */
    protected void onProductChange() {
        initPreviewer();
    }

    /**
     * This manages the event when the permissions request comes back from the user (speech)
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull  int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Recognizer initialization is a time-consuming and it involves IO,
                // so we execute it in async task
                new SetupTask(this).execute();
            } else {
                finish();
            }
        }
    }

    /**
     * In partial result we get quick updates about current hypothesis. In
     * keyword spotting mode we can react here, in other modes we need to wait
     * for final result in onResult.
     */
    @Override
    public void onPartialResult(Hypothesis hypothesis) {
    }

    /**
     * This is called when we stop the recognizer.
     */
    @Override
    public void onResult(Hypothesis hypothesis) {
        if (hypothesis != null) {
            String text = hypothesis.getHypstr();
            if (text.equals("backward"))
                text = "back";
            if(text.equals(ALL_DONE) || text.equals("Land")){
                speechRecognizer.cancel();
                showToast("all done");
            } else {
                //voice command recognized!
                if (text.equals("up") || text.equals("fly up"))
                    rightJoystickInput(0, UP_MOVE_DISTANCE);
                else if (text.equals("down") || text.equals("fly down"))
                    rightJoystickInput(0, DOWN_MOVE_DISTANCE);
                else if (text.equals("left") || text.equals("turn left"))
                    rightJoystickInput(LEFT_ROTATION_AMOUNT, 0);
                else if (text.equals("right") || text.equals("turn right"))
                    rightJoystickInput(RIGHT_ROTATION_AMOUNT, 0);
                else if (text.equals("forward") || text.equals("move forward"))
                    leftJoystickInput(0, FORWARD_MOVE_DISTANCE);
                else if (text.equals("back") || text.equals("move backward"))
                    leftJoystickInput(0, BACK_MOVE_DISTANCE);
            }
            showToast(text);
        }
    }

    /**
     * This method is called when the recognizer starts to recongize speech
     */
    @Override
    public void onBeginningOfSpeech() {
        speechRecognizer.startListening(DIRECTIONS_COMMANDS, 1000);
    }

    /**
     * We stop the speech recognizer here to get the result
     */
    @Override
    public void onEndOfSpeech() {
        speechRecognizer.stop();
        speechRecognizer.startListening(DIRECTIONS_COMMANDS);
    }

    /**
     * Recognizer initialization
     * @param assetsDir
     * @throws IOException
     */
    private void setupRecognizer(File assetsDir) throws IOException {
        speechRecognizer = SpeechRecognizerSetup.defaultSetup()
                .setAcousticModel(new File(assetsDir, "en-us-ptm"))
                .setDictionary(new File(assetsDir, "cmudict-en-us.dict"))
                .getRecognizer();
        speechRecognizer.addListener(this);
        File myGrammar = new File(assetsDir, "digits.gram");
        speechRecognizer.addGrammarSearch(DIRECTIONS_COMMANDS, myGrammar);
    }

    /**
     * When an error occurs this is called
     * @param error
     */
    @Override
    public void onError(Exception error) {
        showToast(error.toString());
    }

    /**
     * When a command times out, this is called
     */
    @Override
    public void onTimeout() {
    }

    /**
     * When the app starts or resumes this is called
     */
    @Override
    public void onResume() {
        Log.e(TAG, "onResume");
        super.onResume();
        initPreviewer();
        onProductChange();
        if(mVideoSurface == null) {
            Log.e(TAG, "mVideoSurface is null");
        }
    }

    /**
     * When the app goes into the background, this is called
     */
    @Override
    public void onPause() {
        Log.e(TAG, "onPause");
        uninitPreviewer();
        super.onPause();
    }

    /**
     * When the app is done, this is called
     */
    @Override
    public void onStop() {
        Log.e(TAG, "onStop");
        super.onStop();
    }

    /**
     * When the app restarts, this is called
     * @param view
     */
    public void onReturn(View view){
        Log.e(TAG, "onReturn");
        this.finish();
    }

    /**
     * When the OS reclaims the memory for the app, this is called
     */
    @Override
    protected void onDestroy() {
        Log.e(TAG, "onDestroy");
        uninitPreviewer();
        super.onDestroy();
        if (speechRecognizer != null) {
            stopVoiceControl();
        }
    }

    /**
     * This initializes the video stream portion of the app
     */
    private void initPreviewer() {
        BaseProduct product = FPVApplication.getProductInstance();
        if (product == null || !product.isConnected()) {
            showToast(getString(R.string.disconnected));
        } else {
            if (null != mVideoSurface) {
                mVideoSurface.setSurfaceTextureListener(this);
            }
            if (!product.getModel().equals(Model.UNKNOWN_AIRCRAFT)) {
                VideoFeeder.getInstance().getPrimaryVideoFeed().setCallback(mReceivedVideoDataCallBack);
            }
        }
    }

    /**
     * This uninitializes the video stream from the app to free resources
     */
    private void uninitPreviewer() {
        Camera camera = FPVApplication.getCameraInstance();
        if (camera != null){// Reset the callback
            VideoFeeder.getInstance().getPrimaryVideoFeed().setCallback(null);
        }
    }

    /**
     * This initializes the viewable stream
     * @param surface
     * @param width
     * @param height
     */
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "onSurfaceTextureAvailable");
        if (mCodecManager == null) {
            mCodecManager = new DJICodecManager(this, surface, width, height);
        }
    }

    /**
     * This is called when the surface changes for display
     * @param surface
     * @param width
     * @param height
     */
    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "onSurfaceTextureSizeChanged");
    }

    /**
     * This is called when the surface is destroyed
     * @param surface
     * @return
     */
    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.e(TAG,"onSurfaceTextureDestroyed");
        if (mCodecManager != null) {
            mCodecManager.cleanSurface();
            mCodecManager = null;
        }
        return false;
    }

    /**
     * This is called when the surface is updated
     * @param surface
     */
    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    /**
     * This is called whenever a button click happens.
     * @param v
     */
    @Override
    public void onClick(View v) {
        FlightController flightController = ModuleVerificationUtil.getFlightController();
        if (flightController == null) {
            return;
        }
        if(compass.getCalibrationState() == CompassCalibrationState.FAILED)
            heading.setText("Calibration Failed");
        else if(compass.getCalibrationState() == CompassCalibrationState.NOT_CALIBRATING)
            heading.setText("Calibrated");
        else if(compass.getCalibrationState() == CompassCalibrationState.UNKNOWN)
            heading.setText("Please Calibrate!");
        else if(compass.getCalibrationState() == CompassCalibrationState.SUCCESSFUL)
            heading.setText("Calibration Successful");
        else
            heading.setText("unknown");

        switch (v.getId()) {
            case R.id.btn_calibrate:{
                compass.startCalibration(new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        if(null == djiError){
                            showToast("calibration complete");
                        }
                        else {
                            showToast("calibration error "+ djiError.getDescription());
                        }
                    }});
                break;
            }
            case R.id.btn_take_off:{
                flightController.startTakeoff(new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        if (null == djiError) {
                            showToast("Taking off...");
                        } else {
                            showToast("failure "+ djiError.getDescription());
                        }
                    }
                });
                break;
            }
            case R.id.btn_land:{
                flightController.startLanding(new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        if (null == djiError) {
                            showToast("Landing...");
                        } else {
                            showToast("failure "+ djiError.getDescription());
                        }
                    }
                });
                break;
            }
            case R.id.btn_left:{
                rightJoystickInput(LEFT_ROTATION_AMOUNT, 0);
                break;
            }
            case R.id.btn_right:{
                rightJoystickInput(RIGHT_ROTATION_AMOUNT,0);
                break;
            }
            case R.id.btn_forward:{
                leftJoystickInput(0, FORWARD_MOVE_DISTANCE);
                break;
            }
            case R.id.btn_back: {
                leftJoystickInput(0, BACK_MOVE_DISTANCE);
                break;
            }
            case R.id.btn_up: {
                rightJoystickInput(0, UP_MOVE_DISTANCE);
                break;
            }
            case R.id.btn_down: {
                rightJoystickInput(0, DOWN_MOVE_DISTANCE);
                break;
            }
            case R.id.spinButton: {
                rightJoystickInput(180, 0);
            }
            default:
                break;
        }
    }

    /**
     * This starts the speech recognizer
     */
    private void startVoiceControl() {
        speechRecognizer.startListening(DIRECTIONS_COMMANDS);
    }

    /**
     * This stops the speech recognizer
     */
    private void stopVoiceControl() {
        speechRecognizer.cancel();
        speechRecognizer.shutdown();
    }

    /**
     * This emulates the left joystick being moved and released
     * @param pX x-axis amount
     * @param pY y-axis amount
     */
    private void leftJoystickInput(double pX, double pY){//left joystick pX, pY
        if (Math.abs(pX) < 0.02)
            pX = 0;
        if (Math.abs(pY) < 0.02)
            pY = 0;
        pitch = (float)pY;
        roll = (float)pX;
        yaw = 0;
        throttle = 0;
        FPVApplication.getAircraftInstance()
                .getFlightController()
                .sendVirtualStickFlightControlData(new FlightControlData(pitch,
                                roll,
                                yaw,
                                throttle),
                        new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {
                                if (null == djiError) {
                                    showToast("LeftStickCommandExecuted");
                                } else {
                                    showToast("failure "+ djiError.getDescription());
                                }
                            }
                        });
    }

    /**
     * This emulates the right joystick being moved and released
     * @param pX x-axis
     * @param pY y-axis
     */
    public void rightJoystickInput(double pX, double pY) {//right joystick pX,pY
        if (Math.abs(pX) < 0.02)
            pX = 0;
        if (Math.abs(pY) < 0.02)
            pY = 0;
        yaw = (float)pX;
        throttle = (float)pY;
        pitch = 0;
        roll = 0;
        FPVApplication.getAircraftInstance()
                .getFlightController()
                .sendVirtualStickFlightControlData(new FlightControlData(pitch,
                                roll,
                                yaw,
                                throttle),
                        new CommonCallbacks.CompletionCallback() { //1 is throttle
                            @Override
                            public void onResult(DJIError djiError) {
                                if (null == djiError) {
                                    showToast("RightStickCommandExecuted");
                                } else {
                                    showToast("failure "+ djiError.getDescription());
                                }
                            }
                        });
    }

    /**
     * This displays a toast notification
     * @param msg message to be displayed
     */
    public void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }
}

