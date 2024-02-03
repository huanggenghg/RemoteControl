package com.lumostech.remotecontrol;

import android.app.Application;
import android.content.Context;
import android.provider.Settings;

public class MyApp extends Application {
    public static final String TAG="MyApp";
    private Context context;

    @Override
    public void onCreate() {
        super.onCreate();
        context=this;
        try {
            setMyServiceEnable();
        }catch (Exception ignored){

        }

    }

    /**
     * 需要授予权限 android.permission.WRITE_SECURE_SETTINGS
     * adb shell pm grant 包名 android.permission.WRITE_SECURE_SETTINGS
     */
    private void setMyServiceEnable() {
        String name = getPackageName()+"/"+ MyService.class.getName();

        String string = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        StringBuffer stringBuffer=new StringBuffer(string);
        if (!string.contains(name)){
            String s = stringBuffer.append(":").append(name).toString();
            Settings.Secure.putString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,s);
        }
    }
}
