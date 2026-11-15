import { ESLintUtils } from '@typescript-eslint/utils';

const createRule = ESLintUtils.RuleCreator(() => '');

/**
 * @fileoverview
 * Ban `ngOnChanges` in client code in favour of `computed()` (for derived state) and `effect()`
 * (for genuine side effects).
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
 * stale).
 *
 * The client is now completely free of the hook (PR #12951), so the rule is configured as an **error**
 * to keep it that way: `pnpm run lint` runs without `--max-warnings`, so at warning severity a
 * reintroduced hook would pass CI unnoticed.
 *
 * ## Why it does not require an `@Component` / `@Directive` decorator
 *
 * Angular invokes `ngOnChanges` on the component *instance*, so a hook inherited from an undecorated
 * base class runs exactly like one declared on the component itself. Requiring the decorator would
 * therefore leave a hole big enough to smuggle the hook back in through any of the client's abstract
 * base classes. The rule matches the member wherever it is declared; interface declarations
 * (`TSMethodSignature`) and object-literal properties (`Property`) are a different AST node and stay
 * unmatched, so `OnChanges` type declarations and literal-based mocks are unaffected.
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
 * In those cases keep `ngOnChanges` and silence this rule on the line with a short justification:
 *
 * ```ts
 * // eslint-disable-next-line localRules/prefer-signal-reactivity-over-ngonchanges -- needs SimpleChanges.previousValue
 * ngOnChanges(changes: SimpleChanges) { ... }
 * ```
 *
 * The full rationale and a decision table live in
 * `documentation/docs/developer/guidelines/client-development.mdx`.
 *
 * Examples **flagged** by this rule:
 * ```ts
 * @Component({ ... })
 * export class ExampleComponent {
 *   value = input.required<number>();
 *   ngOnChanges() { this.recompute(); }   // ❌ prefer computed()/effect()
 * }
 *
 * // Also flagged: an undecorated base class a component extends — Angular still calls the hook.
 * export abstract class ExampleBase {
 *   ngOnChanges() { this.recompute(); }   // ❌
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

const HOOK_NAME = 'ngOnChanges';

/**
 * Whether a class-member key names the `ngOnChanges` lifecycle hook.
 *
 * All of these declare the very same prototype member, and Angular invokes each of them identically, so the ban has
 * to recognise every spelling — matching only `key.name` would let the quoted and computed forms straight through:
 *
 * ```ts
 * ngOnChanges() {}        // Identifier
 * 'ngOnChanges'() {}      // Literal (quoted, not computed)
 * ['ngOnChanges']() {}    // Literal (computed)
 * [`ngOnChanges`]() {}    // TemplateLiteral without substitutions
 * ```
 *
 * A key computed from a variable (`[hookName]() {}`) cannot be resolved statically and is therefore out of reach for
 * any lint rule; nobody writes a lifecycle hook that way by accident, and doing it deliberately to dodge this rule is
 * indistinguishable from disabling it.
 */
function isNgOnChangesKey(key) {
    switch (key?.type) {
        case 'Identifier':
            return key.name === HOOK_NAME;
        case 'Literal':
            return key.value === HOOK_NAME;
        case 'TemplateLiteral':
            return key.expressions.length === 0 && key.quasis[0]?.value.cooked === HOOK_NAME;
        default:
            return false;
    }
}

export default createRule({
    name: 'prefer-signal-reactivity-over-ngonchanges',
    meta: {
        type: 'suggestion',
        docs: {
            description:
                'Ban `ngOnChanges` in client code. Prefer `computed()` for derived state and `effect()` for genuine side effects; keep `ngOnChanges` only when `SimpleChanges.previousValue` / `isFirstChange()` or pre-child-initialisation timing is genuinely required (silence the rule on that line with a justification).',
        },
        messages: {
            preferSignalReactivity:
                "Avoid 'ngOnChanges'. Prefer computed() for derived state, and effect() only for genuine side effects. ngOnChanges still works in Angular 21 (it fires for signal inputs), but computed()/effect() are the idiomatic, consistent choice, and the client is otherwise free of the hook. This also applies to undecorated base classes, because Angular calls an inherited ngOnChanges just like one declared on the component. If you specifically need SimpleChanges.previousValue / isFirstChange(), or logic that must run before child components initialise, keep ngOnChanges and disable this rule for the line with a short justification.",
        },
        schema: [],
    },
    defaultOptions: [],
    create(context) {
        const report = (node) => {
            if (!isNgOnChangesKey(node.key)) {
                return;
            }
            // Static members are never Angular lifecycle hooks (Angular only invokes the instance `ngOnChanges`),
            // so an unrelated `static ngOnChanges` helper must not be flagged.
            if (node.static) {
                return;
            }
            // node.parent is the ClassBody; node.parent.parent is the class declaration/expression. No decorator
            // check: Angular calls the hook on the instance, so one inherited from an undecorated base class runs
            // just the same — requiring `@Component`/`@Directive` would leave that route open.
            const classNode = node.parent?.parent;
            if (!classNode || (classNode.type !== 'ClassDeclaration' && classNode.type !== 'ClassExpression')) {
                return;
            }
            context.report({ node: node.key, messageId: 'preferSignalReactivity' });
        };

        return {
            // Every class member is visited and its key resolved by isNgOnChangesKey(), rather than filtering on
            // `key.name` in the selector: that would only match the plain identifier form and silently let the
            // string-literal and computed-literal spellings through.
            MethodDefinition: report,
            PropertyDefinition: report,
        };
    },
});
