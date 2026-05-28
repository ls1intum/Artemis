import { describe, expect, it } from 'vitest';
import { MathNode, applyRule, assertWildcardFree, distance, equalsAC, isTautology, mathNodesEqual, normalize, normalizeAC, size } from 'app/proof/shared/entities/math-node.model';
import { RuleConstraint } from 'app/proof/shared/entities/rule-constraint.model';

const num = (v: string): MathNode => ({ type: 'number', value: v });
const variable = (v: string): MathNode => ({ type: 'variable', value: v });
const wc = (v: string): MathNode => ({ type: 'wildcard', value: v });
const add = (l: MathNode, r: MathNode): MathNode => ({ type: 'add', slots: { left: [l], right: [r] } });
const mul = (l: MathNode, r: MathNode): MathNode => ({ type: 'mul', slots: { left: [l], right: [r] } });
const frac = (n: MathNode, d: MathNode): MathNode => ({ type: 'fraction', slots: { numerator: [n], denominator: [d] } });
const paren = (c: MathNode): MathNode => ({ type: 'parentheses', slots: { content: [c] } });
const neg = (inner: MathNode): MathNode => ({ type: 'negation', slots: { inner: [inner] } });
const eq = (l: MathNode, r: MathNode): MathNode => ({ type: 'equality', slots: { left: [l], right: [r] } });

describe('math-node engine — frontend mirror', () => {
    describe('applyRule', () => {
        it('applies add_zero_left at root', () => {
            // pattern: 0 + a, template: a
            const pattern = add(num('0'), wc('a'));
            const template = wc('a');
            const tree = add(num('0'), variable('x'));
            expect(applyRule(tree, [], pattern, template)).toEqual(variable('x'));
        });

        it('add_comm applied twice is identity', () => {
            const pattern = add(wc('a'), wc('b'));
            const template = add(wc('b'), wc('a'));
            const tree = add(variable('a'), variable('b'));
            const once = applyRule(tree, [], pattern, template)!;
            const twice = applyRule(once, [], pattern, template)!;
            expect(mathNodesEqual(twice, tree)).toBe(true);
        });

        it('mul_zero_left collapses any rhs to 0', () => {
            const pattern = mul(num('0'), wc('a'));
            const template = num('0');
            const tree = mul(num('0'), add(variable('a'), variable('b')));
            expect(applyRule(tree, [], pattern, template)).toEqual(num('0'));
        });

        it('paren_unwrap applies at a non-root path', () => {
            // path [0] = left slot of add (alphabetical: left < right)
            const pattern = paren(wc('x'));
            const template = wc('x');
            const tree = add(paren(variable('x')), variable('y'));
            expect(applyRule(tree, [0], pattern, template)).toEqual(add(variable('x'), variable('y')));
        });

        it('returns undefined on pattern mismatch', () => {
            const pattern = add(num('0'), wc('a'));
            const template = wc('a');
            const tree = add(variable('x'), num('0')); // wrong order
            expect(applyRule(tree, [], pattern, template)).toBeUndefined();
        });

        it('enforces non-linear binding consistency (cancel rule)', () => {
            // pattern: (c · a) / (c · b) — same wildcard c on both sides
            const pattern = frac(mul(wc('c'), wc('a')), mul(wc('c'), wc('b')));
            const template = frac(wc('a'), wc('b'));

            const matching = frac(mul(variable('x'), variable('a')), mul(variable('x'), variable('b')));
            expect(applyRule(matching, [], pattern, template)).toEqual(frac(variable('a'), variable('b')));

            const nonMatching = frac(mul(variable('x'), variable('a')), mul(variable('y'), variable('b')));
            expect(applyRule(nonMatching, [], pattern, template)).toBeUndefined();
        });

        it('side condition c != 0 rejects zero factor in cancel rule', () => {
            const pattern = frac(mul(wc('c'), wc('a')), mul(wc('c'), wc('b')));
            const template = frac(wc('a'), wc('b'));
            const constraints: RuleConstraint[] = [{ type: 'NOT_EQUAL_TO_CONSTANT', wildcardName: 'c', value: num('0') }];

            const zeroFactor = frac(mul(num('0'), variable('a')), mul(num('0'), variable('b')));
            expect(applyRule(zeroFactor, [], pattern, template, constraints)).toBeUndefined();

            const nonZeroFactor = frac(mul(num('2'), variable('a')), mul(num('2'), variable('b')));
            expect(applyRule(nonZeroFactor, [], pattern, template, constraints)).toEqual(frac(variable('a'), variable('b')));
        });
    });

    describe('normalize', () => {
        it('collapses numeric forms', () => {
            expect(normalize(num('0.0'))).toEqual(num('0'));
            expect(normalize(num('00'))).toEqual(num('0'));
            expect(normalize(num('-0'))).toEqual(num('0'));
            expect(normalize(num('1.50'))).toEqual(num('1.5'));
        });

        it('preserves non-numeric values', () => {
            expect(normalize(variable('x'))).toEqual(variable('x'));
            expect(normalize(num('notANumber'))).toEqual(num('notANumber'));
        });

        it('recurses through slots', () => {
            expect(normalize(add(num('0.0'), variable(' x ')))).toEqual(add(num('0'), variable('x')));
        });

        it('returns undefined unchanged', () => {
            expect(normalize(undefined)).toBeUndefined();
        });
    });

    describe('assertWildcardFree', () => {
        it('rejects a wildcard at the root', () => {
            expect(() => assertWildcardFree(wc('a'))).toThrow(/Wildcard/);
        });

        it('rejects a wildcard nested deep', () => {
            expect(() => assertWildcardFree(add(variable('x'), mul(num('1'), wc('c'))))).toThrow(/Wildcard/);
        });

        it('accepts a wildcard-free tree', () => {
            expect(() => assertWildcardFree(add(variable('x'), mul(num('1'), variable('y'))))).not.toThrow();
        });

        it('accepts undefined', () => {
            expect(() => assertWildcardFree(undefined)).not.toThrow();
        });
    });

    describe('rule direction', () => {
        it('REVERSE on a BIDIRECTIONAL rule swaps pattern and template', () => {
            // add_assoc: (a + b) + c ↔ a + (b + c)
            const pattern = add(add(wc('a'), wc('b')), wc('c'));
            const template = add(wc('a'), add(wc('b'), wc('c')));
            const grouped = add(variable('a'), add(variable('b'), variable('c'))); // RHS shape
            const result = applyRule(grouped, [], pattern, template, [], 'REVERSE', 'BIDIRECTIONAL');
            expect(result).toEqual(add(add(variable('a'), variable('b')), variable('c')));
        });

        it('REVERSE on a FORWARD_ONLY rule returns undefined', () => {
            // add_zero_left: 0 + a → a (forward only)
            const pattern = add(num('0'), wc('a'));
            const template = wc('a');
            expect(applyRule(variable('x'), [], pattern, template, [], 'REVERSE', 'FORWARD_ONLY')).toBeUndefined();
        });

        it('FORWARD on a FORWARD_ONLY rule still works', () => {
            const pattern = add(num('0'), wc('a'));
            const template = wc('a');
            expect(applyRule(add(num('0'), variable('x')), [], pattern, template, [], 'FORWARD', 'FORWARD_ONLY')).toEqual(variable('x'));
        });
    });

    describe('distributivity and negation', () => {
        it('mul_distrib expands forward', () => {
            const pattern = mul(wc('a'), add(wc('b'), wc('c')));
            const template = add(mul(wc('a'), wc('b')), mul(wc('a'), wc('c')));
            const tree = mul(variable('x'), add(variable('y'), variable('z')));
            expect(applyRule(tree, [], pattern, template, [], 'FORWARD', 'BIDIRECTIONAL')).toEqual(add(mul(variable('x'), variable('y')), mul(variable('x'), variable('z'))));
        });

        it('mul_distrib factors in reverse', () => {
            const pattern = mul(wc('a'), add(wc('b'), wc('c')));
            const template = add(mul(wc('a'), wc('b')), mul(wc('a'), wc('c')));
            const expanded = add(mul(variable('x'), variable('y')), mul(variable('x'), variable('z')));
            expect(applyRule(expanded, [], pattern, template, [], 'REVERSE', 'BIDIRECTIONAL')).toEqual(mul(variable('x'), add(variable('y'), variable('z'))));
        });

        it('neg_neg roundtrip', () => {
            const pattern = neg(neg(wc('a')));
            const template = wc('a');
            const collapsed = applyRule(neg(neg(variable('y'))), [], pattern, template, [], 'FORWARD', 'BIDIRECTIONAL')!;
            expect(collapsed).toEqual(variable('y'));
            const reintroduced = applyRule(collapsed, [], pattern, template, [], 'REVERSE', 'BIDIRECTIONAL')!;
            expect(reintroduced).toEqual(neg(neg(variable('y'))));
        });
    });

    describe('isTautology', () => {
        it('returns true for an equality with structurally equal sides', () => {
            expect(isTautology(eq(variable('x'), variable('x')))).toBe(true);
        });

        it('returns false for an equality with different sides', () => {
            expect(isTautology(eq(variable('x'), variable('y')))).toBe(false);
        });

        it('returns false for a non-equality root', () => {
            expect(isTautology(add(variable('x'), variable('x')))).toBe(false);
        });
    });

    describe('distance', () => {
        it('returns 0 for structurally equal trees', () => {
            expect(distance(add(variable('a'), variable('b')), add(variable('a'), variable('b')))).toBe(0);
        });

        it('is symmetric', () => {
            const a = add(num('0'), variable('x'));
            const b = variable('x');
            expect(distance(a, b)).toBe(distance(b, a));
        });

        it('decreases when applying add_zero_left toward target', () => {
            const source = add(num('0'), variable('x'));
            const target = variable('x');
            const before = distance(source, target);
            const after = distance(target, target);
            expect(after).toBeLessThan(before);
            expect(after).toBe(0);
        });

        it('stays the same on commutativity (no progress)', () => {
            const source = add(variable('a'), variable('b'));
            const swapped = add(variable('b'), variable('a'));
            const target = variable('x');
            expect(distance(source, target)).toBe(distance(swapped, target));
        });
    });

    describe('AC normalisation', () => {
        it('commuted add normalises to the same form', () => {
            expect(normalizeAC(add(variable('a'), variable('b')))).toEqual(normalizeAC(add(variable('b'), variable('a'))));
        });

        it('different associativity normalises to the same form', () => {
            const leftAssoc = add(add(variable('c'), variable('a')), variable('b'));
            const rightAssoc = add(variable('b'), add(variable('a'), variable('c')));
            expect(normalizeAC(leftAssoc)).toEqual(normalizeAC(rightAssoc));
        });

        it('equalsAC respects the ac flag', () => {
            const ab = add(variable('a'), variable('b'));
            const ba = add(variable('b'), variable('a'));
            expect(equalsAC(ab, ba, true)).toBe(true);
            expect(equalsAC(ab, ba, false)).toBe(false);
        });

        it('AC normalisation makes a + b = b + a a tautology', () => {
            const goal: MathNode = { type: 'equality', slots: { left: [add(variable('a'), variable('b'))], right: [add(variable('b'), variable('a'))] } };
            expect(isTautology(normalizeAC(goal)!)).toBe(true);
            expect(isTautology(goal)).toBe(false);
        });
    });

    describe('size', () => {
        it('counts every node in the tree', () => {
            expect(size(variable('x'))).toBe(1);
            expect(size(add(variable('x'), num('0')))).toBe(3);
            expect(size(add(add(variable('x'), num('0')), variable('y')))).toBe(5);
        });
    });

    describe('equation-mode reduction (drives the EQUATION grader)', () => {
        it('reaches a tautology by applying add_comm on one side', () => {
            // goal: a + b = b + a
            const goal = eq(add(variable('a'), variable('b')), add(variable('b'), variable('a')));
            // path [0] = left slot of equality (alphabetical sort: left < right)
            const pattern = add(wc('a'), wc('b'));
            const template = add(wc('b'), wc('a'));
            const reduced = applyRule(goal, [0], pattern, template, [], 'FORWARD', 'BIDIRECTIONAL');
            expect(reduced).toBeDefined();
            expect(isTautology(reduced!)).toBe(true);
        });
    });

    // Parity fixtures — these MUST be kept in sync with ProofGradingServiceTest.java on the backend.
    describe('parity with backend engine', () => {
        const cases: { name: string; tree: MathNode; path: number[]; pattern: MathNode; template: MathNode; expected: MathNode | undefined }[] = [
            { name: 'add_zero_left at root', tree: add(num('0'), variable('x')), path: [], pattern: add(num('0'), wc('a')), template: wc('a'), expected: variable('x') },
            {
                name: 'paren_unwrap at [0]',
                tree: add(paren(variable('x')), variable('y')),
                path: [0],
                pattern: paren(wc('x')),
                template: wc('x'),
                expected: add(variable('x'), variable('y')),
            },
            { name: 'add_zero_left mismatch', tree: add(variable('x'), num('0')), path: [], pattern: add(num('0'), wc('a')), template: wc('a'), expected: undefined },
        ];
        for (const c of cases) {
            it(c.name, () => {
                expect(applyRule(c.tree, c.path, c.pattern, c.template)).toEqual(c.expected);
            });
        }
    });
});
