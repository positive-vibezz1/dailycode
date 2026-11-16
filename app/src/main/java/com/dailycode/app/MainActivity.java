package com.dailycode.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Full view of today's question with hint, solution, and progress. */
public class MainActivity extends Activity {
    public static final String EXTRA_SHOW_HINT = "com.dailycode.app.extra.SHOW_HINT";

    private final Random random = new Random();

    private Question current;
    private boolean bonus;
    private boolean hintShown;
    private boolean solutionShown;
    private boolean pendingHint;

    private TextView metaText;
    private TextView chipLang;
    private TextView difficultyText;
    private TextView bonusLabel;
    private TextView titleText;
    private TextView promptText;
    private TextView outputText;
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

        metaText = findViewById(R.id.text_meta);
        chipLang = findViewById(R.id.chip_lang);
        difficultyText = findViewById(R.id.text_difficulty);
        bonusLabel = findViewById(R.id.text_bonus);
        titleText = findViewById(R.id.text_title);
        promptText = findViewById(R.id.text_prompt);
        outputText = findViewById(R.id.text_output);
        hintText = findViewById(R.id.text_hint);
        solutionText = findViewById(R.id.text_solution);
        hintBox = findViewById(R.id.hint_box);
        solutionBox = findViewById(R.id.solution_box);
        hintButton = findViewById(R.id.btn_hint);
        solutionButton = findViewById(R.id.btn_solution);
        solvedButton = findViewById(R.id.btn_solved);

        applySystemBarPadding(findViewById(R.id.content));
        setUpModePicker();

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
    }

    private void readIntent(Intent intent) {
        if (intent != null && intent.getBooleanExtra(EXTRA_SHOW_HINT, false)) {
            bonus = false;
            pendingHint = true;
            intent.removeExtra(EXTRA_SHOW_HINT);
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
        findViewById(R.id.scroll).scrollTo(0, 0);
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

    private void render() {
        if (current == null) {
            return;
        }
        int day = DailyPicker.dayIndex(this) + 1;
        int streak = DailyPicker.streak(this);
        int total = DailyPicker.totalSolved(this);
        metaText.setText("Day " + day + "  \u00B7  \uD83D\uDD25 " + streak + "-day streak  \u00B7  " + total + " solved");

        chipLang.setText(current.langLabel());
        chipLang.setBackgroundResource(current.isC() ? R.drawable.chip_c : R.drawable.chip_py);
        difficultyText.setText(current.difficulty);
        bonusLabel.setVisibility(bonus ? View.VISIBLE : View.GONE);
        titleText.setText(current.title);
        promptText.setText(current.prompt);
        outputText.setText(current.output);

        hintText.setText(current.hint);
        hintBox.setVisibility(hintShown ? View.VISIBLE : View.GONE);
        hintButton.setText(hintShown ? "Hide hint" : "Show hint");

        solutionText.setText(current.solution);
        solutionBox.setVisibility(solutionShown ? View.VISIBLE : View.GONE);
        solutionButton.setText(solutionShown ? "Hide solution" : "Show solution");

        if (bonus) {
            solvedButton.setText("\u2190 Back to today's question");
            solvedButton.setBackgroundResource(R.drawable.btn_primary);
        } else if (DailyPicker.isSolvedToday(this)) {
            solvedButton.setText("\u2713 Solved today  (tap to undo)");
            solvedButton.setBackgroundResource(R.drawable.btn_done);
        } else {
            solvedButton.setText("Mark as solved");
            solvedButton.setBackgroundResource(R.drawable.btn_primary);
        }
    }

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

    /** The app draws edge to edge, so keep content clear of the status and navigation bars. */
    private void applySystemBarPadding(View content) {
        final int left = content.getPaddingLeft();
        final int top = content.getPaddingTop();
        final int right = content.getPaddingRight();
        final int bottom = content.getPaddingBottom();
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom);
            return insets;
        });
    }
}
