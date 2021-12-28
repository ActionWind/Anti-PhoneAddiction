package guard.anti_phoneaddiction;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

public class DeviceAdmin extends DeviceAdminReceiver {
        @Override
        public void onEnabled(Context context, Intent intent) {
            //设备管理可用
        }

        @Override
        public void onDisabled(Context context, Intent intent) {
            //设备管理不可用
        }

        @Override
        public void onPasswordChanged(Context context, Intent intent) {
        }

    }
