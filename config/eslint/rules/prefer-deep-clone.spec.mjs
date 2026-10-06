import { describe, it } from 'vitest';
import rule from './prefer-deep-clone.mjs';
import { createTypeScriptRuleTester } from './rule-tester.mjs';

const ruleTester = createTypeScriptRuleTester();

// The rule guards on the file path, so every case must name one — RuleTester's default `file.ts` would be
// treated as non-client code and produce no reports.
const prod = 'src/main/webapp/app/course/manage/course-update.component.ts';
const spec = 'src/main/webapp/app/course/manage/course-update.component.spec.ts';

describe('prefer-deep-clone', () => {
    it('forbids object spread, Object.assign and structuredClone in production client code', () => {
        ruleTester.run('prefer-deep-clone', rule, {
            valid: [
                // The sanctioned helpers.
                { code: 'const copy = deepClone(course);', filename: prod },
                { code: 'const copy = cloneWith(course, { title: "New" });', filename: prod },
                { code: 'const course = hydrate(new Course(), dto);', filename: prod },
                // Array spread is the documented way to append immutably.
                { code: 'const next = [...items, newItem];', filename: prod },
                { code: 'const merged = [...a, ...b];', filename: prod },
                // Call spread passes arguments; it copies nothing.
                { code: 'const max = Math.max(...values);', filename: prod },
                { code: 'fn(...args);', filename: prod },
                // Object rest in destructuring reads properties rather than copying an object into a new one.
                // It is a RestElement in an ObjectPattern, not a SpreadElement in an ObjectExpression.
                { code: 'const { id, ...rest } = post;', filename: prod },
                { code: 'function f({ a, ...rest }) { return rest; }', filename: prod },
                // A plain object literal with no spread.
                { code: 'const dto = { title: "x", points: 10 };', filename: prod },
                // Other Object.* statics are untouched.
                { code: 'const keys = Object.keys(course);', filename: prod },
                { code: 'const entries = Object.entries(course);', filename: prod },
                // Specs may build fixtures freely.
                { code: 'const fixture = { ...baseCourse, id: 7 };', filename: spec },
                { code: 'const fixture = Object.assign({}, baseCourse);', filename: spec },
                { code: 'const fixture = structuredClone(baseCourse);', filename: spec },
                // Non-client code is out of scope entirely.
                { code: 'const copy = { ...config };', filename: 'src/test/javascript/helper.ts' },
            ],
            invalid: [
                // Pure shallow clone.
                { code: 'const copy = { ...course };', filename: prod, errors: [{ messageId: 'objectSpread' }] },
                // Clone plus override — by far the most common shape.
                { code: 'const copy = { ...course, title: "New" };', filename: prod, errors: [{ messageId: 'objectSpread' }] },
                // Defaults-then-override: the spread wins here, so it is not a plain cloneWith.
                { code: 'const copy = { title: "Default", ...course };', filename: prod, errors: [{ messageId: 'objectSpread' }] },
                // Two spreads in one literal are two separate copies, so two reports.
                { code: 'const merged = { ...a, ...b };', filename: prod, errors: [{ messageId: 'objectSpread' }, { messageId: 'objectSpread' }] },
                // A nested object literal is reported too, not just the outermost one.
                { code: 'const wrapped = { data: { ...course } };', filename: prod, errors: [{ messageId: 'objectSpread' }] },
                // Spread inside a signal update, the shape most call sites use.
                { code: 'this.course.update((current) => ({ ...current, title }));', filename: prod, errors: [{ messageId: 'objectSpread' }] },
                // Object.assign in each of its forms.
                { code: 'const copy = Object.assign({}, course);', filename: prod, errors: [{ messageId: 'objectAssign' }] },
                { code: 'const copy = Object.assign({}, course, { title: "New" });', filename: prod, errors: [{ messageId: 'objectAssign' }] },
                // Hydration: legitimate intent, wrong tool — hydrate() replaces it.
                { code: 'const course = Object.assign(new Course(), dto);', filename: prod, errors: [{ messageId: 'objectAssign' }] },
                // In-place mutation of an existing target, which emits no signal notification.
                { code: 'Object.assign(this.state, patch);', filename: prod, errors: [{ messageId: 'objectAssign' }] },
                // The prototype-preserving shallow-clone idiom deepClone already handles; the inner and outer
                // calls are both Object.assign, hence two reports.
                {
                    code: 'const copy = Object.assign(Object.create(Object.getPrototypeOf(c)), c, { a: Object.assign({}, c.a) });',
                    filename: prod,
                    errors: [{ messageId: 'objectAssign' }, { messageId: 'objectAssign' }],
                },
                // structuredClone, bare and via window — currently absent from production, so this is a
                // regression guard for the bug that motivated deepClone.
                { code: 'const copy = structuredClone(lecture);', filename: prod, errors: [{ messageId: 'structuredCloneCall' }] },
                { code: 'const copy = window.structuredClone(lecture);', filename: prod, errors: [{ messageId: 'structuredCloneCall' }] },
            ],
        });
    });
});
