package com.dailycode.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Runs code on a free online service and judges the output.
 *
 * Uses Compiler Explorer (godbolt.org) first and falls back to Wandbox
 * (wandbox.org) if it can't be reached. Neither needs an account.
 * Plain Java (no Android classes) so it can be tested on any JVM.
 */
public final class Runner {
    static final String CE_URL = "https://godbolt.org/api/compiler/";
    static final String CE_C = "cg162";          // GCC 16.2
    static final String CE_PYTHON = "python314"; // Python 3.14
    static final String WANDBOX_URL = "https://wandbox.org/api/compile.json";
    static final String WANDBOX_C = "gcc-13.2.0-c";
    static final String WANDBOX_PYTHON = "cpython-3.13.8";

    private Runner() {
    }

    /** What the online service reported. */
    public static final class Result {
        public final String stdout;
        public final String errors;
        public final int exitCode;
        public final boolean compiled;
        public final boolean timedOut;
        public final String service;

        Result(String stdout, String errors, int exitCode, boolean compiled, boolean timedOut, String service) {
            this.stdout = stdout;
            this.errors = errors;
            this.exitCode = exitCode;
            this.compiled = compiled;
            this.timedOut = timedOut;
            this.service = service;
        }
    }

    /** How the answer was judged, ready to show. */
    public static final class Verdict {
        public static final int CORRECT = 0;
        public static final int WRONG = 1;
        public static final int ERROR = 2;
        public static final int TIMEOUT = 3;
        public static final int OFFLINE = 4;

        public final int kind;
        public final String title;
        public final String details;

        Verdict(int kind, String title, String details) {
            this.kind = kind;
            this.title = title;
            this.details = details;
        }
    }

    /** Runs code and compares its output with the expected output. Blocks: call off the main thread. */
    public static Verdict check(boolean isC, String code, String expectedOutput) {
        Result result;
        try {
            result = run(isC, code);
        } catch (Exception e) {
            return new Verdict(Verdict.OFFLINE, "Couldn't run your code",
                    "The online code runner couldn't be reached. Check your internet connection and try again.");
        }
        return judge(expectedOutput, result);
    }

    /** Tries Compiler Explorer, then Wandbox. Throws only if both fail. */
    public static Result run(boolean isC, String code) throws Exception {
        try {
            return runCompilerExplorer(isC, code);
        } catch (Exception first) {
            return runWandbox(isC, code);
        }
    }

    public static Verdict judge(String expectedOutput, Result r) {
        String expected = normalize(expectedOutput);
        String got = normalize(r.stdout);

        if (expected.equals(got)) {
            return new Verdict(Verdict.CORRECT, "✓ Correct!", "Your output matches the expected output.");
        }
        if (r.timedOut) {
            return new Verdict(Verdict.TIMEOUT, "Your program ran too long",
                    "It was stopped after about 20 seconds. Check for a loop that never ends.");
        }
        String errors = r.errors.trim();
        if (!r.compiled || r.exitCode != 0 || !errors.isEmpty()) {
            StringBuilder b = new StringBuilder(errors.isEmpty() ? "Exit code " + r.exitCode : errors);
            if (!got.isEmpty()) {
                b.append("\n\nOutput before the error:\n").append(got);
            }
            String title;
            if (!r.compiled) {
                title = "Your code doesn't compile";
            } else if (errors.contains("SyntaxError") || errors.contains("IndentationError")) {
                title = "Your code has a syntax error";
            } else {
                title = "Your program hit an error";
            }
            return new Verdict(Verdict.ERROR, title, b.toString());
        }
        StringBuilder b = new StringBuilder("Your output:\n");
        b.append(got.isEmpty() ? "(nothing was printed)" : got);
        String diff = firstDifference(expected, got);
        if (diff != null) {
            b.append("\n\n").append(diff);
        }
        return new Verdict(Verdict.WRONG, "✗ Not quite", b.toString());
    }

    /** Ignores trailing spaces on each line and blank lines at the end. */
    static String normalize(String s) {
        String[] lines = s.replace("\r\n", "\n").split("\n", -1);
        StringBuilder b = new StringBuilder();
        for (String line : lines) {
            int end = line.length();
            while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) {
                end--;
            }
            b.append(line, 0, end).append('\n');
        }
        int end = b.length();
        while (end > 0 && b.charAt(end - 1) == '\n') {
            end--;
        }
        return b.substring(0, end);
    }

    static String firstDifference(String expected, String got) {
        String[] e = expected.split("\n", -1);
        String[] g = got.isEmpty() ? new String[0] : got.split("\n", -1);
        int lines = Math.max(e.length, g.length);
        for (int i = 0; i < lines; i++) {
            String want = i < e.length ? e[i] : null;
            String have = i < g.length ? g[i] : null;
            if (want != null && want.equals(have)) {
                continue;
            }
            if (have == null) {
                return "Line " + (i + 1) + " is missing. Expected:\n" + want;
            }
            if (want == null) {
                return "Extra line " + (i + 1) + ":\n" + have;
            }
            return "First difference, line " + (i + 1) + ":\n  expected: " + want + "\n  yours:    " + have;
        }
        return null;
    }

    // ------------------------------------------------------------ Compiler Explorer

    public static Result runCompilerExplorer(boolean isC, String code) throws Exception {
        JSONObject compilerOptions = new JSONObject();
        compilerOptions.put("executorRequest", true);
        compilerOptions.put("skipAsm", true);
        JSONObject filters = new JSONObject();
        filters.put("execute", true);
        JSONObject options = new JSONObject();
        options.put("userArguments", "");
        options.put("compilerOptions", compilerOptions);
        options.put("filters", filters);
        JSONObject body = new JSONObject();
        body.put("source", code);
        body.put("options", options);

        JSONObject res = new JSONObject(post(CE_URL + (isC ? CE_C : CE_PYTHON) + "/compile", body.toString()));
        if (!res.has("didExecute") && !res.has("buildResult")) {
            throw new IOException("Unexpected response from Compiler Explorer");
        }
        String fileName = isC ? "main.c" : "main.py";
        JSONObject build = res.optJSONObject("buildResult");
        boolean executed = res.optBoolean("didExecute", false);
        if (!executed && build != null && build.optInt("code", 0) != 0) {
            String errors = joinLines(build.optJSONArray("stderr")) + "\n" + joinLines(build.optJSONArray("stdout"));
            return new Result("", tidy(errors, fileName), build.optInt("code", 1), false, false, "Compiler Explorer");
        }
        if (!executed) {
            throw new IOException("Compiler Explorer did not run the program");
        }
        return new Result(
                joinLines(res.optJSONArray("stdout")),
                tidy(joinLines(res.optJSONArray("stderr")), fileName),
                res.optInt("code", 0),
                true,
                res.optBoolean("timedOut", false),
                "Compiler Explorer");
    }

    private static String joinLines(JSONArray lines) throws Exception {
        if (lines == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < lines.length(); i++) {
            JSONObject line = lines.optJSONObject(i);
            if (i > 0) {
                b.append('\n');
            }
            if (line != null) {
                b.append(line.optString("text", ""));
            }
        }
        return b.toString();
    }

    /** Removes terminal colour codes and the service's internal file names. */
    private static String tidy(String text, String fileName) {
        return text.replaceAll("\\x1B\\[[0-9;]*[A-Za-z]", "")
                .replace("<source>", fileName)
                .replace("/app/output.s", fileName)
                .replace("/app/example.c", fileName)
                .trim();
    }

    // ------------------------------------------------------------------- Wandbox

    public static Result runWandbox(boolean isC, String code) throws Exception {
        JSONObject body = new JSONObject();
        body.put("compiler", isC ? WANDBOX_C : WANDBOX_PYTHON);
        body.put("code", code);
        body.put("save", false);
        JSONObject res = new JSONObject(post(WANDBOX_URL, body.toString()));
        if (!res.has("status") && !res.has("program_output")) {
            throw new IOException("Unexpected response from Wandbox");
        }
        String fileName = isC ? "main.c" : "main.py";
        int exit;
        try {
            exit = Integer.parseInt(res.optString("status", "0").trim());
        } catch (NumberFormatException e) {
            exit = -1;
        }
        String out = res.optString("program_output", "");
        String compilerError = res.optString("compiler_error", "");
        String programError = res.optString("program_error", "");
        String signal = res.optString("signal", "");
        boolean compileFailed = exit != 0 && out.isEmpty() && programError.isEmpty() && !compilerError.isEmpty();
        String errors = compileFailed ? compilerError : programError;
        if (!signal.isEmpty() && !compileFailed) {
            errors = (errors + "\nProgram stopped: " + signal).trim();
        }
        return new Result(out, tidy(errors.replace("prog.c", fileName).replace("prog.py", fileName), fileName),
                exit, !compileFailed, signal.toLowerCase().contains("kill"), "Wandbox");
    }

    // ---------------------------------------------------------------------- HTTP

    private static String post(String url, String json) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(40_000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "DailyCode/1.1 (Android learning app)");
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(bytes);
            }
            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status);
            }
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int n;
                while ((n = in.read(chunk)) != -1) {
                    buf.write(chunk, 0, n);
                }
                return new String(buf.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            conn.disconnect();
        }
    }
}
