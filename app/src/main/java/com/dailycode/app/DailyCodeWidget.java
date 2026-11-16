package com.dailycode.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.SizeF;
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/** Home-screen widget that shows today's coding challenge. */
public class DailyCodeWidget extends AppWidgetProvider {
    public static final String ACTION_TOGGLE_SOLVED = "com.dailycode.app.action.TOGGLE_SOLVED";
    public static final String ACTION_REFRESH = "com.dailycode.app.action.REFRESH";

    private static final int REQ_OPEN = 0;
    private static final int REQ_TOGGLE = 1;
    private static final int REQ_HINT = 2;
    private static final int REQ_MIDNIGHT = 3;

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] widgetIds) {
        RemoteViews views = buildViews(context);
        for (int id : widgetIds) {
            manager.updateAppWidget(id, views);
        }
        scheduleMidnightRefresh(context);
    }

    @Override
    public void onEnabled(Context context) {
        scheduleMidnightRefresh(context);
    }

    @Override
    public void onDisabled(Context context) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        if (alarms != null) {
            alarms.cancel(refreshIntent(context));
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (ACTION_TOGGLE_SOLVED.equals(action)) {
            DailyPicker.toggleSolvedToday(context);
            updateAll(context);
        } else if (ACTION_REFRESH.equals(action)) {
            updateAll(context);
        }
    }

    /** Redraws every placed widget. Safe to call from the app at any time. */
    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, DailyCodeWidget.class));
        if (ids == null || ids.length == 0) {
            return;
        }
        RemoteViews views = buildViews(context);
        for (int id : ids) {
            manager.updateAppWidget(id, views);
        }
        scheduleMidnightRefresh(context);
    }

    /** A compact layout for the default 4x2 size and a roomier one when you make it taller. */
    private static RemoteViews buildViews(Context context) {
        Map<SizeF, RemoteViews> layouts = new HashMap<>();
        layouts.put(new SizeF(160f, 100f), build(context, R.layout.widget_daily));
        layouts.put(new SizeF(200f, 240f), build(context, R.layout.widget_daily_large));
        return new RemoteViews(layouts);
    }

    private static RemoteViews build(Context context, int layoutId) {
        Question q = DailyPicker.todaysQuestion(context);
        int day = DailyPicker.dayIndex(context) + 1;
        int streak = DailyPicker.streak(context);
        boolean solved = DailyPicker.isSolvedToday(context);

        RemoteViews v = new RemoteViews(context.getPackageName(), layoutId);
        v.setTextViewText(R.id.chip_lang, q.langLabel());
        v.setInt(R.id.chip_lang, "setBackgroundResource", q.isC() ? R.drawable.chip_c : R.drawable.chip_py);
        v.setTextViewText(R.id.text_difficulty, q.difficulty);
        v.setTextViewText(R.id.text_meta, streak > 0 ? "Day " + day + "  \uD83D\uDD25" + streak : "Day " + day);
        v.setTextViewText(R.id.text_title, q.title);
        v.setTextViewText(R.id.text_prompt, q.prompt);

        v.setTextViewText(R.id.btn_solved, solved ? "\u2713 Solved" : "Mark solved");
        v.setInt(R.id.btn_solved, "setBackgroundResource", solved ? R.drawable.btn_done : R.drawable.btn_primary);

        v.setOnClickPendingIntent(android.R.id.background, openApp(context, false));
        v.setOnClickPendingIntent(R.id.btn_hint, openApp(context, true));
        v.setOnClickPendingIntent(R.id.btn_solved, broadcast(context, ACTION_TOGGLE_SOLVED, REQ_TOGGLE));
        return v;
    }

    private static PendingIntent openApp(Context context, boolean showHint) {
        Intent intent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_SHOW_HINT, showHint);
        return PendingIntent.getActivity(context, showHint ? REQ_HINT : REQ_OPEN, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent broadcast(Context context, String action, int requestCode) {
        Intent intent = new Intent(context, DailyCodeWidget.class).setAction(action);
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent refreshIntent(Context context) {
        return broadcast(context, ACTION_REFRESH, REQ_MIDNIGHT);
    }

    /**
     * Asks Android to refresh the widget just after midnight. This is an inexact,
     * battery-friendly alarm: if the phone is asleep, it fires when you next wake it.
     */
    private static void scheduleMidnightRefresh(Context context) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        if (alarms == null) {
            return;
        }
        long nextMidnight = LocalDate.now().plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .plusMinutes(1)
                .toInstant()
                .toEpochMilli();
        alarms.set(AlarmManager.RTC, nextMidnight, refreshIntent(context));
    }
}
