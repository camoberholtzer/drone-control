package cs.msoevoicecontrol.edu.msoevoicecontrol;
/**
 * This Activity contains the the initial screen the users see
 * @author Derek Riley
 */
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.gimbal.Gimbal;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;


public class FPVApplication extends Application {
    public static final String FLAG_CONNECTION_CHANGE = "FPV_APP_Connection_change";
    private DJISDKManager.SDKManagerCallback djiSDKManagerCallback;
    private BaseProduct.BaseProductListener djiBaseProductListener;
    private BaseComponent.ComponentListener djiComponentListener;
    private static BaseProduct product;
    public Handler handler;
    private Application instance;

    @Override
    public Context getApplicationContext() {
        return instance;
    }

    public void setContext(Application app) {
        instance = app;
    }

    public static synchronized Aircraft getAircraftInstance() {
        if (!isAircraftConnected()) {
            return null;
        }
        return (Aircraft) getProductInstance();
    }

    public static synchronized BaseProduct getProductInstance() {
        if(product == null) {
            product = DJISDKManager.getInstance().getProduct();
        }
        return product;
    }

    public static boolean isAircraftConnected() {
        return getProductInstance() != null && getProductInstance() instanceof Aircraft;
    }

    public static synchronized Camera getCameraInstance() {
        if(getProductInstance() == null)
            return null;
        Camera camera = null;
        if(getProductInstance() instanceof Aircraft)
            camera = ((Aircraft) getProductInstance()).getCamera();
        return camera;
    }

    public static synchronized Gimbal getGimbalInstance() {
        if(getProductInstance() == null)
            return null;
        Gimbal gimbal = null;
        if (getProductInstance() instanceof Aircraft)
            gimbal = getProductInstance().getGimbal();
        return gimbal;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        djiComponentListener = new BaseComponent.ComponentListener()
        {
            @Override
            public void onConnectivityChange(boolean inSonnected)
            {
                notifyStatusChange();
            }
        };

        djiBaseProductListener = new BaseProduct.BaseProductListener() {
            @Override
            public void onComponentChange(BaseProduct.ComponentKey componentKey, BaseComponent oldComponent, BaseComponent newComponent) {
                if (newComponent != null)
                    newComponent.setComponentListener(djiComponentListener);
                notifyStatusChange();
            }

            @Override
            public void onConnectivityChange(boolean b)
            {
                notifyStatusChange();
            }
        };

        djiSDKManagerCallback = new DJISDKManager.SDKManagerCallback() {
            @Override
            public void onRegister(DJIError error) {
                if (error == DJISDKError.REGISTRATION_SUCCESS) {
                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            Toast.makeText(getApplicationContext(), "Register Success", Toast.LENGTH_LONG).show();
                        }
                    });
                    DJISDKManager.getInstance().startConnectionToProduct();
                } else {
                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(new Runnable()
                    {

                        @Override
                        public void run()
                        {
                            Toast.makeText(getApplicationContext(), "Register sdk fails, check network is available", Toast.LENGTH_LONG).show();
                        }
                    });
                }
                Log.e("TAG", error.toString());
            }

            //Listens to the connected product changing, including two parts, component changing or product connection changing.
            @Override
            public void onProductChange(BaseProduct oldProduct, BaseProduct newProduct) {
                product = newProduct;
                if (product != null) {
                    product.setBaseProductListener(djiBaseProductListener);
                }
                notifyStatusChange();
            }
        };

        //Check the permissions before registering the application for android system 6.0 above.
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int permissionCheck2 = ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.READ_PHONE_STATE);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || (permissionCheck == 0 && permissionCheck2 == 0)) {
            //This is used to start SDK services and initiate SDK.
            DJISDKManager.getInstance().registerApp(getApplicationContext(), djiSDKManagerCallback);
            Toast.makeText(getApplicationContext(), "registering, pls wait...", Toast.LENGTH_LONG).show();
        }
        else {
            Toast.makeText(getApplicationContext(), "Please check if the permission is granted.", Toast.LENGTH_LONG).show();
        }
    }

    private void notifyStatusChange() {
        handler.removeCallbacks(updateRunnable);
        handler.postDelayed(updateRunnable, 500);
    }

    private Runnable updateRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            Intent intent = new Intent(FLAG_CONNECTION_CHANGE);
            getApplicationContext().sendBroadcast(intent);
        }
    };
}
