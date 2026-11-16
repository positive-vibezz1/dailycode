package com.dailycode.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides which question belongs to today and tracks solved days and streaks.
 *
 * Day 1 is the day you first open the app or add the widget. Questions are
 * ordered easy to hard, so you progress through them in order and then the
 * list starts again from the top.
 */
public final class DailyPicker {
    public static final String MODE_MIX = "mix";
    public static final String MODE_C = "c";
    public static final String MODE_PYTHON = "python";

    private static final String PREFS = "dailycode";
    private static final String KEY_START_DAY = "start_day";
    private static final String KEY_MODE = "mode";
    private static final String KEY_SOLVED = "solved_days";

    private DailyPicker() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static long today() {
        return LocalDate.now().toEpochDay();
    }

    private static long startDay(Context context) {
        SharedPreferences p = prefs(context);
        if (!p.contains(KEY_START_DAY)) {
            p.edit().putLong(KEY_START_DAY, today()).apply();
            return today();
        }
        return p.getLong(KEY_START_DAY, today());
    }

    /** 0 on the first day, 1 on the second, and so on. */
    public static int dayIndex(Context context) {
        return (int) Math.max(0, today() - startDay(context));
    }

    public static String mode(Context context) {
        return prefs(context).getString(KEY_MODE, MODE_MIX);
    }

    public static void setMode(Context context, String mode) {
        prefs(context).edit().putString(KEY_MODE, mode).apply();
    }

    public static Question todaysQuestion(Context context) {
        int n = dayIndex(context);
        List<Question> c = QuestionBank.c(context);
        List<Question> py = QuestionBank.python(context);
        switch (mode(context)) {
            case MODE_C:
                return c.get(n % c.size());
            case MODE_PYTHON:
                return py.get(n % py.size());
            default:
                // Alternate: Python on even days, C on odd days.
                return (n % 2 == 0) ? py.get((n / 2) % py.size()) : c.get((n / 2) % c.size());
        }
    }

    private static Set<String> solvedDays(Context context) {
        return new HashSet<>(prefs(context).getStringSet(KEY_SOLVED, Collections.emptySet()));
    }

    public static boolean isSolvedToday(Context context) {
        return solvedDays(context).contains(Long.toString(today()));
    }

    public static void toggleSolvedToday(Context context) {
        Set<String> solved = solvedDays(context);
        String key = Long.toString(today());
        if (!solved.remove(key)) {
            solved.add(key);
        }
        prefs(context).edit().putStringSet(KEY_SOLVED, solved).apply();
    }

    /** Marks today as solved. Returns true if it wasn't already. */
    public static boolean markSolvedToday(Context context) {
        Set<String> solved = solvedDays(context);
        boolean added = solved.add(Long.toString(today()));
        if (added) {
            prefs(context).edit().putStringSet(KEY_SOLVED, solved).apply();
        }
        return added;
    }

    /** Consecutive solved days ending today (or yesterday, if today isn't done yet). */
    public static int streak(Context context) {
        Set<String> solved = solvedDays(context);
        long day = today();
        if (!solved.contains(Long.toString(day))) {
            day--;
        }
        int count = 0;
        while (solved.contains(Long.toString(day))) {
            count++;
            day--;
        }
        return count;
    }

    public static int totalSolved(Context context) {
        return solvedDays(context).size();
    }
}
