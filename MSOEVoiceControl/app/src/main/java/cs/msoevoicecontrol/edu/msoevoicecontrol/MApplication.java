package cs.msoevoicecontrol.edu.msoevoicecontrol;
/**
 * This Activity contains the main methods for the application to launch the first-person viewer
 * @author Derek Riley
 */
import android.app.Application;
import android.content.Context;

import com.secneo.sdk.Helper;

public class MApplication extends Application {
    private FPVApplication fpvApplication;

    @Override
    protected void attachBaseContext(Context paramContext) {
        super.attachBaseContext(paramContext);
        Helper.install(MApplication.this);
        if (fpvApplication == null) {
            fpvApplication = new FPVApplication();
            fpvApplication.setContext(this);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        fpvApplication.onCreate();
    }
}