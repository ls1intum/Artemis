import { RuleConstraint, evaluateConstraint } from './rule-constraint.model';
import { RuleDirection, StepDirection } from './rule-direction.model';

export interface MathNode {
    type: string;
    value?: string;
    slots?: Record<string, MathNode[]>;
}

/** Callback to look up rendering metadata for a node type from the block registry. */
export type RegistryLookup = (type: string) => { precedence?: number; associativity?: string; layoutCategory?: string; latexSymbol?: string } | undefined;

/**
 * Converts a {@link MathNode} AST to a LaTeX string.
 *
 * When a {@link RegistryLookup} is supplied (loaded from the block registry), the function
 * performs precedence-based auto-parenthesization and drives binary-infix rendering generically
 * from the descriptor's {@code latexSymbol}. Without a lookup, it falls back to the hardcoded
 * switch for backward compatibility.
 */
export function mathNodeToLatex(node: MathNode | undefined, lookup: RegistryLookup = () => undefined, parentPrecedence = -1, isRightChild = false): string {
    if (!node) {
        return '{?}';
    }

    const desc = lookup(node.type);
    const myPrec = desc?.precedence ?? -Infinity;

    const needsParens = parentPrecedence >= 0 && (myPrec === -Infinity || (isRightChild ? myPrec <= parentPrecedence : myPrec < parentPrecedence));

    function renderChild(child: MathNode | undefined, slotKey: string): string {
        const prec = desc?.precedence ?? -1;
        return mathNodeToLatex(child, lookup, prec, slotKey === 'right');
    }

    let inner: string;
    switch (node.type) {
        case 'number':
        case 'variable':
        case 'wildcard':
            inner = node.value ?? '{?}';
            break;
        case 'fraction': {
            const num = mathNodeToLatex(node.slots?.['numerator']?.[0], lookup, -1);
            const den = mathNodeToLatex(node.slots?.['denominator']?.[0], lookup, -1);
            inner = `\\frac{${num}}{${den}}`;
            break;
        }
        case 'parentheses':
            inner = `\\left(${mathNodeToLatex(node.slots?.['content']?.[0], lookup, -1)}\\right)`;
            break;
        case 'add':
        case 'sub':
        case 'mul':
        case 'equality': {
            if (desc?.layoutCategory === 'BINARY_INFIX' && desc.latexSymbol) {
                // Registry-driven: uses latexSymbol with precedence-based parens on children
                inner = `${renderChild(node.slots?.['left']?.[0], 'left')} ${desc.latexSymbol} ${renderChild(node.slots?.['right']?.[0], 'right')}`;
            } else {
                // Fallback when registry not loaded: hardcoded symbols, no auto-parens
                const sym = node.type === 'add' ? '+' : node.type === 'sub' ? '-' : node.type === 'mul' ? '\\cdot' : '=';
                inner = `${mathNodeToLatex(node.slots?.['left']?.[0], lookup)} ${sym} ${mathNodeToLatex(node.slots?.['right']?.[0], lookup)}`;
            }
            break;
        }
        case 'negation': {
            const sym = desc?.latexSymbol ?? '-';
            inner = `${sym}${renderChild(node.slots?.['inner']?.[0], 'inner')}`;
            break;
        }
        default:
            // Registry-driven dispatch for future blocks added without code changes here
            if (desc?.layoutCategory === 'BINARY_INFIX' && desc.latexSymbol) {
                inner = `${renderChild(node.slots?.['left']?.[0], 'left')} ${desc.latexSymbol} ${renderChild(node.slots?.['right']?.[0], 'right')}`;
            } else if (desc?.layoutCategory === 'UNARY_PREFIX' && desc.latexSymbol) {
                const slotKey = Object.keys(node.slots ?? {})[0] ?? 'inner';
                inner = `${desc.latexSymbol}${renderChild(node.slots?.[slotKey]?.[0], slotKey)}`;
            } else {
                inner = `{${node.type}}`;
            }
    }

    return needsParens ? `\\left(${inner}\\right)` : inner;
}

/** Returns all child nodes in canonical order (slots sorted alphabetically, then in-order within slot). */
export function flatChildren(node: MathNode): MathNode[] {
    if (!node.slots) {
        return [];
    }
    return Object.keys(node.slots)
        .sort()
        .flatMap((key) => node.slots![key]);
}

/** Navigates the tree along a path (list of child indices using flatChildren ordering). */
export function nodeAtPath(root: MathNode, path: number[]): MathNode {
    let current = root;
    for (const index of path) {
        const children = flatChildren(current);
        current = children[index];
    }
    return current;
}

/** Deep structural equality for MathNode trees. */
export function mathNodesEqual(a: MathNode, b: MathNode): boolean {
    if (a.type !== b.type || a.value !== b.value) return false;
    const aSlots = a.slots ?? {};
    const bSlots = b.slots ?? {};
    const aKeys = Object.keys(aSlots).sort();
    const bKeys = Object.keys(bSlots).sort();
    if (aKeys.length !== bKeys.length) return false;
    if (!aKeys.every((k, i) => k === bKeys[i])) return false;
    for (const key of aKeys) {
        const ac = aSlots[key];
        const bc = bSlots[key];
        if (ac.length !== bc.length) return false;
        if (!ac.every((c, i) => mathNodesEqual(c, bc[i]))) return false;
    }
    return true;
}

/**
 * Tries to match `pattern` against `node`, populating `bindings`.
 * Wildcards capture any subtree; consistent bindings are enforced.
 * Returns true on success.
 */
function matchPattern(pattern: MathNode, node: MathNode, bindings: Map<string, MathNode>): boolean {
    if (pattern.type === 'wildcard') {
        const varName = pattern.value!;
        const existing = bindings.get(varName);
        if (existing !== undefined) {
            return mathNodesEqual(existing, node);
        }
        bindings.set(varName, node);
        return true;
    }
    if (pattern.type !== node.type || pattern.value !== node.value) return false;
    const patternSlots = pattern.slots ?? {};
    const nodeSlots = node.slots ?? {};
    const patternKeys = Object.keys(patternSlots).sort();
    const nodeKeys = Object.keys(nodeSlots).sort();
    if (patternKeys.length !== nodeKeys.length) return false;
    if (!patternKeys.every((k, i) => k === nodeKeys[i])) return false;
    for (const key of patternKeys) {
        const pc = patternSlots[key];
        const nc = nodeSlots[key];
        if (pc.length !== nc.length) return false;
        for (let i = 0; i < pc.length; i++) {
            if (!matchPattern(pc[i], nc[i], bindings)) return false;
        }
    }
    return true;
}

/** Substitutes wildcards in `template` with their bound subtrees. */
function instantiate(template: MathNode, bindings: Map<string, MathNode>): MathNode {
    if (template.type === 'wildcard') {
        return bindings.get(template.value!)!;
    }
    const result: MathNode = { type: template.type };
    if (template.value !== undefined) result.value = template.value;
    if (template.slots) {
        result.slots = {};
        for (const [key, children] of Object.entries(template.slots)) {
            result.slots[key] = children.map((c) => instantiate(c, bindings));
        }
    }
    return result;
}

/** Returns a new tree with the node at `path` replaced by `replacement`. */
function replaceAtPath(root: MathNode, path: number[], replacement: MathNode): MathNode {
    if (path.length === 0) return replacement;
    const [head, ...rest] = path;
    const slots = root.slots ?? {};
    const slotKeys = Object.keys(slots).sort();
    const newSlots: Record<string, MathNode[]> = {};
    let flatIndex = 0;
    for (const key of slotKeys) {
        const children = slots[key];
        const newChildren: MathNode[] = [];
        for (let i = 0; i < children.length; i++) {
            newChildren.push(flatIndex === head ? replaceAtPath(children[i], rest, replacement) : children[i]);
            flatIndex++;
        }
        newSlots[key] = newChildren;
    }
    return { type: root.type, value: root.value, slots: newSlots };
}

/**
 * Applies a rewrite rule at `path` in `tree`.
 * Returns the new tree on success, or `undefined` if the pattern does not match
 * or any of the supplied constraints rejects the bindings.
 *
 * `direction` selects forward (pattern → template) or reverse (template → pattern) application.
 * `ruleDirection` lets the caller request the engine reject REVERSE on a FORWARD_ONLY rule.
 */
export function applyRule(
    tree: MathNode,
    path: number[],
    pattern: MathNode,
    template: MathNode,
    constraints: RuleConstraint[] = [],
    direction: StepDirection = 'FORWARD',
    ruleDirection: RuleDirection = 'BIDIRECTIONAL',
): MathNode | undefined {
    if (direction === 'REVERSE' && ruleDirection !== 'BIDIRECTIONAL') return undefined;
    const patternSide = direction === 'REVERSE' ? template : pattern;
    const templateSide = direction === 'REVERSE' ? pattern : template;
    const target = nodeAtPath(tree, path);
    const bindings = new Map<string, MathNode>();
    if (!matchPattern(patternSide, target, bindings)) return undefined;
    for (const constraint of constraints) {
        if (!evaluateConstraint(constraint, bindings)) return undefined;
    }
    const replacement = instantiate(templateSide, bindings);
    return replaceAtPath(tree, path, replacement);
}

/**
 * Returns a structurally-equivalent tree with terminal values canonicalised:
 * numbers are stripped of trailing zeros (so {@code "0.0"}, {@code "-0"}, {@code "00"} all become {@code "0"});
 * variable / wildcard names are trimmed.
 * Mirrors `MathNodes.normalize` on the backend.
 */
export function normalize(node: MathNode | undefined): MathNode | undefined {
    if (!node) return node;
    if (node.type === 'number') {
        const v = normalizeNumberLiteral(node.value);
        return v === undefined ? { type: node.type } : { type: node.type, value: v };
    }
    if (node.type === 'variable' || node.type === 'wildcard') {
        const v = node.value?.trim();
        return v === undefined ? { type: node.type } : { type: node.type, value: v };
    }
    if (!node.slots || Object.keys(node.slots).length === 0) {
        return node.value === undefined ? { type: node.type } : { type: node.type, value: node.value };
    }
    const newSlots: Record<string, MathNode[]> = {};
    for (const key of Object.keys(node.slots)) {
        newSlots[key] = node.slots[key].map((c) => normalize(c)!);
    }
    return node.value === undefined ? { type: node.type, slots: newSlots } : { type: node.type, value: node.value, slots: newSlots };
}

/**
 * Throws if any node in the tree has type `'wildcard'`.
 * Wildcards are valid only inside rule definitions; an instructor-authored or
 * student-submitted tree containing one would let the matcher bind a metavariable
 * to a metavariable, which is nonsense.
 */
export function assertWildcardFree(node: MathNode | undefined): void {
    if (!node) return;
    if (node.type === 'wildcard') {
        throw new Error('Wildcard nodes are not allowed in submissions or exercise definitions');
    }
    if (node.slots) {
        for (const children of Object.values(node.slots)) {
            for (const child of children) assertWildcardFree(child);
        }
    }
}

function normalizeNumberLiteral(value: string | undefined): string | undefined {
    if (value === undefined) return value;
    const trimmed = value.trim();
    if (trimmed === '') return value;
    // accept the same numeric forms the backend BigDecimal does
    if (!/^[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?$/.test(trimmed)) {
        return value;
    }
    const num = Number(trimmed);
    if (!Number.isFinite(num)) return value;
    if (num === 0) return '0';
    // strip trailing zeros without losing precision for moderate-size numbers
    let plain = trimmed.startsWith('+') ? trimmed.slice(1) : trimmed;
    if (plain.includes('e') || plain.includes('E')) {
        plain = num.toString();
    }
    if (plain.includes('.')) {
        plain = plain.replace(/0+$/, '').replace(/\.$/, '');
    }
    if (plain === '-0') plain = '0';
    return plain;
}

export function getAllPossibleRewrites(tree: MathNode, pattern: MathNode, template: MathNode): MathNode[] {
    const results: MathNode[] = [];

    function recurse(path: number[]): void {
        const node = nodeAtPath(tree, path);
        const bindings = new Map<string, MathNode>();
        if (matchPattern(pattern, node, bindings)) {
            results.push(replaceAtPath(tree, path, instantiate(template, bindings)));
        }
        flatChildren(node).forEach((_, i) => recurse([...path, i]));
    }

    recurse([]);
    return results;
}

export function verifyTransformation(prev: MathNode, current: MathNode, pattern: MathNode, template: MathNode): boolean {
    return getAllPossibleRewrites(prev, pattern, template).some((r) => mathNodesEqual(r, current));
}

export function isTautology(tree: MathNode): boolean {
    if (tree.type !== 'equality') return false;
    const left = tree.slots?.['left']?.[0];
    const right = tree.slots?.['right']?.[0];
    return !!left && !!right && mathNodesEqual(left, right);
}

/**
 * AC-normalised form: chains of `+` or `·` are flattened, their operands sorted by a stable canonical key, and
 * rebuilt as a left-associative binary tree. Used only for equality comparisons when the exercise has
 * {@code acNormalization} enabled — submission and exercise trees are never persisted in normalised form.
 * Mirrors `MathNodes.normalizeAC` on the backend.
 */
export function normalizeAC(node: MathNode | undefined): MathNode | undefined {
    if (!node) return node;
    if (!node.slots || Object.keys(node.slots).length === 0) return node;
    if (node.type === 'add' || node.type === 'mul') {
        const operands: MathNode[] = [];
        flattenChain(node, node.type, operands);
        const normalised = operands.map((c) => normalizeAC(c)!) as MathNode[];
        normalised.sort((a, b) => subtreeKey(a).localeCompare(subtreeKey(b)));
        return rebuildLeftAssoc(node.type, normalised);
    }
    const newSlots: Record<string, MathNode[]> = {};
    for (const key of Object.keys(node.slots)) {
        newSlots[key] = node.slots[key].map((c) => normalizeAC(c)!) as MathNode[];
    }
    return node.value === undefined ? { type: node.type, slots: newSlots } : { type: node.type, value: node.value, slots: newSlots };
}

/** Equality with optional AC normalisation. */
export function equalsAC(a: MathNode | undefined, b: MathNode | undefined, ac: boolean): boolean {
    if (!ac) return !!a && !!b && mathNodesEqual(a, b);
    const na = normalizeAC(a);
    const nb = normalizeAC(b);
    return !!na && !!nb && mathNodesEqual(na, nb);
}

function flattenChain(node: MathNode, type: string, out: MathNode[]): void {
    if (node.type === type && node.slots) {
        for (const k of Object.keys(node.slots).sort()) {
            for (const child of node.slots[k]) flattenChain(child, type, out);
        }
    } else {
        out.push(node);
    }
}

function rebuildLeftAssoc(type: string, operands: MathNode[]): MathNode {
    if (operands.length === 0) return { type };
    let acc = operands[0];
    for (let i = 1; i < operands.length; i++) {
        acc = { type, slots: { left: [acc], right: [operands[i]] } };
    }
    return acc;
}

/**
 * Coarse tree distance between two trees: cardinality of the multiset symmetric difference of subtrees.
 * Mirrors `MathNodeDistance.distance` on the backend. Zero iff the trees are structurally equal.
 */
export function distance(a: MathNode | undefined, b: MathNode | undefined): number {
    if (!a && !b) return 0;
    if (!a) return size(b);
    if (!b) return size(a);
    if (mathNodesEqual(a, b)) return 0;
    const ca = new Map<string, number>();
    const cb = new Map<string, number>();
    collectSubtrees(a, ca);
    collectSubtrees(b, cb);
    const keys = new Set<string>([...ca.keys(), ...cb.keys()]);
    let total = 0;
    for (const k of keys) total += Math.abs((ca.get(k) ?? 0) - (cb.get(k) ?? 0));
    return total;
}

/** Number of nodes in the tree (1 per node, recursive). */
export function size(node: MathNode | undefined): number {
    if (!node) return 0;
    let count = 1;
    if (node.slots) {
        for (const children of Object.values(node.slots)) {
            for (const c of children) count += size(c);
        }
    }
    return count;
}

/** Stable key for a MathNode subtree (JSON with sorted slot keys), used to multiset-compare subtrees. */
function subtreeKey(node: MathNode): string {
    if (!node.slots || Object.keys(node.slots).length === 0) {
        return JSON.stringify({ t: node.type, v: node.value ?? null });
    }
    const slotKeys = Object.keys(node.slots).sort();
    const parts: Record<string, string[]> = {};
    for (const k of slotKeys) {
        parts[k] = node.slots[k].map(subtreeKey);
    }
    return JSON.stringify({ t: node.type, v: node.value ?? null, s: parts });
}

function collectSubtrees(node: MathNode, counts: Map<string, number>): void {
    const key = subtreeKey(node);
    counts.set(key, (counts.get(key) ?? 0) + 1);
    if (node.slots) {
        for (const children of Object.values(node.slots)) {
            for (const c of children) collectSubtrees(c, counts);
        }
    }
}
