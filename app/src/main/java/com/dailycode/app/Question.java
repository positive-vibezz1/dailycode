package com.dailycode.app;

/** One coding challenge loaded from assets/questions.json. */
public final class Question {
    public final String id;
    public final String lang;
    public final String difficulty;
    public final String title;
    public final String prompt;
    public final String output;
    public final String hint;
    public final String solution;

    Question(String id, String lang, String difficulty, String title,
             String prompt, String output, String hint, String solution) {
        this.id = id;
        this.lang = lang;
        this.difficulty = difficulty;
        this.title = title;
        this.prompt = prompt;
        this.output = output;
        this.hint = hint;
        this.solution = solution;
    }

    public boolean isC() {
        return "C".equals(lang);
    }

    /** Short label for the language chip. */
    public String langLabel() {
        return isC() ? "C" : "PYTHON";
    }

    /** Plain-text version for pasting into a code editor as a comment. */
    public String asShareText() {
        return "[" + lang + " \u00B7 " + difficulty + "] " + title + "\n\n"
                + prompt + "\n\nExpected output:\n" + output + "\n";
    }
}
