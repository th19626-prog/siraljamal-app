package com.siraljamal.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;

import java.util.Calendar;

/** Daily reminder on working days (Sun–Thu) that the pages were refreshed. */
public class Notifier extends BroadcastReceiver {
    static final String CH = "updates";

    static void createChannel(Context c) {
        NotificationChannel ch = new NotificationChannel(CH, "تحديثات الصفحات", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("تذكير يومي بتحديث أعمار الموردين ومشتريات المارد");
        ch.setLightColor(Color.parseColor("#C9A227"));
        c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    static void show(Context c, String title, String text) {
        createChannel(c);
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, CH)
            .setSmallIcon(R.drawable.ic_notify)
            .setColor(Color.parseColor("#1F3A5F"))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(new Notification.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build();
        try { c.getSystemService(NotificationManager.class).notify(1, n); } catch (SecurityException ignored) {}
    }

    static void schedule(Context c) {
        SharedPreferences sp = c.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, 2, new Intent(c, Notifier.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        am.cancel(pi);
        if (!sp.getBoolean("notify", true)) return;
        Calendar t = Calendar.getInstance();
        t.set(Calendar.HOUR_OF_DAY, sp.getInt("notify_hour", 9));
        t.set(Calendar.MINUTE, 15);
        t.set(Calendar.SECOND, 0);
        if (t.getTimeInMillis() <= System.currentTimeMillis()) t.add(Calendar.DAY_OF_YEAR, 1);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, t.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);
    }

    @Override public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) { schedule(c); return; }
        int day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        if (day == Calendar.FRIDAY || day == Calendar.SATURDAY) return; // أيام العطلة
        show(c, "سر الجمال - الوكالات", "تم تحديث أعمار الموردين ومشتريات المارد لليوم. اضغط للاطلاع على المطلوب دفعه هذا الأسبوع.");
    }
}
