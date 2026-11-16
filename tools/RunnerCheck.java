import com.dailycode.app.Runner;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Exercises the app's Runner class against the live online services, from
 * GitHub's build servers. Run by .github/workflows/build.yml after each build.
 */
public class RunnerCheck {
    private static final String[][] BROKEN = {
            {"C", "compile error", "#include <stdio.h>\nint main(void) {\n    printf(\"hi\\n\")\n    return 0;\n}\n"},
            {"C", "wrong answer", "#include <stdio.h>\nint main(void) {\n    printf(\"Sum: 100\\nAverage: 18\\n\");\n    return 0;\n}\n"},
            {"C", "crash", "#include <stdio.h>\nint main(void) {\n    int *p = 0;\n    *p = 1;\n    return 0;\n}\n"},
            {"Python", "syntax error", "print(\"hi\"\n"},
            {"Python", "exception", "x = [1, 2]\nprint(x[5])\n"},
            {"Python", "infinite loop", "while True:\n    pass\n"},
    };

    public static void main(String[] args) throws Exception {
        JSONArray all = new JSONArray(new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8));
        int failures = 0;

        System.out.println("=== Solutions through Runner.check (Compiler Explorer first) ===");
        String c01Output = null;
        for (int i = 0; i < all.length(); i++) {
            JSONObject q = all.getJSONObject(i);
            String id = q.getString("id");
            if (id.equals("c01")) {
                c01Output = q.getString("output");
            }
            boolean sample = args.length > 1 && args[1].equals("--all")
                    || id.equals("c01") || id.equals("c30") || id.equals("py01") || id.equals("py30");
            if (!sample) {
                continue;
            }
            boolean isC = q.getString("lang").equals("C");
            long start = System.currentTimeMillis();
            Runner.Verdict v = Runner.check(isC, q.getString("solution"), q.getString("output"));
            boolean ok = v.kind == Runner.Verdict.CORRECT;
            failures += ok ? 0 : 1;
            System.out.println(id + ": " + (ok ? "PASS" : "FAIL") + " in " + (System.currentTimeMillis() - start)
                    + "ms" + (ok ? "" : "  -> " + v.title + " | " + v.details));
        }

        System.out.println("\n=== Wandbox backup path ===");
        for (int i = 0; i < all.length(); i++) {
            JSONObject q = all.getJSONObject(i);
            String id = q.getString("id");
            if (!id.equals("c01") && !id.equals("py01")) {
                continue;
            }
            boolean isC = q.getString("lang").equals("C");
            try {
                Runner.Result r = Runner.runWandbox(isC, q.getString("solution"));
                Runner.Verdict v = Runner.judge(q.getString("output"), r);
                boolean ok = v.kind == Runner.Verdict.CORRECT;
                failures += ok ? 0 : 1;
                System.out.println(id + " via " + r.service + ": " + (ok ? "PASS" : "FAIL -> " + v.title + " | " + v.details));
            } catch (Exception e) {
                System.out.println(id + " via Wandbox: unavailable (" + e + ")");
            }
        }

        System.out.println("\n=== Broken programs (what the learner would see) ===");
        for (String[] b : BROKEN) {
            boolean isC = b[0].equals("C");
            String expected = isC ? c01Output : "Hi Ada! Next year you'll be 37.";
            long start = System.currentTimeMillis();
            Runner.Verdict v = Runner.check(isC, b[2], expected);
            System.out.println("[" + b[0] + " " + b[1] + "] kind=" + v.kind + " in "
                    + (System.currentTimeMillis() - start) + "ms\n  " + v.title + "\n  "
                    + v.details.replace("\n", "\n  "));
        }

        System.out.println("\nFAILURES: " + failures);
        if (failures > 0) {
            System.exit(1);
        }
    }
}
