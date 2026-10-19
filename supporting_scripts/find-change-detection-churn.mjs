/**
 * Reports component members that build a NEW value on every call and are read during change detection.
 *
 * Why this matters: a template binding is re-evaluated on every change-detection pass. A member that returns a fresh
 * array, object, Observable or function each time therefore changes its consumer's input on every pass — an OnPush
 * child can never skip work, and if the consumer writes back into the producer the churn becomes a loop. PR #13502
 * fixed that shape twice in programming-exercise-update.component.ts, where it froze the page in a production build
 * and surfaced only as two E2E tests timing out at four minutes.
 *
 * Usage: node supporting_scripts/find-change-detection-churn.mjs
 *
 * Output is grouped by severity:
 *   INPUT-BOUND    the member's name appears inside a property binding — the shape that reaches a child component
 *   template-only  read while rendering, but not handed to a child (wasted allocation rather than a propagating loop)
 *   SCALAR-ok      returns a primitive, so the returned identity is stable no matter what the body allocates
 *
 * This is a heuristic triage aid, not a proof, and it is deliberately reported rather than enforced. Known limits:
 *   - INPUT-BOUND matches the member NAME inside a binding, not the binding's resulting identity, so
 *     `[ngModel]="a.length === usersWithoutCurrentUser.length"` is listed even though the bound value is a boolean.
 *   - A getter reading live DOM state (e.g. `getBoundingClientRect()`) is listed, but must NOT be converted to a
 *     `computed()`: the measurement is not reactive and the computed would cache a stale value. Those need the
 *     measurement pushed into a signal (ResizeObserver) first.
 *   - A `computed()` is only correct when every dependency is a signal. A getter reading plain fields must not be
 *     converted without converting those fields too.
 * The narrow, always-wrong subset of this problem is enforced by `localRules/no-bind-in-template-binding`.
 */
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, dirname } from 'node:path';

const ROOT = 'src/main/webapp/app';

/** Expressions that mint a fresh reference each time they are evaluated. */
const CHURN = [
    { re: /\.asObservable\(\)/, kind: 'asObservable()' },
    { re: /\.bind\s*\(/, kind: '.bind()' },
    { re: /\.pipe\(/, kind: '.pipe()' },
    { re: /\?\?\s*\[\]/, kind: '?? []' },
    { re: /\?\?\s*\{\}/, kind: '?? {}' },
    { re: /\|\|\s*\[\]/, kind: '|| []' },
    { re: /return\s*\[/, kind: 'array literal' },
    { re: /return\s*\{/, kind: 'object literal' },
    { re: /\.map\(/, kind: '.map()' },
    { re: /\.filter\(/, kind: '.filter()' },
    { re: /\.slice\(/, kind: '.slice()' },
    { re: /\.concat\(/, kind: '.concat()' },
    { re: /\.sort\(/, kind: '.sort()' },
    { re: /Object\.(values|keys|entries)\(/, kind: 'Object.values/keys/entries()' },
    { re: /Array\.from\(/, kind: 'Array.from()' },
    { re: /\bnew\s+[A-Z]/, kind: 'new X()' },
];

function walk(dir, out = []) {
    for (const entry of readdirSync(dir)) {
        const p = join(dir, entry);
        if (statSync(p).isDirectory()) walk(p, out);
        else if (p.endsWith('.ts') && !p.endsWith('.spec.ts')) out.push(p);
    }
    return out;
}

/** Brace-matched body of every `get name()` / `name()` member declared at class-member indentation. */
function members(text) {
    const found = [];
    const re = /\n {4}(?:(?:public|private|protected|readonly)\s+)*(get\s+)?([A-Za-z_$][\w$]*)\s*\(\s*\)\s*(?::\s*[^{;]+)?\{/g;
    let m;
    while ((m = re.exec(text))) {
        let depth = 1;
        let i = re.lastIndex;
        while (i < text.length && depth > 0) {
            if (text[i] === '{') depth++;
            else if (text[i] === '}') depth--;
            i++;
        }
        found.push({
            name: m[2],
            isGetter: Boolean(m[1]),
            body: text.slice(re.lastIndex, i - 1),
            line: text.slice(0, m.index).split('\n').length + 1,
            retType: (m[0].match(/\)\s*:\s*([^{]+)\{$/) || [, ''])[1].trim(),
            firstReturn: (text.slice(re.lastIndex, i - 1).match(/return\s+([^;]{0,90})/) || [, ''])[1].replace(/\s+/g, ' ').trim(),
        });
    }
    return found;
}

/** Template text for a component, with event bindings stripped: those run on user action, not during rendering. */
function templateFor(file, text) {
    let t = '';
    const htmlMatch = text.match(/templateUrl:\s*'([^']+)'/);
    const path = htmlMatch ? join(dirname(file), htmlMatch[1]) : file.replace(/\.ts$/, '.html');
    try {
        t += readFileSync(path, 'utf8');
    } catch {
        /* inline-only or generated template */
    }
    const inline = text.match(/template:\s*`([\s\S]*?)`/);
    if (inline) t += inline[1];
    return t.replace(/\((?:[\w.$-]+)\)\s*=\s*"[^"]*"/g, ' ').replace(/\bon-[\w.$-]+\s*=\s*"[^"]*"/g, ' ');
}

const rows = [];
for (const file of walk(ROOT)) {
    const text = readFileSync(file, 'utf8');
    if (!/@(Component|Directive)\(/.test(text)) continue;
    const tpl = templateFor(file, text);
    if (!tpl) continue;
    for (const mem of members(text)) {
        const hits = CHURN.filter((c) => c.re.test(mem.body)).map((c) => c.kind);
        if (!hits.length) continue;
        // Escape the member name before interpolating it: observable-suffixed members like `stateReplaced$` are
        // common here, and an unescaped `$` is a regex end anchor, so those members were silently dropped. For the
        // same reason the boundaries below are explicit lookaround rather than `\b`, which does not treat `$` as
        // part of an identifier.
        const name = mem.name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        if (!new RegExp(`[^\\w$.]${name}\\s*(\\(|[)\\s|}!?.=<>&+\\]])`).test(tpl)) continue;
        const asInput = new RegExp(`\\[[\\w.$-]+\\]\\s*=\\s*"[^"]*(?<![\\w$])${name}(?![\\w$])`).test(tpl);
        const scalar = /^(number|boolean|string|void)(\s*\|\s*undefined)?$/.test(mem.retType) || /\.length\b|^!/.test(mem.firstReturn);
        rows.push({ ...mem, file, hits, asInput, scalar });
    }
}

const tierOf = (r) => (r.scalar ? 'SCALAR-ok' : r.asInput ? 'INPUT-BOUND' : 'template-only');
rows.sort((a, b) => a.file.localeCompare(b.file) || a.line - b.line);
for (const tier of ['INPUT-BOUND', 'template-only', 'SCALAR-ok']) {
    const group = rows.filter((r) => tierOf(r) === tier);
    console.log(`\n===== ${tier} (${group.length}) =====`);
    for (const r of group) {
        console.log(`${r.file}:${r.line}\n    ${r.isGetter ? 'get ' : ''}${r.name}(): ${r.retType || '?'}  <- ${r.hits.join(', ')}`);
    }
}
console.log(`\n${rows.length} members total`);
