//实现自启动功能

package guard.anti_phoneaddiction;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver  extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            //Context.startService(new Intent(this, LockGuardianService.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
                Intent thisIntent = new Intent(context, Guardian.class);
                Intent service=new Intent(context, Anti_PhoneAddictionService.class);
                thisIntent.setAction("android.intent.action.MAIN");
                thisIntent.addCategory("android.intent.category.LAUNCHER");
                thisIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                //context.startService(service);
                context.startActivity(thisIntent);
            }
//            if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED"))
//            {
//                Intent i = new Intent(context, Guardian.class);
//                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                context.startActivity(i);
//            }
        }
    }
