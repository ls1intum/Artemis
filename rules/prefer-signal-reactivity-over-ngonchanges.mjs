import { ESLintUtils } from '@typescript-eslint/utils';

const createRule = ESLintUtils.RuleCreator(() => '');

/**
 * @fileoverview
 * Discourage `ngOnChanges` on signal-based components and directives in favour of
 * `computed()` (for derived state) and `effect()` (for genuine side effects).
 *
 * ## Why this rule exists
 *
 * Contrary to a common belief that carried over from Angular 17–18, in **Angular 21**
 * `ngOnChanges` **does** fire for signal inputs (`input()` / `input.required()` / `model()`),
 * with a full `SimpleChanges` record, and the first `ngOnChanges` still runs before `ngOnInit`.
 * (Confirmed against `@angular/core` 21.x: input writes for signal-based inputs are routed through
 * `NgOnChangesFeature` → `ngOnChangesSetInput`, which records a `SimpleChange` and applies the value
 * to the input signal, and `ngOnChanges` is registered as a per-change-detection pre-order check hook.)
 *
 * So `ngOnChanges` is **not** dead code and **not** a correctness bug — but the Angular docs
 * still recommend `computed`/`effect` over it for signal-based components, and a signal-first code
 * base is more consistent and less error-prone (no manually-maintained derived fields that can go
 * stale). This rule is therefore a **warning**, not an error, and is scoped (in `eslint.config.mjs`)
 * to modules that are already fully migrated to signals and currently free of `ngOnChanges`.
 *
 * ## What to use instead
 *
 *  - Deriving a value from inputs/state            → `computed()` (lazy, memoised, cannot go stale)
 *  - A derived value that is also locally settable → `linkedSignal()`
 *  - A genuine side effect (subscribe, imperative / 3rd-party API, DOM)
 *                                                   → `effect()` — sparingly; it is the *last* API to reach for
 *
 * ## When `ngOnChanges` is still the right tool (use the escape hatch below)
 *
 *  - You need `SimpleChanges.previousValue` or `SimpleChange.isFirstChange()` (signals do not expose these directly).
 *  - The logic must run **before** child components initialise. `ngOnChanges` runs before `ngOnInit`
 *    and before child rendering; an `effect()` runs afterwards, which can change behaviour.
 *
 * In those cases keep `ngOnChanges` and silence this warning on the line with a short justification:
 *
 * ```ts
 * // eslint-disable-next-line localRules/prefer-signal-reactivity-over-ngonchanges -- needs SimpleChanges.previousValue
 * ngOnChanges(changes: SimpleChanges) { ... }
 * ```
 *
 * The full rationale and a decision table live in
 * `documentation/docs/developer/guidelines/client-development.mdx`.
 *
 * Examples **flagged** by this rule (inside an `@Component` / `@Directive`):
 * ```ts
 * @Component({ ... })
 * export class ExampleComponent {
 *   value = input.required<number>();
 *   ngOnChanges() { this.recompute(); }   // ⚠️ prefer computed()/effect()
 * }
 * ```
 *
 * Example **not flagged** (the idiomatic replacement):
 * ```ts
 * @Component({ ... })
 * export class ExampleComponent {
 *   value = input.required<number>();
 *   doubled = computed(() => this.value() * 2);
 * }
 * ```
 */

const ANGULAR_CLASS_DECORATORS = new Set(['Component', 'Directive']);

/** Resolves a decorator's identifier name, handling both `@Component(...)` and a bare `@Component`. */
function getDecoratorName(decorator) {
    const expression = decorator.expression;
    if (expression.type === 'CallExpression' && expression.callee.type === 'Identifier') {
        return expression.callee.name;
    }
    if (expression.type === 'Identifier') {
        return expression.name;
    }
    return undefined;
}

/** True if the class carries an `@Component` or `@Directive` decorator. */
function isAngularComponentOrDirective(classNode) {
    const decorators = classNode.decorators ?? [];
    return decorators.some((decorator) => ANGULAR_CLASS_DECORATORS.has(getDecoratorName(decorator)));
}

export default createRule({
    name: 'prefer-signal-reactivity-over-ngonchanges',
    meta: {
        type: 'suggestion',
        docs: {
            description:
                'Discourage `ngOnChanges` on signal-based components/directives. Prefer `computed()` for derived state and `effect()` for genuine side effects; keep `ngOnChanges` only when `SimpleChanges.previousValue` / `isFirstChange()` or pre-child-initialisation timing is genuinely required (silence the rule on that line with a justification).',
        },
        messages: {
            preferSignalReactivity:
                "Avoid 'ngOnChanges' in signal-based components. Prefer computed() for derived state, and effect() only for genuine side effects. ngOnChanges still works in Angular 21 (it fires for signal inputs), but computed()/effect() are the idiomatic, consistent choice. If you specifically need SimpleChanges.previousValue / isFirstChange(), or logic that must run before child components initialise, keep ngOnChanges and disable this rule for the line with a short justification.",
        },
        schema: [],
    },
    defaultOptions: [],
    create(context) {
        const report = (node) => {
            // node.parent is the ClassBody; node.parent.parent is the class declaration/expression.
            const classNode = node.parent?.parent;
            if (!classNode || (classNode.type !== 'ClassDeclaration' && classNode.type !== 'ClassExpression')) {
                return;
            }
            if (!isAngularComponentOrDirective(classNode)) {
                return;
            }
            context.report({ node: node.key, messageId: 'preferSignalReactivity' });
        };

        return {
            // Covers both `ngOnChanges() {}` (method) and the rare `ngOnChanges = (changes) => {}` (property).
            "MethodDefinition[key.name='ngOnChanges']": report,
            "PropertyDefinition[key.name='ngOnChanges']": report,
        };
    },
});
