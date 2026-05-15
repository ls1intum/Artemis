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

    const needsParens =
        parentPrecedence >= 0 && (myPrec === -Infinity || (isRightChild ? myPrec <= parentPrecedence : myPrec < parentPrecedence));

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
        default:
            // Handles future BINARY_INFIX node types added via the block registry
            if (desc?.layoutCategory === 'BINARY_INFIX' && desc.latexSymbol) {
                inner = `${renderChild(node.slots?.['left']?.[0], 'left')} ${desc.latexSymbol} ${renderChild(node.slots?.['right']?.[0], 'right')}`;
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
 * Returns the new tree on success, or `undefined` if the pattern does not match.
 */
export function applyRule(tree: MathNode, path: number[], pattern: MathNode, template: MathNode): MathNode | undefined {
    const target = nodeAtPath(tree, path);
    const bindings = new Map<string, MathNode>();
    if (!matchPattern(pattern, target, bindings)) return undefined;
    const replacement = instantiate(template, bindings);
    return replaceAtPath(tree, path, replacement);
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
