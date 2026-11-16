package com.dailycode.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Loads the bundled questions once and keeps them in memory. */
public final class QuestionBank {
    private static List<Question> cQuestions;
    private static List<Question> pythonQuestions;

    private QuestionBank() {
    }

    public static List<Question> c(Context context) {
        load(context);
        return cQuestions;
    }

    public static List<Question> python(Context context) {
        load(context);
        return pythonQuestions;
    }

    private static synchronized void load(Context context) {
        if (cQuestions != null) {
            return;
        }
        List<Question> c = new ArrayList<>();
        List<Question> py = new ArrayList<>();
        try (InputStream in = context.getAssets().open("questions.json")) {
            JSONArray items = new JSONArray(readAll(in));
            for (int i = 0; i < items.length(); i++) {
                JSONObject o = items.getJSONObject(i);
                Question q = new Question(
                        o.getString("id"),
                        o.getString("lang"),
                        o.getString("difficulty"),
                        o.getString("title"),
                        o.getString("prompt"),
                        o.getString("output"),
                        o.getString("hint"),
                        o.getString("solution"));
                if (q.isC()) {
                    c.add(q);
                } else {
                    py.add(q);
                }
            }
        } catch (IOException | JSONException e) {
            throw new IllegalStateException("Could not load questions.json", e);
        }
        cQuestions = Collections.unmodifiableList(c);
        pythonQuestions = Collections.unmodifiableList(py);
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
