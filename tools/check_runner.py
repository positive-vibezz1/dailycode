"""Checks the online code runners the app uses, from GitHub's build servers.

Runs the bundled solutions (and a few broken programs) through Wandbox and
Compiler Explorer and writes a readable report. Usage:
    python3 tools/check_runner.py REPORT_PATH [--all]
"""
import json
import sys
import time
import urllib.request

QUESTIONS = "app/src/main/assets/questions.json"
UA = {"User-Agent": "DailyCode-runner-check", "Content-Type": "application/json",
      "Accept": "application/json"}

report = []


def log(*parts):
    line = " ".join(str(p) for p in parts)
    print(line)
    report.append(line)


def http(method, url, body=None, timeout=60):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers=UA)
    start = time.time()
    with urllib.request.urlopen(req, timeout=timeout) as r:
        raw = r.read().decode("utf-8", "replace")
    return raw, time.time() - start


def version_key(v):
    out = []
    for part in v.replace("-", ".").split("."):
        out.append(int(part) if part.isdigit() else -1)
    return out


# ---------------------------------------------------------------- Wandbox
def wandbox_pick(compilers, language, prefix):
    candidates = [c for c in compilers
                  if c.get("language") == language and c["name"].startswith(prefix)
                  and "head" not in c["name"]]
    candidates.sort(key=lambda c: version_key(c.get("version", "")), reverse=True)
    return candidates[0]["name"] if candidates else None


def wandbox_run(compiler, code):
    raw, secs = http("POST", "https://wandbox.org/api/compile.json",
                     {"compiler": compiler, "code": code, "save": False})
    return json.loads(raw), secs


# ------------------------------------------------------- Compiler Explorer
def godbolt_pick(lang, prefer):
    raw, _ = http("GET", f"https://godbolt.org/api/compilers/{lang}?fields=id,name,semver,supportsExecute")
    items = json.loads(raw)
    ok = [c for c in items if c.get("supportsExecute") and prefer in c["id"]]
    ok.sort(key=lambda c: version_key(c.get("semver") or ""), reverse=True)
    return (ok[0]["id"] if ok else None), [c["id"] for c in items][:40]


def godbolt_run(compiler_id, code):
    body = {"source": code,
            "options": {"userArguments": "",
                        "compilerOptions": {"executorRequest": True, "skipAsm": True},
                        "filters": {"execute": True}}}
    raw, secs = http("POST", f"https://godbolt.org/api/compiler/{compiler_id}/compile", body)
    return json.loads(raw), secs


def godbolt_stdout(res):
    return "\n".join(x.get("text", "") for x in res.get("stdout", []))


BROKEN = [
    ("C", "compile error", '#include <stdio.h>\nint main(void) {\n    printf("hi\\n")\n    return 0;\n}\n'),
    ("C", "crash", '#include <stdio.h>\nint main(void) {\n    int *p = 0;\n    *p = 1;\n    return 0;\n}\n'),
    ("C", "infinite loop", 'int main(void) {\n    for (;;) {}\n}\n'),
    ("Python", "syntax error", 'print("hi"\n'),
    ("Python", "exception", 'x = [1, 2]\nprint(x[5])\n'),
    ("Python", "infinite loop", 'while True:\n    pass\n'),
]


def main():
    out_path = sys.argv[1]
    run_all = "--all" in sys.argv
    questions = json.load(open(QUESTIONS, encoding="utf-8"))
    if not run_all:
        questions = [q for q in questions if q["id"] in ("c01", "c30", "py01", "py30")]

    # Wandbox
    log("=== Wandbox ===")
    try:
        raw, secs = http("GET", "https://wandbox.org/api/list.json")
        compilers = json.loads(raw)
        c_comp = wandbox_pick(compilers, "C", "gcc")
        py_comp = wandbox_pick(compilers, "Python", "cpython")
        log(f"list.json ok in {secs:.1f}s; {len(compilers)} compilers; picked C={c_comp} Python={py_comp}")
        log("C compilers:", [c["name"] for c in compilers if c.get("language") == "C"])
        log("Python compilers:", [c["name"] for c in compilers if c.get("language") == "Python"])
        passed = 0
        for q in questions:
            comp = c_comp if q["lang"] == "C" else py_comp
            try:
                res, secs = wandbox_run(comp, q["solution"])
                got = res.get("program_output", "").rstrip("\n")
                ok = got == q["output"]
                passed += ok
                log(f"{q['id']}: {'PASS' if ok else 'FAIL'} {secs:.1f}s status={res.get('status')}"
                    + ("" if ok else f" raw={json.dumps(res)[:600]}"))
            except Exception as e:  # noqa: BLE001
                log(f"{q['id']}: ERROR {e!r}")
            time.sleep(0.5)
        log(f"Wandbox passed {passed}/{len(questions)}")
        for lang, label, code in BROKEN:
            comp = c_comp if lang == "C" else py_comp
            try:
                res, secs = wandbox_run(comp, code)
                log(f"[{lang} {label}] {secs:.1f}s -> {json.dumps(res)[:700]}")
            except Exception as e:  # noqa: BLE001
                log(f"[{lang} {label}] ERROR {e!r}")
    except Exception as e:  # noqa: BLE001
        log("Wandbox unavailable:", repr(e))

    # Compiler Explorer
    log("\n=== Compiler Explorer ===")
    try:
        c_id, c_ids = godbolt_pick("c", "cg")
        py_id, py_ids = godbolt_pick("python", "python")
        log(f"picked C={c_id} Python={py_id}")
        log("python ids:", py_ids)
        sample = [q for q in questions if q["id"] in ("c01", "c30", "py01", "py30")]
        for q in sample:
            cid = c_id if q["lang"] == "C" else py_id
            try:
                res, secs = godbolt_run(cid, q["solution"])
                got = godbolt_stdout(res).rstrip("\n")
                log(f"{q['id']}: {'PASS' if got == q['output'] else 'FAIL'} {secs:.1f}s"
                    + ("" if got == q["output"] else f" raw={json.dumps(res)[:800]}"))
            except Exception as e:  # noqa: BLE001
                log(f"{q['id']}: ERROR {e!r}")
        for lang, label, code in BROKEN:
            cid = c_id if lang == "C" else py_id
            try:
                res, secs = godbolt_run(cid, code)
                log(f"[{lang} {label}] {secs:.1f}s -> {json.dumps(res)[:700]}")
            except Exception as e:  # noqa: BLE001
                log(f"[{lang} {label}] ERROR {e!r}")
    except Exception as e:  # noqa: BLE001
        log("Compiler Explorer unavailable:", repr(e))

    open(out_path, "w", encoding="utf-8").write("\n".join(report) + "\n")


if __name__ == "__main__":
    main()
