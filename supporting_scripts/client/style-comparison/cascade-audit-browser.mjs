/**
 * Runs in the page. For the cascade layer `layerName`, it lists every (element, property) where a declaration of the layer
 * loses although it would win by specificity and source order alone:
 * - `FLIP`: against a declaration outside the cascade layers with a lower specificity or an earlier position
 * - `UTIL`: against a Tailwind utility, where no declaration outside the layers settles the property anyway
 * These are the places whose appearance changes when styles move into that layer, or out of it. Pseudo-classes and
 * attributes of a state (`:hover`, `[aria-disabled]`, `[data-state]`) are ignored when the selectors are matched, so the
 * states of an element are covered without bringing the element into each of them.
 */
export default (layerName) => {
    const inLayer = (layer) => layer === layerName || (layer || '').startsWith(layerName + '.');
    const STATE =
        /:(hover|focus-visible|focus-within|focus|active|disabled|enabled|checked|indeterminate|placeholder-shown|invalid|valid|required|optional|read-only|read-write|visited|target|open|default|autofill|user-invalid|user-valid)(?![\w-])/g;
    const STATE_ATTR =
        /\[(aria-(?:disabled|pressed|selected|checked|expanded|invalid|current|busy|hidden|readonly|required)|data-(?:state|active|disabled|open|selected|highlighted|checked|focus|focused|invalid|loading|dragging|pressed|hover|empty|visible|placeholder)|disabled|readonly|open|hidden|inert)([~|^$*]?=("[^"]*"|'[^']*'|[^\]]*))?( [is])?\]/g;
    const splitTop = (s) => {
        const out = [];
        let d = 0,
            cur = '',
            q = null;
        for (let i = 0; i < s.length; i++) {
            const c = s[i];
            if (q) {
                cur += c;
                if (c === '\\') {
                    cur += s[++i] || '';
                } else if (c === q) q = null;
                continue;
            }
            if (c === '"' || c === "'") {
                q = c;
                cur += c;
                continue;
            }
            if (c === '\\') {
                cur += c + (s[++i] || '');
                continue;
            }
            if (c === '(' || c === '[') d++;
            if (c === ')' || c === ']') d--;
            if (c === ',' && d === 0) {
                out.push(cur.trim());
                cur = '';
            } else cur += c;
        }
        if (cur.trim()) out.push(cur.trim());
        return out;
    };
    const spec = (sel) => {
        let a = 0,
            b = 0,
            c = 0;
        let i = 0;
        const n = sel.length;
        const add = (x) => {
            a += x[0];
            b += x[1];
            c += x[2];
        };
        const max = (list) => list.map(spec).reduce((m, x) => (x[0] > m[0] || (x[0] === m[0] && (x[1] > m[1] || (x[1] === m[1] && x[2] > m[2]))) ? x : m), [0, 0, 0]);
        const ident = () => {
            const s = i;
            while (i < n) {
                if (sel[i] === '\\') {
                    i += 2;
                    continue;
                }
                if (/[\w-]/.test(sel[i]) || sel.charCodeAt(i) > 127) i++;
                else break;
            }
            return sel.slice(s, i);
        };
        const paren = () => {
            let d = 1;
            const s = ++i;
            while (i < n && d) {
                if (sel[i] === '\\') {
                    i += 2;
                    continue;
                }
                if (sel[i] === '(') d++;
                if (sel[i] === ')') d--;
                i++;
            }
            return sel.slice(s, i - 1);
        };
        while (i < n) {
            const ch = sel[i];
            if (ch === '#') {
                i++;
                ident();
                a++;
            } else if (ch === '.') {
                i++;
                ident();
                b++;
            } else if (ch === '[') {
                let d = 1;
                i++;
                while (i < n && d) {
                    if (sel[i] === '\\') {
                        i += 2;
                        continue;
                    }
                    if (sel[i] === '[') d++;
                    if (sel[i] === ']') d--;
                    i++;
                }
                b++;
            } else if (ch === ':') {
                if (sel[i + 1] === ':') {
                    i += 2;
                    ident();
                    if (sel[i] === '(') paren();
                    c++;
                } else {
                    i++;
                    const name = ident().toLowerCase();
                    let arg = null;
                    if (sel[i] === '(') arg = paren();
                    if (name === 'where') {
                        /* 0 */
                    } else if (name === 'is' || name === 'not' || name === 'has' || name === 'matches') add(max(splitTop(arg || '')));
                    else if (name === 'nth-child' || name === 'nth-last-child') {
                        b++;
                        const m = /\sof\s(.+)$/.exec(arg || '');
                        if (m) add(max(splitTop(m[1])));
                    } else if (['before', 'after', 'first-line', 'first-letter'].includes(name)) c++;
                    else b++;
                }
            } else if (ch === '*' || ch === '&' || /[\s>+~,|]/.test(ch)) i++;
            else if (ch === '\\' || /[\w-]/.test(ch) || sel.charCodeAt(i) > 127) {
                const s = i;
                ident();
                if (i === s) i++;
                else c++;
            } else i++;
        }
        return [a, b, c];
    };
    const cmp = (x, y) => x.spec[0] - y.spec[0] || x.spec[1] - y.spec[1] || x.spec[2] - y.spec[2] || x.order - y.order;
    const relax = (sel) => {
        let s = sel,
            prev;
        // :not(<only state things>) disappears
        do {
            prev = s;
            s = s.replace(/:not\(((?:[^()]|\([^()]*\))*)\)/g, (m, inner) => {
                const r = inner
                    .replace(STATE, '')
                    .replace(STATE_ATTR, '')
                    .replace(/[\s,*]/g, '');
                return r === '' ? '' : m;
            });
        } while (s !== prev);
        s = s.replace(STATE, '').replace(STATE_ATTR, '');
        if (s === sel) return sel;
        // a compound that lost everything becomes `*`
        s = s.replace(/(^|[\s>+~(,])(?=[\s>+~),]|$)/g, (m, p) => p + '*').replace(/\*\s*\*/g, '*');
        return s === sel ? sel + ' ' : s;
    };
    const LOGICAL = {
        'padding-inline-start': 'padding-left',
        'padding-inline-end': 'padding-right',
        'padding-block-start': 'padding-top',
        'padding-block-end': 'padding-bottom',
        'margin-inline-start': 'margin-left',
        'margin-inline-end': 'margin-right',
        'margin-block-start': 'margin-top',
        'margin-block-end': 'margin-bottom',
        'inset-inline-start': 'left',
        'inset-inline-end': 'right',
        'inset-block-start': 'top',
        'inset-block-end': 'bottom',
        'border-inline-start-width': 'border-left-width',
        'border-inline-end-width': 'border-right-width',
        'border-block-start-width': 'border-top-width',
        'border-block-end-width': 'border-bottom-width',
        'border-inline-start-color': 'border-left-color',
        'border-inline-end-color': 'border-right-color',
        'border-block-start-color': 'border-top-color',
        'border-block-end-color': 'border-bottom-color',
        'border-inline-start-style': 'border-left-style',
        'border-inline-end-style': 'border-right-style',
        'border-block-start-style': 'border-top-style',
        'border-block-end-style': 'border-bottom-style',
        'border-start-start-radius': 'border-top-left-radius',
        'border-start-end-radius': 'border-top-right-radius',
        'border-end-start-radius': 'border-bottom-left-radius',
        'border-end-end-radius': 'border-bottom-right-radius',
        'inline-size': 'width',
        'block-size': 'height',
        'min-inline-size': 'min-width',
        'min-block-size': 'min-height',
        'max-inline-size': 'max-width',
        'max-block-size': 'max-height',
    };
    const rules = [];
    let order = 0;
    const addRule = (selText, style, ctx) => {
        if (!style || !style.length) return;
        const props = new Map();
        const vals = new Map();
        for (let i = 0; i < style.length; i++) {
            const p = style[i];
            if (p.startsWith('--')) continue;
            props.set(LOGICAL[p] || p, style.getPropertyPriority(p) === 'important');
            vals.set(
                LOGICAL[p] || p,
                style
                    .getPropertyValue(p)
                    .replace(/--tumaet-ui-spacing/g, '--spacing')
                    .replace(/\s+/g, ' '),
            );
        }
        if (!props.size) return;
        for (const one of splitTop(selText)) {
            if (/::|:(before|after|first-line|first-letter|placeholder|selection|marker|backdrop)(?![\w-])/.test(one)) continue;
            const r = relax(one);
            rules.push({ sel: one, relaxed: r, cond: ctx.cond || r !== one, layer: ctx.layer, props, vals, spec: spec(one), order: order++ });
        }
    };
    const walk = (list, ctx, parentSel) => {
        for (const rule of list) {
            const type = rule.constructor.name;
            if (type === 'CSSLayerBlockRule') walk(rule.cssRules, { ...ctx, layer: (ctx.layer ? ctx.layer + '.' : '') + rule.name }, parentSel);
            else if (type === 'CSSMediaRule') {
                if (/print/.test(rule.conditionText)) continue;
                walk(rule.cssRules, { ...ctx, cond: ctx.cond || !/^\(hover:\s*hover\)$/.test(rule.conditionText.trim()) }, parentSel);
            } else if (type === 'CSSSupportsRule' || type === 'CSSContainerRule' || type === 'CSSScopeRule' || type === 'CSSStartingStyleRule')
                walk(rule.cssRules, { ...ctx, cond: true }, parentSel);
            else if (type === 'CSSStyleRule') {
                let sel = rule.selectorText;
                if (parentSel)
                    sel = splitTop(sel)
                        .map((s) => (s.includes('&') ? s.replace(/&/g, splitTop(parentSel).length > 1 ? ':is(' + parentSel + ')' : parentSel) : parentSel + ' ' + s))
                        .join(', ');
                addRule(sel, rule.style, ctx);
                if (rule.cssRules && rule.cssRules.length) walk(rule.cssRules, ctx, sel);
            } else if (type === 'CSSNestedDeclarations') {
                if (parentSel) addRule(parentSel, rule.style, ctx);
            }
        }
    };
    for (const sheet of document.styleSheets) {
        let list;
        try {
            list = sheet.cssRules;
        } catch (e) {
            continue;
        }
        walk(list, { layer: null, cond: false }, null);
    }
    const q = (sel) => {
        try {
            return document.querySelectorAll(sel);
        } catch (e) {
            return [];
        }
    };
    const layerMap = new Map(); // element -> Map(property -> [rules of the layer])
    for (const r of rules)
        if (inLayer(r.layer))
            for (const e of q(r.relaxed)) {
                let m = layerMap.get(e);
                if (!m) layerMap.set(e, (m = new Map()));
                for (const p of r.props.keys()) {
                    let l = m.get(p);
                    if (!l) m.set(p, (l = []));
                    l.push(r);
                }
            }
    const otherMap = new Map(); // element -> Map(prop -> {H:[], U:[], imp:boolean})
    for (const r of rules) {
        if (inLayer(r.layer) || (r.layer && r.layer !== 'utilities')) continue;
        for (const e of q(r.relaxed)) {
            const km = layerMap.get(e);
            if (!km) continue;
            for (const [p, imp] of r.props) {
                if (!km.has(p)) continue;
                let m = otherMap.get(e);
                if (!m) otherMap.set(e, (m = new Map()));
                let x = m.get(p);
                if (!x) m.set(p, (x = { H: [], U: [], imp: false }));
                if (imp && !r.cond) x.imp = true;
                else if (imp) continue;
                else (r.layer ? x.U : x.H).push(r);
            }
        }
    }
    const desc = (e) => {
        const c = typeof e.className === 'string' ? e.className : '';
        const one = (x) =>
            x.tagName.toLowerCase() +
            (typeof x.className === 'string' && x.className
                ? '.' +
                  x.className
                      .trim()
                      .split(/\s+/)
                      .filter((t) => !t.startsWith('tumaet:'))
                      .slice(0, 6)
                      .join('.')
                : '');
        return (e.parentElement ? one(e.parentElement) + ' > ' : '') + e.tagName.toLowerCase() + '|' + c.slice(0, 200);
    };
    // the element that carries the utility class of a rule: the element itself, or an ancestor for a variant that targets children
    const owner = (e, sel) => {
        const m = /^(?::is\()?\.((?:\\.|[\w-])+)/.exec(sel);
        if (!m) return null;
        const tok = m[1].replace(/\\(.)/g, '$1');
        for (let x = e, i = 0; x && i < 6; x = x.parentElement, i++)
            if (x.classList && x.classList.contains(tok))
                return { tok, tag: x.tagName.toLowerCase(), cls: [...x.classList].filter((t) => !t.startsWith('tumaet') && !t.startsWith('ng-')).join(' '), self: x === e };
        return { tok };
    };
    const agg = new Map();
    for (const [e, m] of otherMap) {
        const km = layerMap.get(e);
        for (const [p, x] of m) {
            if (x.imp) continue;
            if (e.style && e.style.getPropertyValue(p)) continue;
            const hostUncond = x.H.filter((r) => !r.cond)
                .sort(cmp)
                .pop();
            for (const K of km.get(p)) {
                if (hostUncond && cmp(hostUncond, K) > 0) continue; // the kit rule never won here
                const hits = [];
                for (const H of x.H) if (cmp(K, H) > 0) hits.push(['FLIP', H]);
                if (!hostUncond) for (const U of x.U) hits.push(['UTIL', U]);
                for (const [type, R] of hits) {
                    const key = type + '|' + p + '|' + R.sel + '|' + K.sel;
                    let a = agg.get(key);
                    if (!a)
                        agg.set(
                            key,
                            (a = {
                                type,
                                prop: p,
                                host: R.sel,
                                kit: K.sel,
                                condHost: R.cond,
                                condKit: K.cond,
                                same: R.vals.get(p) === K.vals.get(p),
                                hv: R.vals.get(p),
                                kv: K.vals.get(p),
                                n: 0,
                                ex: desc(e),
                                owner: owner(e, R.sel),
                            }),
                        );
                    a.n++;
                }
            }
        }
    }
    return { rules: rules.length, layerElements: layerMap.size, conflicts: [...agg.values()] };
};
