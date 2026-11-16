package com.dailycode.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Today's question, an answer box that checks your code, plus hint, solution and progress. */
public class MainActivity extends Activity {
    public static final String EXTRA_SHOW_HINT = "com.dailycode.app.extra.SHOW_HINT";
    public static final String EXTRA_FOCUS_ANSWER = "com.dailycode.app.extra.FOCUS_ANSWER";

    private static final String C_STARTER = "#include <stdio.h>\n\nint main(void) {\n    \n    return 0;\n}\n";
    private static final String TAB_KEY = "⇥";
    private static final String[] SYMBOLS = {
            TAB_KEY, "(", ")", "{", "}", "[", "]", "\"", "'", ":", ";", "=", "<", ">",
            "+", "-", "*", "/", "%", "#", "&", "|", "!", "_", ",", ".", "\\"
    };

    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Runnable saveDraftTask = this::saveDraftNow;

    private Question current;
    private boolean bonus;
    private boolean hintShown;
    private boolean solutionShown;
    private boolean pendingHint;
    private boolean pendingFocus;
    private boolean running;
    private boolean settingCode;
    private int pendingIndentAt = -1;
    private String editorQuestionId;
    private Runner.Verdict verdict;
    private String verdictQuestionId;

    private ScrollView scroll;
    private View answerSection;
    private TextView metaText;
    private TextView chipLang;
    private TextView difficultyText;
    private TextView bonusLabel;
    private TextView titleText;
    private TextView promptText;
    private TextView outputText;
    private EditText codeInput;
    private Button runButton;
    private View resultBox;
    private TextView resultTitle;
    private TextView resultDetails;
    private TextView hintText;
    private TextView solutionText;
    private View hintBox;
    private View solutionBox;
    private Button hintButton;
    private Button solutionButton;
    private Button solvedButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        scroll = findViewById(R.id.scroll);
        answerSection = findViewById(R.id.answer_section);
        metaText = findViewById(R.id.text_meta);
        chipLang = findViewById(R.id.chip_lang);
        difficultyText = findViewById(R.id.text_difficulty);
        bonusLabel = findViewById(R.id.text_bonus);
        titleText = findViewById(R.id.text_title);
        promptText = findViewById(R.id.text_prompt);
        outputText = findViewById(R.id.text_output);
        codeInput = findViewById(R.id.code_input);
        runButton = findViewById(R.id.btn_run);
        resultBox = findViewById(R.id.result_box);
        resultTitle = findViewById(R.id.result_title);
        resultDetails = findViewById(R.id.result_details);
        hintText = findViewById(R.id.text_hint);
        solutionText = findViewById(R.id.text_solution);
        hintBox = findViewById(R.id.hint_box);
        solutionBox = findViewById(R.id.solution_box);
        hintButton = findViewById(R.id.btn_hint);
        solutionButton = findViewById(R.id.btn_solution);
        solvedButton = findViewById(R.id.btn_solved);

        applyInsets(findViewById(R.id.root), findViewById(R.id.content));
        setUpModePicker();
        setUpEditor();

        runButton.setOnClickListener(v -> runCheck());
        findViewById(R.id.btn_reset).setOnClickListener(v -> resetCode());
        hintButton.setOnClickListener(v -> {
            hintShown = !hintShown;
            render();
        });
        solutionButton.setOnClickListener(v -> {
            solutionShown = !solutionShown;
            render();
        });
        solvedButton.setOnClickListener(v -> {
            if (bonus) {
                bonus = false;
                showToday();
            } else {
                DailyPicker.toggleSolvedToday(this);
                DailyCodeWidget.updateAll(this);
            }
            render();
        });
        findViewById(R.id.btn_copy).setOnClickListener(v -> copyQuestion());
        findViewById(R.id.btn_bonus).setOnClickListener(v -> showBonus());

        readIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!bonus) {
            showToday();
        }
        if (pendingHint) {
            hintShown = true;
            pendingHint = false;
        }
        render();
        DailyCodeWidget.updateAll(this);
        if (pendingFocus) {
            pendingFocus = false;
            focusAnswer();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveDraftNow();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        super.onDestroy();
    }

    private void readIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        if (intent.getBooleanExtra(EXTRA_SHOW_HINT, false)) {
            bonus = false;
            pendingHint = true;
            intent.removeExtra(EXTRA_SHOW_HINT);
        }
        if (intent.getBooleanExtra(EXTRA_FOCUS_ANSWER, false)) {
            bonus = false;
            pendingFocus = true;
            intent.removeExtra(EXTRA_FOCUS_ANSWER);
        }
    }

    /** Loads today's question, hiding the hint and solution if it changed. */
    private void showToday() {
        Question today = DailyPicker.todaysQuestion(this);
        if (current == null || !today.id.equals(current.id)) {
            hintShown = false;
            solutionShown = false;
        }
        current = today;
    }

    private void showBonus() {
        String mode = DailyPicker.mode(this);
        List<Question> pool = new ArrayList<>();
        if (!DailyPicker.MODE_PYTHON.equals(mode)) {
            pool.addAll(QuestionBank.c(this));
        }
        if (!DailyPicker.MODE_C.equals(mode)) {
            pool.addAll(QuestionBank.python(this));
        }
        Question next;
        do {
            next = pool.get(random.nextInt(pool.size()));
        } while (pool.size() > 1 && current != null && next.id.equals(current.id));

        current = next;
        bonus = true;
        hintShown = false;
        solutionShown = false;
        render();
        scroll.scrollTo(0, 0);
    }

    private void setUpModePicker() {
        RadioGroup group = findViewById(R.id.mode_group);
        String mode = DailyPicker.mode(this);
        if (DailyPicker.MODE_C.equals(mode)) {
            group.check(R.id.mode_c);
        } else if (DailyPicker.MODE_PYTHON.equals(mode)) {
            group.check(R.id.mode_python);
        } else {
            group.check(R.id.mode_mix);
        }
        group.setOnCheckedChangeListener((g, checkedId) -> {
            String newMode;
            if (checkedId == R.id.mode_c) {
                newMode = DailyPicker.MODE_C;
            } else if (checkedId == R.id.mode_python) {
                newMode = DailyPicker.MODE_PYTHON;
            } else {
                newMode = DailyPicker.MODE_MIX;
            }
            DailyPicker.setMode(this, newMode);
            bonus = false;
            showToday();
            render();
            DailyCodeWidget.updateAll(this);
        });
    }

    // ------------------------------------------------------------------ render

    private void render() {
        if (current == null) {
            return;
        }
        int day = DailyPicker.dayIndex(this) + 1;
        int streak = DailyPicker.streak(this);
        int total = DailyPicker.totalSolved(this);
        metaText.setText("Day " + day + "  ·  🔥 " + streak + "-day streak  ·  " + total + " solved");

        chipLang.setText(current.langLabel());
        chipLang.setBackgroundResource(current.isC() ? R.drawable.chip_c : R.drawable.chip_py);
        difficultyText.setText(current.difficulty);
        bonusLabel.setVisibility(bonus ? View.VISIBLE : View.GONE);
        titleText.setText(current.title);
        promptText.setText(current.prompt);
        outputText.setText(current.output);

        syncEditor();
        renderVerdict();

        hintText.setText(current.hint);
        hintBox.setVisibility(hintShown ? View.VISIBLE : View.GONE);
        hintButton.setText(hintShown ? "Hide hint" : "Show hint");

        solutionText.setText(current.solution);
        solutionBox.setVisibility(solutionShown ? View.VISIBLE : View.GONE);
        solutionButton.setText(solutionShown ? "Hide solution" : "Show solution");

        if (bonus) {
            solvedButton.setText("← Back to today's question");
            solvedButton.setBackgroundResource(R.drawable.btn_secondary);
            solvedButton.setTextColor(getColor(R.color.on_btn_secondary));
        } else if (DailyPicker.isSolvedToday(this)) {
            solvedButton.setText("✓ Solved today  (tap to undo)");
            solvedButton.setBackgroundResource(R.drawable.btn_done);
            solvedButton.setTextColor(getColor(R.color.on_btn_primary));
        } else {
            solvedButton.setText("Solved it elsewhere? Mark as solved");
            solvedButton.setBackgroundResource(R.drawable.btn_secondary);
            solvedButton.setTextColor(getColor(R.color.on_btn_secondary));
        }
    }

    private void renderVerdict() {
        if (verdict == null || !current.id.equals(verdictQuestionId)) {
            resultBox.setVisibility(View.GONE);
            return;
        }
        boolean good = verdict.kind == Runner.Verdict.CORRECT;
        boolean codeLike = verdict.kind == Runner.Verdict.WRONG || verdict.kind == Runner.Verdict.ERROR;
        resultBox.setVisibility(View.VISIBLE);
        resultBox.setBackgroundResource(good ? R.drawable.result_good : R.drawable.result_bad);
        resultTitle.setTextColor(getColor(good ? R.color.result_good_text : R.color.result_bad_text));
        resultTitle.setText(verdict.title);
        resultDetails.setTypeface(codeLike ? Typeface.MONOSPACE : Typeface.DEFAULT);
        resultDetails.setText(verdict.details);
    }

    // ------------------------------------------------------------------ editor

    private void setUpEditor() {
        codeInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!settingCode && before == 0 && count == 1 && s.charAt(start) == '\n') {
                    pendingIndentAt = start;
                }
            }

            @Override
            public void afterTextChanged(Editable e) {
                if (settingCode) {
                    return;
                }
                int newline = pendingIndentAt;
                pendingIndentAt = -1;
                if (newline >= 0 && newline < e.length()) {
                    String indent = indentAfter(e, newline);
                    if (!indent.isEmpty()) {
                        settingCode = true;
                        e.insert(newline + 1, indent);
                        settingCode = false;
                    }
                }
                handler.removeCallbacks(saveDraftTask);
                handler.postDelayed(saveDraftTask, 600);
            }
        });

        LinearLayout bar = findViewById(R.id.symbol_bar);
        for (String symbol : SYMBOLS) {
            TextView key = new TextView(this);
            key.setText(symbol);
            key.setGravity(Gravity.CENTER);
            key.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            key.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            key.setTextColor(getColor(R.color.on_btn_secondary));
            key.setBackgroundResource(R.drawable.key_bg);
            key.setMinWidth(dp(44));
            key.setPadding(dp(10), 0, dp(10), 0);
            key.setContentDescription(TAB_KEY.equals(symbol) ? "Tab" : symbol);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
            lp.setMarginEnd(dp(6));
            key.setLayoutParams(lp);
            key.setOnClickListener(v -> insertAtCursor(TAB_KEY.equals(symbol) ? "    " : symbol));
            bar.addView(key);
        }
    }

    /** Keeps the line's indentation after Enter, and indents one more level after ':' or '{'. */
    private static String indentAfter(CharSequence text, int newlineIndex) {
        int lineStart = newlineIndex;
        while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') {
            lineStart--;
        }
        StringBuilder indent = new StringBuilder();
        int i = lineStart;
        while (i < newlineIndex && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) {
            indent.append(text.charAt(i));
            i++;
        }
        int end = newlineIndex;
        while (end > i && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        if (end > i) {
            char last = text.charAt(end - 1);
            if (last == ':' || last == '{') {
                indent.append("    ");
            }
        }
        return indent.toString();
    }

    private void insertAtCursor(String text) {
        Editable e = codeInput.getText();
        int a = codeInput.getSelectionStart();
        int b = codeInput.getSelectionEnd();
        int start = a < 0 ? e.length() : Math.min(a, b);
        int end = a < 0 ? e.length() : Math.max(a, b);
        e.replace(start, end, text);
        codeInput.requestFocus();
    }

    private String starterFor(Question q) {
        return q.isC() ? C_STARTER : "";
    }

    /** Swaps the editor contents when the question changes, keeping a saved draft per question. */
    private void syncEditor() {
        if (current.id.equals(editorQuestionId)) {
            return;
        }
        saveDraftNow();
        String draft = drafts().getString(current.id, null);
        setCode(draft != null ? draft : starterFor(current), draft == null);
        editorQuestionId = current.id;
    }

    private void setCode(String text, boolean isStarter) {
        settingCode = true;
        codeInput.setText(text);
        settingCode = false;
        int cursor = text.length();
        if (isStarter && current.isC()) {
            cursor = C_STARTER.indexOf("    \n") + 4;
        }
        codeInput.setSelection(Math.max(0, Math.min(cursor, text.length())));
    }

    private void resetCode() {
        if (current == null) {
            return;
        }
        setCode(starterFor(current), true);
        drafts().edit().remove(current.id).apply();
        verdict = null;
        renderVerdict();
    }

    private SharedPreferences drafts() {
        return getSharedPreferences("drafts", Context.MODE_PRIVATE);
    }

    private void saveDraftNow() {
        handler.removeCallbacks(saveDraftTask);
        if (editorQuestionId == null || codeInput == null) {
            return;
        }
        String text = codeInput.getText().toString();
        SharedPreferences.Editor editor = drafts().edit();
        if (text.trim().isEmpty() || text.equals(C_STARTER)) {
            editor.remove(editorQuestionId);
        } else {
            editor.putString(editorQuestionId, text);
        }
        editor.apply();
    }

    private void focusAnswer() {
        scroll.post(() -> {
            scroll.smoothScrollTo(0, Math.max(0, answerSection.getTop() - dp(8)));
            codeInput.requestFocus();
        });
        handler.postDelayed(() -> {
            InputMethodManager imm = getSystemService(InputMethodManager.class);
            if (imm != null && codeInput.hasFocus()) {
                imm.showSoftInput(codeInput, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 350);
    }

    // ------------------------------------------------------------------- check

    private void runCheck() {
        if (running || current == null) {
            return;
        }
        final Question question = current;
        final String code = codeInput.getText().toString();
        if (code.trim().isEmpty() || code.equals(C_STARTER)) {
            verdict = new Runner.Verdict(Runner.Verdict.WRONG, "Write some code first",
                    "Type your answer in the box above, then tap Run & check.");
            verdictQuestionId = question.id;
            renderVerdict();
            return;
        }
        saveDraftNow();
        hideKeyboard();
        running = true;
        runButton.setEnabled(false);
        runButton.setText(R.string.btn_running);
        final boolean countsForToday = !bonus;

        executor.execute(() -> {
            Runner.Verdict result = Runner.check(question.isC(), code, question.output);
            runOnUiThread(() -> onVerdict(question, countsForToday, result));
        });
    }

    private void onVerdict(Question question, boolean countsForToday, Runner.Verdict result) {
        if (isDestroyed()) {
            return;
        }
        running = false;
        runButton.setEnabled(true);
        runButton.setText(R.string.btn_run);

        Runner.Verdict shown = result;
        if (result.kind == Runner.Verdict.CORRECT && countsForToday
                && question.id.equals(DailyPicker.todaysQuestion(this).id)) {
            DailyPicker.markSolvedToday(this);
            DailyCodeWidget.updateAll(this);
            int streak = DailyPicker.streak(this);
            shown = new Runner.Verdict(result.kind, result.title,
                    "Your output matches. Today is marked as solved — 🔥 "
                            + streak + "-day streak!");
        } else if (result.kind == Runner.Verdict.CORRECT) {
            shown = new Runner.Verdict(result.kind, result.title, "Your output matches. Nice work!");
        }
        verdict = shown;
        verdictQuestionId = question.id;
        render();
        if (current != null && current.id.equals(question.id)) {
            scroll.post(() -> scroll.smoothScrollTo(0,
                    Math.max(0, answerSection.getTop() + resultBox.getTop() - dp(120))));
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) {
            imm.hideSoftInputFromWindow(codeInput.getWindowToken(), 0);
        }
    }

    // ------------------------------------------------------------------- misc

    private void copyQuestion() {
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        if (clipboard == null || current == null) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(current.title, current.asShareText()));
        // Android 13+ shows its own "copied" confirmation.
        if (Build.VERSION.SDK_INT < 33) {
            Toast.makeText(this, "Question copied", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /**
     * The app draws edge to edge. Pad the content clear of the status bar, and shrink the
     * scroll area above the navigation bar or the keyboard so the answer box stays visible.
     */
    private void applyInsets(View root, View content) {
        final int left = content.getPaddingLeft();
        final int top = content.getPaddingTop();
        final int right = content.getPaddingRight();
        final int bottom = content.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsets.Type.ime());
            v.setPadding(bars.left, 0, bars.right, Math.max(bars.bottom, ime.bottom));
            content.setPadding(left, top + bars.top, right, bottom);
            return insets;
        });
    }
}
