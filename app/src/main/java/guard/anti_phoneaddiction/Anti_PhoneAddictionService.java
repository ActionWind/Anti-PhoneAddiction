/* ***无法自启动的问题和老是被关掉的问题：记得要在手机上设置4个地方：1、自启动白名单；2、内存清理白名单；3、电池优化白名单；4、打开无障碍服务
需要增加的功能：
1、根据使用时长封禁

Bugs：
1、toast不显示
2、

//要增加自启动功能（完成），
// 最好还有防卸载功能
//防杀后台（完成，用前台服务实现）
//2019.11.28日更新：1、在Manifest中将此服务设置为无障碍服务（Accessibility）；
// 2、监听最近使用按钮事件后启动自己的ui界面，以加快速度。以前是启动防沉迷应用锁。
//2019.12.19增加悬浮窗https://blog.csdn.net/qq_27885521/article/details/101510088
//遇到不能安装的问题：https://blog.csdn.net/love_yan_1314/article/details/78207454 ，做完后让gradle重新sync
//2021.3.2 添加在“最近应用列表”隐藏的功能。(搞定，并去掉了遮盖整个recent界面的功能）
//2021.3.22 添加了recent界面的全屏悬浮窗，挡住recent界面。
//2021.3.25 让全屏悬浮窗一直存在，在recent界面屏蔽点击等操作，在其他界面可以正常点击下层界面。自动清理内存的功能没写成。
//2021.5.15 在manifest中注册成为launcher，使其不会被一键清除干掉。
2021.5.16 今天要完成：1、悬浮按钮点击三次弹出应用锁。（效果估计不好，要改成当进入recent界面之后，再退出时就启动应用锁）。
//                        2、输入法界面悬浮按钮点击可穿透。（完成，改成可被输入法覆盖）
//                        3、每五秒检查一次应用锁是否在运行，没有就启动它。
//                        （这个现在好像无法检测正在运行的app，不知道launcher和无障碍可不可以）
//                        （解决办法，改为进入recent界面之后，将召唤开关打开，每三秒召唤一次应用锁，当退出recent界面的时候将召唤开关关闭。完成！）实现这个之后，防护应该已经完美了。
2021.5.19 进入recent界面之后，按返回键召唤应用锁。（需要添加一个悬浮窗，因为其他两个需要设置not_focusable，设置了这个就不能响应返回键。）

UI设计:
1、界面探查器：点击开启界面探查器之后会出现一个悬浮窗，显示当前activity名，引导用户进入索要屏蔽的界面之后，点击悬浮窗上面的“屏蔽当前界面”按钮之后出现屏蔽设置界面。
*/
package guard.anti_phoneaddiction;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.icu.text.SimpleDateFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

//import android.content.BroadcastReceiver;
//import android.content.IntentFilter;
//import android.text.TextUtils;

public class Anti_PhoneAddictionService extends AccessibilityService implements Button.OnClickListener {

    public static int startTime_user=0;
    public static int endTime_user=0;
    // 悬浮窗所需要的变量
    WindowManager windowManager;
    WindowManager buttonWindowManager;
    Button floatingButton;
    View recentScreen;

    TextView sheild;
    WindowManager.LayoutParams layoutParams;
    WindowManager.LayoutParams buttonLayoutParams;
    Display display;
    int width;
    int height;
    int fullFloatingWindowFlag = 0;
    //
    Intent home;
    int callForLocker = 0;//决定要不要调用应用锁的标志位
    Intent applocker;

    Calendar cal;
    final int start = 22 * 60 + 10;// 起始时间 22:10的分钟数
    final int end = 6 * 60 + 30;// 结束时间 6:30的分钟数
    int hour, minute, now;

    HashSet whiteList = new HashSet();//白名单用于全局屏蔽时设置例外app
    HashSet blackList = new HashSet();//黑名单用于关乎设置的屏蔽
    HashSet nightBlackList = new HashSet();//夜晚黑名单
    HashSet clickTextBlackList = new HashSet();//点击黑名单

    AlarmManager alarmManager;
    Calendar calendar = Calendar.getInstance();

    @Override
    public void onCreate() {
        super.onCreate();
//         Log.e("onCreate1",",start--------------------");
//        showFloatingWindow();//这是小悬浮窗
//        Log.e("onCreate2",",start--------------------");
        fullScreenFloatingWindow();//对Recent界面的全屏悬浮窗进行各种设置。
//        Log.e("onCreate3",",start--------------------");

        sheildSetting();//设置好屏蔽的悬浮窗，以便下面调用

        final InputMethodManager inputMethod = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
//        recentScreen.setOnClickListener(this);
        applocker = getPackageManager().getLaunchIntentForPackage("com.lxq.applocker");

        //监听recent键。启动自己的ui挡住recent界面（以前是启动应用锁），这样会快很多
        BroadcastReceiver recentKeyReceiver = new BroadcastReceiver() {
            // class RecentKeyEventReceiver extends BroadcastReceiver {
            String SYSTEM_REASON = "reason";
            String SYSTEM_HOME_KEY = "homekey";
            String SYSTEM_RECENT_APPS = "recentapps";
            Intent AppUI = new Intent(Anti_PhoneAddictionService.this, Guardian.class);

            @Override
            public void onReceive(Context context, Intent intent) {

                if (intent.getAction().equals(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) {
                    //Log.d(TAG, "ACTION_CLOSE_SYSTEM_DIALOGS");
                    String reason = intent.getStringExtra(SYSTEM_REASON);
                    //Log.d(TAG, "reason - " + reason);
                    if (TextUtils.equals(reason, SYSTEM_RECENT_APPS)) {//进入Recent界面时
//                        log("recent");
                        callForLocker = 1;//当进入recent界面的时候就打开调用开关。
                        new CallLockerLoop().start();//开始每三秒循环一次调用应用锁
                        //修改全屏悬浮窗设置，让屏幕无法响应点击事件
                        if (fullFloatingWindowFlag == 0) {
                            layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
                            windowManager.updateViewLayout(recentScreen, layoutParams);
                            fullFloatingWindowFlag = 1;
//                            //        Log.e(""resent按钮if里面","=================");
                        }
//                        //        Log.e(""resent按钮", "=================");

//                        startActivity(AppUI);//显示自己的ui
                        //  Recent key is pressed，启动应用锁
                        //startActivity(applocker);
                    } else if (TextUtils.equals(reason, SYSTEM_HOME_KEY)) {//不在recent界面时。（返回到home界面时 if (TextUtils.equals(reason, SYSTEM_HOME_KEY))
                        // Home key. 让界面恢复成可操作状态
                        if (callForLocker == 1) {//从recent退出时，调用应用锁
                            startActivity(applocker);
                            callForLocker = 0;
                            Log.e("调用应用锁", "=================");
                        }
                        if (fullFloatingWindowFlag == 1) {
                            layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
                            windowManager.updateViewLayout(recentScreen, layoutParams);
//                            windowManager.removeViewImmediate(recentScreen);
                            fullFloatingWindowFlag = 0;
                        }
                        if (sheildFlag == 1) {//关掉微信的屏蔽
//                            windowManager.removeViewImmediate(sheild);
                            windowManager.updateViewLayout(sheild, sheildParams);
                            sheildFlag = 0;
                        }
                    }
                }
//                else if(intent.getAction().equals(Intent.ACTION_INPUT_METHOD_CHANGED)){
//                    startActivity(AppUI);//显示自己的ui
//                    //        Log.e(""Intent.ACTION_INPUT_METHOD_CHANGED",",start--------------------");
//                    if (inputMethod.isActive()){//弹出输入法的时候将悬浮按钮设置为可穿透
//                        //        Log.e(""Intent.ACTION_INPUT_METHOD_CHANGED",",--------------------"+inputMethod.isActive());
//
//                        buttonLayoutParams.flags =WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE| WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
//                        buttonWindowManager.updateViewLayout(floatingButton,buttonLayoutParams);
//                    }else{
//                        //        Log.e(""Intent.ACTION_INPUT_METHOD_CHANGED",",--------------------"+inputMethod.isActive());
//
//                        buttonLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
//                        buttonWindowManager.updateViewLayout(floatingButton,buttonLayoutParams);
//                    }
//                }
            }
        };

        // 2. 设置接收广播的类型
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
//        intentFilter.addAction(Intent.ACTION_INPUT_METHOD_CHANGED);//不起作用
//        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_INPUT_METHOD_CHANGED);//不起作用

        // 3. Receiver动态注册：调用Context的registerReceiver（）方法
        registerReceiver(recentKeyReceiver, intentFilter);

        //启用内存监控和清理操作：
//        new memoryWatcher().start();

//        //设置无障碍服务的权限：
//
//        AccessibilityServiceInfo config = new AccessibilityServiceInfo();
//        //配置监听的事件类型为界面变化|点击事件
//        config.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_VIEW_CLICKED;//
//        config.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
//        config.flags=AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS |AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
//        if (Build.VERSION.SDK_INT >= 16) {
//            config.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
//        }
//        setServiceInfo(config);

        whiteList.add("com.microsoft.launcher");//桌面
        whiteList.add("com.android.dialer");//拨号程序
        whiteList.add("com.zui.mms");//短信
        whiteList.add("com.zui.launcher");//桌面
//        whiteList.add("cn.wps.moffice_eng");//wps
        whiteList.add("com.android.systemui");//系统界面
        whiteList.add("io.moreless.tide");//潮汐
//        whiteList.add("com.UCMobile");//浏览器
//        whiteList.add("com.android.chrome");//浏览器
//        whiteList.add("com.fotiaoqiangvpn.android");//ｖｐｎ
        whiteList.add("com.zui.deskclock");//时钟
        whiteList.add("com.baidu.input");//输入法
        whiteList.add("com.zui.contacts");//联系人
        whiteList.add("com.zui.notes");//笔记
        whiteList.add("com.seblong.meditation");//大象冥想
        whiteList.add("com.zui.calculator");// 计算器
        whiteList.add("com.zui.calendar");//日历
        whiteList.add("com.asus.calendar");//日历
        whiteList.add("com.zui.safecenter");//安全中心
        whiteList.add("com.seblong.idream");//蜗牛睡眠
        whiteList.add("com.lxq.applocker");//应用锁
        whiteList.add("lock.guardian");//自身

        blackList.add("com.android.settings.feature.multiplespace.AppListActivity");

        nightBlackList.add("com.tencent.mm.plugin.webview.ui.tools.WebViewUI");
        nightBlackList.add("com.tencent.mm.plugin.webview.ui.tools.WebviewMpUI");
        nightBlackList.add("com.tencent.mm.plugin.fts.ui.FTSMainUI");
        nightBlackList.add("com.baidu.input.ImeSearchActivity");
        nightBlackList.add("com.tencent.mm.plugin.appbrand.launching.AppBrandLaunchProxyUI");

    }

    Calendar wzry_afterNoon = Calendar.getInstance();
    String tag;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        Log.e("启动无障碍服务", "=================");
        AccessibilityServiceInfo config = new AccessibilityServiceInfo();
        //配置监听的事件类型为界面变化|点击事件
        config.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_VIEW_CLICKED;//AccessibilityEvent.TYPE_WINDOWS_CHANGED
        config.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        config.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;//AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
//        if (Build.VERSION.SDK_INT >= 16) {
//            config.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
//        }
        setServiceInfo(config);
        if (Build.VERSION.SDK_INT >= 24) {
            //用AlarmManager实现定期检查界面。
            alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

            calendar.set(Calendar.HOUR_OF_DAY, 21);
            calendar.set(Calendar.MINUTE, 30);
            calendar.set(Calendar.SECOND, 00);//这里代表 21.30.00
            log("看看日历时间" + calendar.getTimeInMillis());
//        PendingIntent checkScreenIntent=new PendingIntent("Check Screen");
//        // 到了 21点14分00秒 后通过PendingIntent pi对象发送广播
//        tag="夜晚屏蔽";
            class AlarmListener implements AlarmManager.OnAlarmListener {
                @Override
                public void onAlarm() {
                    log("onalarm中的packageName：" + packageName);
                    if (nightBlackList.contains(activityName)) {//Calendar.getInstance()>=calendar&&
//            log("alarm listener");
                        guardsSheild();
                        calendar.setTimeInMillis(calendar.getTimeInMillis() + 24 * 3600 * 1000);
                        if (Build.VERSION.SDK_INT >= 24)
                            alarmManager.setExact(AlarmManager.RTC, calendar.getTimeInMillis(), tag = "晚上检查界面一决定是否屏蔽", this, null);
                        log("calendar 已发出第二天的定时");
                    } else if (packageName.equals("com.tencent.tmgp.sgame")) {//  Calendar.getInstance().equals(wzry_afterNoon)&&
                        guardsSheild();
                        wzry_afterNoon.setTimeInMillis(wzry_afterNoon.getTimeInMillis() + 24 * 3600 * 1000);
                        if (Build.VERSION.SDK_INT >= 24)
                            alarmManager.setExact(AlarmManager.RTC, wzry_afterNoon.getTimeInMillis(), tag = "下午屏蔽", this, null);
                        log("wzry_afterNoon已发出第二天的定时");
                    }
                }
            }

            AlarmManager.OnAlarmListener alarmListener = new AlarmListener();
            alarmManager.setExact(AlarmManager.RTC, calendar.getTimeInMillis(), "夜晚屏蔽", alarmListener, null);

            wzry_afterNoon.set(Calendar.HOUR_OF_DAY, 14);
            wzry_afterNoon.set(Calendar.MINUTE, 45);
            wzry_afterNoon.set(Calendar.SECOND, 00);//这里代表 14.45.00
//        tag="下午屏蔽";
            alarmManager.setExact(AlarmManager.RTC, wzry_afterNoon.getTimeInMillis(), "下午屏蔽", alarmListener, null);
        }
        //        log("看看日历时间2" + (21*60+30)*60*1000);
        //这样用handler设置定时任务会引起anr，以后再研究。忽然想到原因可能是递归的时候设置了同一时间，结果无限重复类似死循环，应推迟一两分钟再设置。
//        Handler handler = new Handler();
//        Runnable checkScreen=new CheckScreen(activityName);
//        handler.postAtTime(checkScreen, (15 * 3600+20*60) * 1000); //递归调用，使其每天定时检查一次
//        Log.e("启动无障碍服务　　ｅｎｄ", "=================");
    }

    String activityName = "";
    ComponentName componentName;
    CharSequence packageName = "";
    AccessibilityNodeInfo source;
    AccessibilityEvent event;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent currentEvent) {
        event = currentEvent;
//        AccessibilityEvent event = event0;
        source = event.getSource();//同一个event竟然getSource()会获取到不同的结果！要记住这个方法，以后遇到类似的问题先把获取的结果在判断之前保存！
//        Log.e("触发了无障碍服务事件１", "=================" + event.getSource());
        if (source != null) {//以下两项在根目录下的屏蔽相关代码，点击和画面变化的时候都会进行判断，也会比较消耗系统资源。
            // 所以不需要两种情况都触发判断的app尽量写在相应的if下，以尽量节省系统资源。

            packageName = source.getPackageName();//packge name被获取一次之后就会变空，所以一定要在这里一开始就先保存起来
//            Log.e("当前app包名", "=================" + packageName);

            //***晚上屏蔽单个界面
//            if (nightTimeBetween(21 * 60 + 30, 7 * 60)) {
//                //获取当前窗口activity名
//                getActivityName(event);
//                if (nightBlackList.contains(activityName)) {
//                    guardsSheild();
//                }
//            }


            //            if (packageName != null && packageName.equals("com.android.packageinstaller")) {//屏蔽卸载界面
//                if (sheildFlag == 0) {
//                    guardsSheild();
//                }
//            }

            if (event.getEventType() == event.TYPE_WINDOW_STATE_CHANGED) {
                getActivityName(event);
//                log("activityname: " + activityName);

                //***晚上屏蔽单个界面(int) calendar.getTimeInMillis()
//                activitySheild(activityName, 21 * 60 + 30, 7 * 60);

                appSheild("com.tencent.tmgp.sgame", 14 * 60 + 45, 15 * 60);

                if (timeBetween(startTime_user, endTime_user)) {
                    //全局屏蔽，同时设置了白名单
                    if (!whiteList.contains(packageName)) {
                        guardsSheild();//晚上屏蔽手机
                    }
                }
//                if (nightTimeBetween(21 * 60 + 30, 7 * 60)) {
//                    //获取当前窗口activity名
//                    getActivityName(event);
////                log("blackList"+blackList);
//
//                    if (nightBlackList.contains(activityName)) {
//                        if (sheildFlag == 0) {
//                            guardsSheild();
//                        }
//                    }
//                }
                ////黑名单用于屏蔽单个的界面
                if (blackList.contains(activityName)) {
//                    Log.e("if当前窗口activity", "=================" + activityName);
                    if (!timeBetween(13 * 60 + 5, 13 * 60 + 25)) {
                        guardsSheild();
                    }
                } else if (timeBetween(21 * 60 + 30, 22 * 60)) {
                    if (packageName.equals("cn.wps.moffice_eng")) {
                        guardsSheild();
                    }
                }

                //好了，根据关键词屏蔽界面的功能经测试已经成功实现！哈哈
                if (timeBetween(21 * 60 + 30, 22 * 60)) {
                    AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
                    if (nodeInfo != null) {
                        List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByText("朋友圈");
                        List<AccessibilityNodeInfo> list1 = nodeInfo.findAccessibilityNodeInfosByText("游戏");
                        List<AccessibilityNodeInfo> list2 = nodeInfo.findAccessibilityNodeInfosByText("看一看");
//                    List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByViewId("xxxxx:id/rl_action");
//                        log("查找关键字"+list+"----个数: "+list.size());
//                        Rect rect = new Rect();
                        if (packageName.equals("com.tencent.mm") && list.size() > 0 && list1.size() > 0 && list2.size() > 0) {
                            guardsSheild();
//                            list2.get(0).getBoundsInScreen(rect);
//                    list.get(list.size()-1).getBoundsInScreen(rect);
//                            log("ScreenList-2" + list2);
//                            Log.e("================", "要点击的像素点在手机屏幕位置::" + rect.centerX() + " " + rect.centerY());
                        }
//                        else if (packageName.equals("com.baidu.input")) {
//
//                        }
                    }
                }
//                AccessibilityNodeInfo inputEventSource = source.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
//                log("focus window: "+source.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY));
//                if (inputEventSource != null) {
////                    Log.e("event.TYPE_WINDOW_State_CHANGED", "=================" + inputEventSource);
//                    if (inputEventSource != null && inputEventSource.getText() != null && inputEventSource.getText().equals("百度一下")) {
//                        guardsSheild();
//                    }
//                }
//                if (packageName != null && (packageName.equals("com.tencent.mobileqq") || packageName.equals("com.tencent.mm"))) {
////                    Log.e("当前窗口信息2", "=================" + source);
//                    cal = Calendar.getInstance();// 当前日期
//                    hour = cal.get(Calendar.HOUR_OF_DAY);// 获取小时
//                    minute = cal.get(Calendar.MINUTE);// 获取分钟
//                    now = hour * 60 + minute;// 从0:00分开是到目前为止的分钟数
//
//                    //晚上到第二天早上，所以用或运算
//                    if (sheildFlag == 0 && (now >= start || now <= end))
//                        guardsSheild();
//                }
//                ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
//                ComponentName cn = am.getRunningTasks(1).get(0).topActivity;
//                Log.e("窗口   ", "=======================pkg:" + cn.getPackageName());
//                Log.e("窗口", "=======================cls:" + cn.getClassName());
            }
//            else if (event.getEventType() == event.TYPE_WINDOWS_CHANGED) {
//                Log.e("event.TYPE_WINDOWS_CHANGED", "=================");
//
//
////                    Log.e("当前窗口类名是", "=================" + source.findFocus());
//            }
            else if (event.getEventType() == event.TYPE_VIEW_CLICKED) {
//                Log.e("触发了无障碍服务 点击事件２", "=================" + event.getSource());

                //对隐藏app界面的屏蔽
                if (source.getViewIdResourceName() != null && source.getViewIdResourceName().equals("com.microsoft.launcher:id/all_apps_menu_dot")) {
//                    Log.e("触发了无障碍服务 点击元素名: ", "=================" + event.getSource().getViewIdResourceName());
                    if (!timeBetween(13 * 60 + 5, 13 * 60 + 25)) {
                        guardsSheild();
                    }
                }
//                if (source.getText()!=null) {
//                    Log.e("触发了无障碍服务 点击事件名", "=================" + event.getSource().getText());
////                    if (source.getText().equals("WeChat") || source.getText().equals("QQ")) {
////
////                        cal = Calendar.getInstance();// 当前日期
////                        hour = cal.get(Calendar.HOUR_OF_DAY);// 获取小时
////                        minute = cal.get(Calendar.MINUTE);// 获取分钟
////                        now = hour * 60 + minute;// 从0:00分开是到目前为止的分钟数
////
////                        //晚上到第二天早上，所以用或运算
////                        if (sheildFlag == 0 && (now >= start || now <= end)) {
////                            guardsSheild();
////
////                        }
////                    } else if (sheildFlag == 1) {
////                        windowManager.removeViewImmediate(sheild);
////                        sheildFlag = 0;
////                    }
//                }
//                callSheildWhenClick("Bluetooth");
            }
        }
//        if (event.getPackageName().equals("com.tencent.mobileqq")) {
//            showFloatingWindow();
//        }
    }

    //app中的某个界面屏蔽　
    void activitySheild(String shieldActivity, String currentActivityName, int startTime, int endTime) {
        if (timeBetween(startTime, endTime)) {
            if (currentActivityName.equals(shieldActivity)) {
                guardsSheild();
            }
        }
    }

    //app中的某个界面屏蔽（黑名单模式）　
    void activitySheild(HashSet blackList, String currentActivityName, int startTime, int endTime) {
        if (timeBetween(startTime, endTime)) {
            if (blackList.contains(currentActivityName)) {
                guardsSheild();
            }
        }
    }

    //app屏蔽
    void appSheild(String pName, int startTime, int endTime) {
        if (timeBetween(startTime, endTime)) {
            if (packageName.equals(pName)) {
                guardsSheild();
            }
        }
    }

    //点击指定按钮时屏蔽（黑名单模式）
    void callSheildWhenClick() {
        if (event.getText().size() > 0) {
//            Log.e("触发了无障碍服务 点击元素名: ", "=================" + event.getText().get(0));
            CharSequence itemText = event.getText().get(0);

//        Log.e("触发了无障碍服务 点击元素名: ", "=================" + buttonText);
//        if (text.equals(buttonText)) {
            if (clickTextBlackList.contains(itemText)) {

//            Log.e("触发了无障碍服务 点击元素名2: ", "=================" + event.getText());
                if (!timeBetween(13 * 60 + 5, 13 * 60 + 25)) {
//                Log.e("触发了无障碍服务 点击元素名3: ", "=================" + event.getText());
                    guardsSheild();
                }
            }
        }
    }

    //点击指定按钮时屏蔽（指定按钮文本模式）
    void callSheildWhenClick(CharSequence buttonText) {
        if (event.getText().size() > 0) {
//            Log.e("触发了无障碍服务 点击元素名: ", "=================" + event.getText());
            CharSequence itemText = event.getText().get(0);

//            Log.e("指定文本: ", "=================" + buttonText);
//            Log.e("触发了无障碍服务 点击元素名: ", "=================" + itemText);
//        if (text.equals(buttonText)) {
            if (buttonText.equals(itemText)) {

//            Log.e("是否符合 ", "=================" + buttonText.equals(itemText));
                if (!timeBetween(13 * 60 + 5, 13 * 60 + 25)) {
//                    Log.e("触发了无障碍服务 点击元素名3: ", "=================" + event.getText());
                    guardsSheild();
                }
            }
        }
    }

    void getActivityName(AccessibilityEvent event) {
        componentName = new ComponentName(event.getPackageName().toString(), event.getClassName().toString());

        try {
            activityName = getPackageManager().getActivityInfo(componentName, 0).toString();
            activityName = activityName.substring(activityName.indexOf(" ") + 1, activityName.indexOf("}"));
//                    Log.e("当前窗口activity", "=================" + activityName);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
//        return activityName;
    }

    boolean timeBetween(int start, int end) {
        cal = Calendar.getInstance();// 当前日期
        hour = cal.get(Calendar.HOUR_OF_DAY);// 获取小时
        minute = cal.get(Calendar.MINUTE);// 获取分钟
        now = hour * 60 + minute;// 从0:00分开是到目前为止的分钟数
        if (start < end)
            return now >= start && now <= end;
        else if(start>end)
            return now >= start || now <= end;
        return false;
    }

    boolean nightTimeBetween(int start, int end) {
        cal = Calendar.getInstance();// 当前日期
        hour = cal.get(Calendar.HOUR_OF_DAY);// 获取小时
        minute = cal.get(Calendar.MINUTE);// 获取分钟
        now = hour * 60 + minute;// 从0:00分开是到目前为止的分钟数
        return now >= start || now <= end;
    }

    //设置晚上屏蔽的时间
    void nightSheildTime(int start, int end) {
        cal = Calendar.getInstance();// 当前日期
        hour = cal.get(Calendar.HOUR_OF_DAY);// 获取小时
        minute = cal.get(Calendar.MINUTE);// 获取分钟
        now = hour * 60 + minute;// 从0:00分开是到目前为止的分钟数

        //晚上到第二天早上，所以用或运算
        if (sheildFlag == 0 && (now >= start || now <= end))
            guardsSheild();
    }

    @Override
    public void onInterrupt() {

    }

//    @Override
//    public IBinder onBind(Intent intent) {
//        return null;
//    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

//        Notification notification = new Notification(R.drawable.logo,
//                "wf update service is running",
//                System.currentTimeMillis());
//        pintent=PendingIntent.getService(this, 0, intent, 0);
//        notification.setLatestEventInfo(this, "WF Update Service",
//                "wf update service is running！", pintent);

        //让该service前台运行，避免手机休眠时系统自动杀掉该服务
        //如果 id 为 0 ，那么状态栏的 notification 将不会显示。


        // 构建Notification的方式
        //Notification.Builder builder = new Notification.Builder
        //        (this.getApplicationContext()); //获取一个Notification构造器

        //创建一个 NotificationChannel
        NotificationManager mNotiManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        Uri mUri = Settings.System.DEFAULT_NOTIFICATION_URI;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel mChannel = new NotificationChannel("1", "LockGuardian", NotificationManager.IMPORTANCE_LOW);

            mChannel.setDescription("no description");

            mChannel.setSound(mUri, Notification.AUDIO_ATTRIBUTES_DEFAULT);

            mNotiManager.createNotificationChannel(mChannel);

            Notification notification = new Notification.Builder(Anti_PhoneAddictionService.this)
                    .setContentTitle("New Message")
                    .setContentText("You've received new messages.")
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setChannelId("1") //同上 channel id
                    .build();

            Intent nfIntent = new Intent(this, Guardian.class);

//        builder.setContentIntent(PendingIntent.
//                getActivity(this, 0, nfIntent, 0)) // 设置PendingIntent
//                .setLargeIcon(BitmapFactory.decodeResource(this.getResources(),
//                        R.drawable.ic_launcher)) // 设置下拉列表中的图标(大图标)
//                .setContentTitle("下拉列表中的Title") // 设置下拉列表里的标题
//                .setSmallIcon(R.mipmap.ic_launcher) // 设置状态栏内的小图标
//                .setContentText("要显示的内容") // 设置上下文内容
//                .setWhen(System.currentTimeMillis()); // 设置该通知发生的时间

            //  Notification notification = builder.build(); // 获取构建好的Notification
            notification.defaults = Notification.DEFAULT_SOUND; //设置为默认的声音

            startForeground(110, notification);
        }
        return START_STICKY;
    }

    private void showFloatingWindow() {
        if (Build.VERSION.SDK_INT >= 23 && Settings.canDrawOverlays(this)) {
            // 获取WindowManager服务
            buttonWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

            // 新建悬浮窗控件
            floatingButton = new Button(getApplicationContext());
            //button.setText("Floating Window");
            floatingButton.setBackgroundColor(Color.GREEN);
            floatingButton.setAlpha(0.1f);

            // 设置LayoutParam
            buttonLayoutParams = new WindowManager.LayoutParams();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                buttonLayoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
                //layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION;
                buttonLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;//最后一个参数是让悬浮按钮能被输入法遮挡

            } else {
                buttonLayoutParams.type = WindowManager.LayoutParams.TYPE_PHONE;
            }
            buttonLayoutParams.format = PixelFormat.RGBA_8888;
            buttonLayoutParams.width = 140;
            buttonLayoutParams.height = 140;
            buttonLayoutParams.x = 0;
            buttonLayoutParams.y = 788;

            // 将悬浮窗控件添加到WindowManager
            buttonWindowManager.addView(floatingButton, buttonLayoutParams);
        }
    }

    private void fullScreenFloatingWindow() {
        if (Build.VERSION.SDK_INT >= 23 && Settings.canDrawOverlays(this)) {
            // 获取WindowManager服务
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

            // 新建悬浮窗控件
            recentScreen = new Button(getApplicationContext());
            //button.setText("Floating Window");
//            recentScreen.setBackgroundColor(Color.rgb(204, 232, 207));
            recentScreen.setBackgroundColor(Color.GREEN);
            recentScreen.setAlpha(0.05f);

            // 设置LayoutParam
            layoutParams = new WindowManager.LayoutParams();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
                //layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION;
//                layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
//                layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;

                //设置在正常界面能够点击、返回、调出输入法等正常操作
                layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;

//                layoutParams.rotationAnimation=WindowManager.LayoutParams.ROTATION_ANIMATION_ROTATE;
            } else {
                layoutParams.type = WindowManager.LayoutParams.TYPE_PHONE;
            }
            display = windowManager.getDefaultDisplay();
            log("屏幕对象获取成功没有" + display);
            width = display.getWidth();
            height = display.getHeight();
            layoutParams.format = PixelFormat.RGBA_8888;
            layoutParams.width = width;
            layoutParams.height = height;

            //设置位置
//            layoutParams.x = 0;
//            layoutParams.y = 788;
            windowManager.addView(recentScreen, layoutParams);
        }
    }

    WindowManager.LayoutParams sheildParams;
    int sheildFlag = 0;
    WindowManager.LayoutParams fullScreenParams = new WindowManager.LayoutParams();

    void sheildSetting() {
        if (Build.VERSION.SDK_INT >= 23 && Settings.canDrawOverlays(this)) {//这个判断也许可以删除，有时间再试

            // 新建悬浮窗控件
            sheild = new TextView(getApplicationContext());
            sheild.setOnClickListener(this);
            sheild.setText("放下手中的虚幻，\n\n到现实中开创一片天地.....\n\n\n\n\n\n点击返回桌面");
            sheild.setTextColor(Color.WHITE);
            sheild.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            sheild.setGravity(Gravity.CENTER_VERTICAL);
//            sheild.setBackgroundColor(Color.rgb(204, 232, 207));
            sheild.setBackgroundColor(Color.rgb(38, 54, 0));
//            sheild.setBackgroundColor(Color.GREEN);
//            recentScreen.setAlpha(0.05f);

            // 设置LayoutParam
            sheildParams = new WindowManager.LayoutParams();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                sheildParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
                //layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION;
//                layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
//                layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;

//                //设置在正常界面能够点击、返回、调出输入法等正常操作
                sheildParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;

//                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                //设置屏蔽界面（可点击的界面，其实就是自身拦截掉对下层的点击事件）。
//                sheildParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
//                layoutParams.rotationAnimation=WindowManager.LayoutParams.ROTATION_ANIMATION_ROTATE;
            } else {
                sheildParams.type = WindowManager.LayoutParams.TYPE_PHONE;
            }
            display = windowManager.getDefaultDisplay();
            width = 1;
            height = 1;
            sheildParams.format = PixelFormat.RGBA_8888;
            sheildParams.width = width;
            sheildParams.height = height;

            //设置位置
//            layoutParams.x = 0;
//            layoutParams.y = 788;
            windowManager.addView(sheild, sheildParams);

            //设置好全屏参数备用：
            fullScreenParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

        }
    }

    private void guardsSheild() {
        if (sheildFlag == 0) {
            log("屏幕对象" + display);
            fullScreenParams.width = display.getWidth();
            fullScreenParams.height = display.getHeight();
            windowManager.updateViewLayout(sheild, fullScreenParams);
            log("启动屏蔽");
            sheildFlag = 1;
        }
    }

    void log(String s) {
        Log.e(s, "====================" + s);
    }

    //判断当前界面显示的是哪个Activity
    String getTopActivity(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(context.ACTIVITY_SERVICE);
        ComponentName cn = am.getRunningTasks(1).get(0).topActivity;
        Log.d("Chunna.zheng", "pkg:" + cn.getPackageName());//包名
        Log.d("Chunna.zheng", "cls:" + cn.getClassName());//包名加类名
        return cn.getClassName();
    }

    @Override
    public void onClick(View view) {
//        if(view==(View)recentScreen){
//        log("点击操作");
        if (sheildFlag == 1) {
            log("返回桌面");
            startActivity(home);//点击的时候返回Home界面
            windowManager.updateViewLayout(sheild, sheildParams);
//            windowManager.removeViewImmediate(sheild);
            sheildFlag = 0;
        }
//        if (fullFloatingWindowFlag == 1) {
////            windowManager.removeViewImmediate(recentScreen);
//            fullFloatingWindowFlag = 0;
//        }
//        }

    }

    ActivityManager activityManager;
    List<ActivityManager.RunningAppProcessInfo> infoList;
    float availableMemory;
    int count;

    public void killBackgroundProcesses() {
        activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        infoList = activityManager.getRunningAppProcesses();
        Log.d("app列表 ", "==================" + activityManager.getRunningAppProcesses());
//        List<ActivityManager.RunningServiceInfo> serviceInfos = activityManager.getRunningServices(100);

        availableMemory = getAvailMemory(this);
//        Log.d(TAG, "-----------before memory info : " + beforeMem);
        count = 0;
        if (infoList != null && availableMemory <= 0.3) {
            Log.d("内存清理开始 ", "==================");
            for (int i = 0; i < infoList.size(); ++i) {
                Log.d("读取每个进程进行判断 ", "==================" + infoList.get(i));
                ActivityManager.RunningAppProcessInfo appProcessInfo = infoList.get(i);
//                Log.d(TAG, "process name : " + appProcessInfo.processName);
//                //importance 该进程的重要程度  分为几个级别，数值越低就越重要。
//                Log.d(TAG, "importance : " + appProcessInfo.importance);

                // 一般数值大于RunningAppProcessInfo.IMPORTANCE_SERVICE的进程都长时间没用或者空进程了
                // 一般数值大于RunningAppProcessInfo.IMPORTANCE_VISIBLE的进程都是非可见进程，也就是在后台运行着
                if (appProcessInfo.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    String[] pkgList = appProcessInfo.pkgList;
                    for (int j = 0; j < pkgList.length; ++j) {//pkgList 得到该进程下运行的包名
//                        Log.d(TAG, "It will be killed, package name : " + pkgList[j]);
                        Log.d("正在清理 ", "==================" + pkgList[j]);
                        activityManager.killBackgroundProcesses(pkgList[j]);
                        count++;
                    }
                }
            }
        }

//        try {
////            this.getTopActivity(this).getDefault().killBackgroundProcesses(packageName,
////                    UserHandle.myUserId());
//        } catch (RemoteException e) {
//        }
    }

    float getAvailMemory(Context context) {
        // 获取android当前可用内存大小
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        //mi.availMem; 当前系统的可用内存
        //return Formatter.formatFileSize(context, mi.availMem);// 将获取的内存大小规格化
//        Log.d(TAG, "可用内存---->>>" + mi.availMem / (1024 * 1024));
        return memoryInfo.availMem * 1.0f / memoryInfo.totalMem * 1.0f;
    }

//    @Override
//    public void onAlarm() {//要找个方法区分时间，
////        log("onAlarm");
////        log("onalarm中的activityName："+activityName);
//        log("onalarm中的packageName："+packageName);
//        if (nightBlackList.contains(activityName)) {//Calendar.getInstance()>=calendar&&
////            log("alarm listener");
//            guardsSheild();
//            calendar.setTimeInMillis(calendar.getTimeInMillis() + 24 * 3600 * 1000);
//            alarmManager.setExact(AlarmManager.RTC, calendar.getTimeInMillis(), tag="晚上检查界面一决定是否屏蔽", this, null);
//            log("calendar 已发出第二天的定时");
//        }
//        else if(packageName.equals("com.tencent.tmgp.sgame")){//  Calendar.getInstance().equals(wzry_afterNoon)&&
//            guardsSheild();
//            wzry_afterNoon.setTimeInMillis(wzry_afterNoon.getTimeInMillis()+24 * 3600 * 1000);
//            alarmManager.setExact(AlarmManager.RTC, wzry_afterNoon.getTimeInMillis() ,tag="下午屏蔽",this,null);
//            log("wzry_afterNoon已发出第二天的定时");
//        }
//        //用这个递归设置下一次闹钟的时间。
//
//
//    }

    class memoryWatcher extends Thread {//android8.1已经无法使用相关内存清理的接口，所以没写成功

        @Override
        public void run() {
//            //        Log.e(""开始","===================="+availableMemory);
            while (true) {//5秒钟检查一次内存情况，可用内存小于30%就进行清理操作
                killBackgroundProcesses();
                //        Log.e(""内存检查","===================="+availableMemory);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {//横屏的时候悬浮窗的高宽跟着修改
//        layoutParams.screenOrientation=newConfig.orientation;
        layoutParams.width = display.getWidth();
        layoutParams.height = display.getHeight();
        windowManager.updateViewLayout(recentScreen, layoutParams);
        super.onConfigurationChanged(newConfig);
    }

    //
    class CallLockerLoop extends Thread {//三秒调用一次应用锁

        @Override
        public void run() {
            super.run();
            while (callForLocker == 1) {
                try {
                    sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                startActivity(applocker);
            }
        }
    }

    Long startTime, endTime;
    String format = "HH:mm:ss";
//    void setTimeRange(String benginTime, String eTime){
//
////Date nowTime = new SimpleDateFormat(format).parse("09:27:00");
//
//
//        try {
//
//            startTime = new SimpleDateFormat(format).parse("22:10:00");
//            endTime = new SimpleDateFormat(format).parse("06:30:00");
//        } catch (ParseException e) {
//            e.printStackTrace();
//        }
//    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    void setDefaultTimeRange() {
        String format = "HH:mm:ss";
//Date nowTime = new SimpleDateFormat(format).parse("09:27:00");
        Date now = new Date();
        DateFormat dateFormat = DateFormat.getTimeInstance();//获取时分秒
//得到当前时间的时分秒
        String nowStr = dateFormat.format(now);

        try {

            Date startTime = new SimpleDateFormat(format).parse("22:10:00");
            Date endTime = new SimpleDateFormat(format).parse("06:30:00");
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    //使用Handler递归调用，定时检查界面
    class CheckScreen implements Runnable {
        Handler handler = new Handler();
        Runnable checkScreen;
        String currentUI;

        public CheckScreen(String name) {
            currentUI = name;
        }

        @Override
        public void run() {
            //在这里写ｉｆ判断当前的界面（字符窜）是否在黑名单中，待会儿写。
            if (nightBlackList.contains(currentUI)) {
                log("handler判断界面");
                guardsSheild();
            }
            //
            Runnable checkScreen = new CheckScreen(currentUI);
            handler.postAtTime(checkScreen, (21 * 3600 + 30 * 60) * 1000); //递归调用，使其每天定时检查一次
        }
    }

    //每隔1秒发出一个service还在运行的广播信号
    class IAmAlive extends Thread{
        @Override
        public  void run(){
            while(true) {
                Intent living = new Intent("I am alive.");
                sendBroadcast(living);
                try {
                    sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}

