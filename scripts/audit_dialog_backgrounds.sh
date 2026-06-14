#!/usr/bin/env bash
#
# Static audit for the dialog-background convention (precise, variable-tracking).
#
# Every AppCompat `new AlertDialog.Builder(...)` must end up with the themed card_background, or it
# renders with the gray AppCompat default. That happens one of three ways:
#   1. wrapped:   DialogUtil.showWithCardBackground(new AlertDialog.Builder(...) ...)   (or the
#                 withGPlay-local showThemedDialog(...) wrapper)
#   2. matched:   the builder is stored / configured-then-shown, and the resulting dialog variable
#                 is passed to DialogUtil.matchDialogToCardBackground(...) before it is shown
#   3. builder var passed to showWithCardBackground(builder) (the helper creates+matches+shows)
#
# Rather than look at nearby lines, this follows the actual variable: builder -> (.create()) dialog
# -> matchDialogToCardBackground(dialog) / showWithCardBackground(builder). It fails (exit 1) on any
# builder whose dialog is never matched/wrapped, or a double-show bug. Strings/comments are masked
# so braces/parens/semicolons inside them never confuse the statement scanner.

set -uo pipefail
cd "$(dirname "$0")/.."

python3 - "$@" <<'PY'
import os, re, sys

SRC = ["app/src/main/java", "app/src/noGPlay/java", "app/src/withGPlay/java"]
WRAP = ("showWithCardBackground", "showThemedDialog")
BUILDER = "new AlertDialog.Builder("

def java_files():
    for d in SRC:
        for root, _, fs in os.walk(d):
            for f in fs:
                if f.endswith(".java"):
                    yield os.path.join(root, f)

def mask(s):
    """Replace string/char-literal and comment contents with spaces (newlines kept) so the
    statement scanner only sees real code punctuation. Length is preserved."""
    out = []
    i, n = 0, len(s)
    while i < n:
        c = s[i]
        if c == '"' or c == "'":
            q = c; out.append(' '); i += 1
            while i < n and s[i] != q:
                if s[i] == '\\' and i + 1 < n:
                    out.append('  '); i += 2; continue
                out.append('\n' if s[i] == '\n' else ' '); i += 1
            if i < n: out.append(' '); i += 1
            continue
        if c == '/' and i + 1 < n and s[i+1] == '/':
            while i < n and s[i] != '\n':
                out.append(' '); i += 1
            continue
        if c == '/' and i + 1 < n and s[i+1] == '*':
            out.append('  '); i += 2
            while i + 1 < n and not (s[i] == '*' and s[i+1] == '/'):
                out.append('\n' if s[i] == '\n' else ' '); i += 1
            if i + 1 < n: out.append('  '); i += 2
            continue
        out.append(c); i += 1
    return ''.join(out)

def line_of(m, pos):
    return m.count('\n', 0, pos) + 1

def stmt_bounds(m, pos):
    """Return (start, end) of the statement containing the builder at index `pos`:
    start = just after the previous ; { } at depth 0; end = the ; that closes it."""
    # backward to statement start
    i = pos - 1; depth = 0; start = 0
    while i >= 0:
        c = m[i]
        if c in ')}': depth += 1
        elif c in '({':
            if depth == 0: start = i + 1; break
            depth -= 1
        elif c == ';' and depth == 0: start = i + 1; break
        i -= 1
    # forward to statement end
    i = pos; depth = 0; end = len(m)
    while i < len(m):
        c = m[i]
        if c in '([{': depth += 1
        elif c in ')]}': depth -= 1
        elif c == ';' and depth == 0: end = i; break
        i += 1
    return start, end

violations = []
total = wrapped = matched = builderwrap = 0

for path in java_files():
    s = open(path).read()
    m = mask(s)
    for mo in re.finditer(re.escape(BUILDER), m):
        total += 1
        p = mo.start()
        prefix = m[:p].rstrip()
        # form 1: wrapped directly
        if any(prefix.endswith(w + "(") for w in WRAP):
            wrapped += 1; continue
        start, end = stmt_bounds(m, p)
        stmt = m[start:end]
        before = m[start:p]
        # assignment target variable, if any:  ... <var> =  new AlertDialog.Builder(
        am = re.search(r'(\b\w+)\s*=\s*$', before)
        var = am.group(1) if am else None
        trailing = stmt.rstrip()
        ends_show = bool(re.search(r'\.\s*show\s*\(\s*\)\s*$', trailing))
        if ends_show:
            violations.append((path, line_of(m, p), "unwrapped builder ending in .show()"))
            continue
        if var is None:
            violations.append((path, line_of(m, p),
                               "builder not assigned and not wrapped (cannot verify)"))
            continue
        # candidate dialog vars: the assigned var, plus any  X = <var>.create()
        candidates = {var}
        for hop in re.finditer(r'\b(\w+)\s*=\s*' + re.escape(var) + r'\s*\.\s*create\s*\(\s*\)', m):
            candidates.add(hop.group(1))
        # scope the search: from the builder to where this dialog is shown (first <cand>.show()),
        # so identically-named vars in other methods can't satisfy it.
        show_rel = None
        for c in candidates:
            sm = re.search(r'\b' + re.escape(c) + r'\s*\.\s*show\s*\(', m[p:])
            if sm and (show_rel is None or sm.start() < show_rel):
                show_rel = sm.start()
        region = m[p: p + show_rel + 40] if show_rel is not None else m[p: p + 8000]
        ok = False
        for c in candidates:
            tok = re.escape(c)
            if re.search(r'matchDialogToCardBackground\([^;{}]*\b' + tok + r'\b', region):
                ok = True; matched += 1; break
            if any(re.search(w + r'\(\s*' + tok + r'\s*\)', region) for w in WRAP):
                ok = True; builderwrap += 1; break
        if not ok:
            violations.append((path, line_of(m, p),
                               f"dialog var {sorted(candidates)} never matched/wrapped before show"))

# double-show: helper already shows the dialog. Match the wrapper's own close paren (depth-aware,
# so .show() calls inside the builder's lambda args don't count) then check for a trailing .show().
for path in java_files():
    m = mask(open(path).read())
    for w in WRAP:
        for mo in re.finditer(re.escape(w) + r'\(', m):
            i = mo.end() - 1; depth = 0
            while i < len(m):
                if m[i] == '(': depth += 1
                elif m[i] == ')':
                    depth -= 1
                    if depth == 0: break
                i += 1
            after = m[i+1:].lstrip()
            if after.startswith('.') and re.match(r'\.\s*show\s*\(', after):
                violations.append((path, line_of(m, mo.start()),
                                   "double-show (helper already shows)"))

print("-" * 64)
print(f"AppCompat builders: {total}   wrapped: {wrapped}   "
      f"matched: {matched}   builder-wrapped: {builderwrap}   violations: {len(violations)}")
for path, ln, why in sorted(violations):
    print(f"VIOLATION {path}:{ln}  {why}")
if violations:
    print(f"FAIL: {len(violations)} dialog(s) would render with the gray default background.")
    sys.exit(1)
print("OK: every AppCompat dialog applies the themed card_background.")
PY
