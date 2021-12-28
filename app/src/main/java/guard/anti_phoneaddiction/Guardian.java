/*
//加了flag以保持后台运行不退出，还没经过测试，（上一版本能用，如果出问题需要恢复上一版本，修改项参看所收藏的相关网页）
//2019.12.19增加悬浮窗权限判断：https://blog.csdn.net/qq_27885521/article/details/101510088
//2021.3.2 要添加在“最近应用列表”隐藏的功能。(搞定，并去掉了遮盖整个recent界面的功能）
*/

package guard.anti_phoneaddiction;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class Guardian extends AppCompatActivity {
public int endTime=0;
public int startTime=0;
EditText startTimeText,endTimeText;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.guardian);
        //enableDeviceAdmin();

        //判断无障碍服务是否已经打开，没打开则进入无障碍设置界面
        if (!isAccessibilitySettingsOn(this)) {
            Toast.makeText(this, "请授权打开无障碍服务", Toast.LENGTH_LONG);
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            this.startActivity(intent);
        }

        //在启动服务之前，需要先判断一下当前是否允许开启悬浮窗
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "当前无开启悬浮窗权限，请授权", Toast.LENGTH_LONG);
            startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())), 0);
        }

        //启动后台监控服务，用于监控是否按了recent键
        this.startService(new Intent(this, Anti_PhoneAddictionService.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        //加了flag以保持后台运行不退出，还没经过测试，（上一版本能用，如果出问题需要恢复上一版本，修改项参看所收藏的相关网页）
        //不断循环启动应用锁
//        int i=0;
//        while(i<=20){
//            try {
//            Intent LaunchIntent = getPackageManager().getLaunchIntentForPackage("com.lxq.applocker");
//               startActivity(LaunchIntent);
//                //Thread.sleep(10);
//                // 　按Home键
////            Intent intent = new Intent(Intent.ACTION_MAIN);
////            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
////            intent.addCategory(Intent.CATEGORY_HOME);
////            startActivity(intent);
//               i++;
//                Thread.sleep(300);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
//

        //用按钮启动应用锁
//        Button startLock =(Button)findViewById(R.id.StartLock);
//        startLock.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                Intent LaunchIntent = getPackageManager().getLaunchIntentForPackage("com.lxq.applocker");
//                startActivity(LaunchIntent);
//            }
//        });

        startTimeText=findViewById(R.id.startTime);
        endTimeText=findViewById(R.id.endTime);
    }


    public void onClick(View v) {
        //点击设置后把时间传送给后台服务
        Anti_PhoneAddictionService.startTime_user=Integer.parseInt(startTimeText.getText().toString());
        Anti_PhoneAddictionService.endTime_user=Integer.parseInt(endTimeText.getText().toString());
        Toast.makeText(this,"已设置禁止使用手机的时间为"+startTime+" 至　"+ endTime,Toast.LENGTH_LONG);
//        return true;
    }

    private void enableDeviceAdmin() {

        ComponentName deviceAdmin = new ComponentName(this, DeviceAdmin.class);
        DevicePolicyManager mDpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);

        // First of all, to access anything you must be device owner
        if (mDpm.isDeviceOwnerApp(getPackageName())) {

            // If not device admin, ask to become one
            if (!mDpm.isAdminActive(deviceAdmin) &&
                    mDpm.isDeviceOwnerApp(getPackageName())) {

                //Log.v(TAG, "Not device admin. Asking device owner to become one.");

                Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdmin);
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "You need to be a device admin to enable device admin.");

                startActivity(intent);
            }

            // Device owner and admin : enter device admin
            else if (Build.VERSION.SDK_INT >= 21) {
                mDpm.setLockTaskPackages(deviceAdmin, new String[]{
                        getPackageName(), // PUT OTHER PACKAGE NAMES HERE! /
                });
                startLockTask();
            }
        }
    }

    private boolean isAccessibilitySettingsOn(Context mContext) {
        int accessibilityEnabled = 0;
        final String service = getPackageName() + "/" + Anti_PhoneAddictionService.class.getCanonicalName();
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    mContext.getApplicationContext().getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED);
//            Log.v(TAG, "accessibilityEnabled = " + accessibilityEnabled);
        } catch (Settings.SettingNotFoundException e) {
//            Log.e(TAG, "Error finding setting, default accessibility to not found: "
//                    + e.getMessage());
        }
        TextUtils.SimpleStringSplitter mStringColonSplitter = new TextUtils.SimpleStringSplitter(':');

        if (accessibilityEnabled == 1) {
//            Log.v(TAG, "***ACCESSIBILITY IS ENABLED*** -----------------");
            String settingValue = Settings.Secure.getString(
                    mContext.getApplicationContext().getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) {
                mStringColonSplitter.setString(settingValue);
                while (mStringColonSplitter.hasNext()) {
                    String accessibilityService = mStringColonSplitter.next();

//                    Log.v(TAG, "-------------- > accessibilityService :: " + accessibilityService + " " + service);
                    if (accessibilityService.equalsIgnoreCase(service)) {
//                        Log.v(TAG, "We've found the correct setting - accessibility is switched on!");
                        return true;
                    }
                }
            }
        } else {
//            Log.v(TAG, "***ACCESSIBILITY IS DISABLED***");
        }

        return false;
    }

}
