import fs from 'node:fs';
import path from 'node:path';
import { ESLintUtils } from '@typescript-eslint/utils';

const createRule = ESLintUtils.RuleCreator(() => '');

/**
 * @fileoverview
 * Require **signal-backed state** for any mutable component/directive field that is read in the template.
 *
 * ## Why this rule exists
 *
 * The Artemis client is **zoneless** (`provideZonelessChangeDetection()`). Without zone.js, Angular only
 * re-renders when something it can observe changes: a **signal** read in a template updates, an `async` pipe
 * emits, a template event handler runs, or code explicitly calls `markForCheck()`.
 *
 * A plain (non-signal) class field that the template reads and that is **reassigned** at runtime is a silent
 * render bug waiting to happen: the moment that field is changed anywhere other than a synchronous render /
 * template event handler (e.g. inside an HTTP `subscribe`, a `setTimeout`, a promise `then`, an rxjs `tap`, or
 * — crucially — any helper method reached from one of those), the write schedules **no** change detection and
 * the view silently keeps the old value (blank page, frozen counter, stuck spinner). This already caused real
 * bugs on pages that lacked E2E coverage (instructor code editor, SSH-key details, LTI launch, …).
 *
 * Rather than try to detect *where* the write happens (a helper method called from a `subscribe` looks
 * synchronous), this rule keys off the two facts that actually matter: the field is **template-bound** and
 * **mutable**. The fix is to make it a **signal** (`signal()` + `.set()/.update()`), which always notifies the
 * zoneless scheduler. Prefer too many signals over too few — a signal whose value never changes is harmless,
 * a missing one is a silent render bug.
 *
 * ## What is (intentionally) NOT flagged
 *
 *  - Signals / `computed` / `input` / `model` / `viewChild` / `contentChild` / `toSignal` / `linkedSignal`.
 *  - Injected services (`inject(...)`).
 *  - Constants — a field that is never reassigned (incl. `readonly` icon/enum/config fields).
 *  - Fields the template never reads (Subscriptions, timer handles, internal caches/flags) — they cannot
 *    cause a render bug, so they may stay plain.
 *  - Getter/setter-over-signal facades (the getter is not a field declaration; the backing `_x` is a signal).
 *  - Deep mutations of an object field (`this.obj.prop = …`) — only direct `this.field = …` reassignment of a
 *    template-bound field marks the field mutable.
 *
 * ## Escape hatch
 *
 * A few template-bound fields genuinely cannot be a bare signal — most notably a `[(ngModel)]` / `[(x)]`
 * two-way binding target a signal cannot sit on (prefer a getter/setter-over-signal facade; for the rare
 * directive two-way like `jhiSort`'s `[(predicate)]`/`[(ascending)]` a plain field is currently required).
 * For those, silence the rule on the declaration with a short justification:
 *
 * ```ts
 * // eslint-disable-next-line localRules/prefer-signal-template-state -- [(ascending)] two-way to jhiSort; signals can't back [(x)]
 * reverse = true;
 * ```
 *
 * The full rationale lives in `documentation/docs/developer/guidelines/client-development.mdx`.
 */

const ANGULAR_CLASS_DECORATORS = new Set(['Component', 'Directive']);

// Initializer call callees that make a field signal-based (exempt).
const SIGNAL_FACTORY_IDENTIFIERS = new Set(['signal', 'computed', 'input', 'model', 'viewChild', 'viewChildren', 'contentChild', 'contentChildren', 'toSignal', 'linkedSignal']);
// `inject(...)` initializers are injected services (exempt).
const INJECT_IDENTIFIER = 'inject';
// Reactive-forms types: these manage their own change propagation (and a `[formGroup]`/`formControlName`
// binding cannot be backed by a signal), so reactive-form fields are exempt — they must NOT become signals.
const REACTIVE_FORM_TYPES = new Set(['FormGroup', 'FormControl', 'FormArray', 'FormRecord', 'AbstractControl', 'UntypedFormGroup', 'UntypedFormControl', 'UntypedFormArray']);
// Builder methods (`fb.group(...)`, `fb.control(...)`, `fb.array(...)`) that produce reactive-form controls.
const REACTIVE_FORM_BUILDER_METHODS = new Set(['group', 'control', 'array', 'record', 'nonNullable']);

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

function isAngularComponentOrDirective(classNode) {
    const decorators = classNode?.decorators ?? [];
    return decorators.some((decorator) => ANGULAR_CLASS_DECORATORS.has(getDecoratorName(decorator)));
}

/** True if a property initializer is a signal/inject factory call (so the field is exempt). */
function isExemptInitializer(init) {
    if (!init || init.type !== 'CallExpression') {
        return false;
    }
    const callee = init.callee;
    if (callee.type === 'Identifier') {
        return SIGNAL_FACTORY_IDENTIFIERS.has(callee.name) || callee.name === INJECT_IDENTIFIER;
    }
    // input.required(...) / viewChild.required(...) / model.required(...) / contentChild.required(...)
    if (callee.type === 'MemberExpression' && callee.object.type === 'Identifier' && callee.property.type === 'Identifier' && callee.property.name === 'required') {
        return SIGNAL_FACTORY_IDENTIFIERS.has(callee.object.name);
    }
    return false;
}

/** True if a property's type annotation is a reactive-forms type (FormGroup/FormControl/…). */
function hasReactiveFormTypeAnnotation(member) {
    const typeRef = member.typeAnnotation?.typeAnnotation;
    if (typeRef?.type !== 'TSTypeReference') {
        return false;
    }
    const name = typeRef.typeName;
    if (name?.type === 'Identifier') {
        return REACTIVE_FORM_TYPES.has(name.name);
    }
    // qualified name like forms.FormGroup
    if (name?.type === 'TSQualifiedName' && name.right?.type === 'Identifier') {
        return REACTIVE_FORM_TYPES.has(name.right.name);
    }
    return false;
}

/** True if an expression produces a reactive-forms control: `new FormGroup(...)` or `*.group/control/array(...)`. */
function isReactiveFormValue(node) {
    if (!node) {
        return false;
    }
    if (node.type === 'NewExpression' && node.callee.type === 'Identifier' && REACTIVE_FORM_TYPES.has(node.callee.name)) {
        return true;
    }
    if (
        node.type === 'CallExpression' &&
        node.callee.type === 'MemberExpression' &&
        node.callee.property.type === 'Identifier' &&
        REACTIVE_FORM_BUILDER_METHODS.has(node.callee.property.name)
    ) {
        return true;
    }
    return false;
}

/** Reads the component's template text (inline `template` or `templateUrl` file), or undefined. */
function getTemplateText(classNode, filename) {
    for (const decorator of classNode.decorators ?? []) {
        const expression = decorator.expression;
        if (expression.type !== 'CallExpression' || expression.callee.type !== 'Identifier' || expression.callee.name !== 'Component') {
            continue;
        }
        const arg = expression.arguments[0];
        if (arg?.type !== 'ObjectExpression') {
            continue;
        }
        for (const prop of arg.properties) {
            if (prop.type !== 'Property' || prop.key.type !== 'Identifier') {
                continue;
            }
            if (prop.key.name === 'template') {
                if (prop.value.type === 'Literal' && typeof prop.value.value === 'string') {
                    return prop.value.value;
                }
                if (prop.value.type === 'TemplateLiteral') {
                    return prop.value.quasis.map((q) => q.value.raw).join(' ');
                }
            }
            if (prop.key.name === 'templateUrl' && prop.value.type === 'Literal' && typeof prop.value.value === 'string') {
                try {
                    return fs.readFileSync(path.resolve(path.dirname(filename), prop.value.value), 'utf8');
                } catch {
                    return undefined;
                }
            }
        }
    }
    return undefined;
}

/**
 * Field names that are the direct target of a two-way binding `[(x)]="field"` (optionally `field!`).
 * A signal cannot back a `[(x)]` binding, so such a field is exempt (use a getter/setter-over-signal facade
 * if the value must still drive rendering). Only a bare-identifier target is matched; `[(ngModel)]="obj.prop"`
 * does not exempt `obj` (its reassignment still needs change detection).
 */
function templateTwoWayBoundNames(html) {
    const names = new Set();
    if (!html) {
        return names;
    }
    const cleaned = html.replace(/<!--[\s\S]*?-->/g, '');
    const addBareTarget = (raw) => {
        const expr = raw.trim().replace(/!$/, '').trim();
        if (/^[a-zA-Z_$][\w$]*$/.test(expr)) {
            names.add(expr);
        }
    };
    for (const m of cleaned.matchAll(/\[\(\s*[\w.-]+\s*\)\]\s*=\s*"([^"]*)"/g)) {
        addBareTarget(m[1]);
    }
    for (const m of cleaned.matchAll(/\[\(\s*[\w.-]+\s*\)\]\s*=\s*'([^']*)'/g)) {
        addBareTarget(m[1]);
    }
    return names;
}

/** Identifiers referenced inside Angular binding expressions of a template (root identifiers only). */
function templateReferencedNames(html) {
    const names = new Set();
    if (!html) {
        return names;
    }
    const cleaned = html.replace(/<!--[\s\S]*?-->/g, '');
    const expressions = [];
    // {{ interpolation }}
    for (const m of cleaned.matchAll(/\{\{([\s\S]*?)\}\}/g)) {
        expressions.push(m[1]);
    }
    // [prop]="…", [(prop)]="…", (event)="…", *structural="…", [attr.x]="…" etc. (double- and single-quoted)
    for (const m of cleaned.matchAll(/(?:\[\(?[\w.\-$]+\)?\]|\([\w.\-$]+\)|\*[\w-]+)\s*=\s*"([^"]*)"/g)) {
        expressions.push(m[1]);
    }
    for (const m of cleaned.matchAll(/(?:\[\(?[\w.\-$]+\)?\]|\([\w.\-$]+\)|\*[\w-]+)\s*=\s*'([^']*)'/g)) {
        expressions.push(m[1]);
    }
    // control flow: @if/@else if/@for/@switch/@case ( … )
    for (const m of cleaned.matchAll(/@(?:if|else if|for|switch|case)\s*\(([\s\S]*?)\)\s*(?:\{|$)/gm)) {
        expressions.push(m[1]);
    }
    for (const expr of expressions) {
        // drop string literals so their contents are not mistaken for identifiers
        const noStrings = expr.replace(/'[^']*'|"[^"]*"|`[^`]*`/g, ' ');
        // root identifiers only: not preceded by '.', '?', or a word char (so `a.b` yields only `a`)
        for (const idMatch of noStrings.matchAll(/(?<![.?\w$])([a-zA-Z_$][\w$]*)\b/g)) {
            names.add(idMatch[1]);
        }
    }
    return names;
}

export default createRule({
    name: 'prefer-signal-template-state',
    meta: {
        type: 'problem',
        docs: {
            description:
                'Require signal-backed state for mutable component/directive fields that are read in the template. Under zoneless, a plain template-bound field that is reassigned outside a synchronous render/event handler (e.g. in a subscribe/setTimeout, or any helper reached from one) does not re-render. Convert it to a signal. Fields the template never reads, injected services, and constants are exempt; genuine [(ngModel)]/[(x)] two-way targets that cannot be signals use a justified line-level disable.',
        },
        messages: {
            preferSignalTemplateState:
                "Mutable field '{{name}}' is read in the template but is not a signal. Under zoneless, reassigning it anywhere other than a synchronous render / template event handler (e.g. in a subscribe/setTimeout/promise, or a helper method reached from one) schedules no change detection, so the view will not update. Convert it to a signal (signal() + .set()/.update()). If it must stay plain (a [(ngModel)]/[(x)] two-way target a signal cannot back — prefer a getter/setter-over-signal facade), disable this rule on the line with a short justification.",
        },
        schema: [],
    },
    defaultOptions: [],
    create(context) {
        const filename = context.filename ?? context.getFilename?.();

        return {
            ClassDeclaration(classNode) {
                if (!isAngularComponentOrDirective(classNode)) {
                    return;
                }
                const templateText = getTemplateText(classNode, filename);
                const templateNames = templateReferencedNames(templateText);
                if (templateNames.size === 0) {
                    return;
                }
                const twoWayNames = templateTwoWayBoundNames(templateText);

                const body = classNode.body.body;
                // names reassigned somewhere in the class via `this.x = …` / `this.x++` / `this.x += …`
                const reassigned = new Set();
                // names assigned a reactive-forms control (`this.form = fb.group(...)`) — exempt
                const reactiveFormNames = new Set();
                const collectReassignments = (node) => {
                    if (!node || typeof node.type !== 'string') {
                        return;
                    }
                    if (
                        node.type === 'AssignmentExpression' &&
                        node.left.type === 'MemberExpression' &&
                        !node.left.computed &&
                        node.left.object.type === 'ThisExpression' &&
                        node.left.property.type === 'Identifier'
                    ) {
                        reassigned.add(node.left.property.name);
                        if (isReactiveFormValue(node.right)) {
                            reactiveFormNames.add(node.left.property.name);
                        }
                    }
                    if (
                        node.type === 'UpdateExpression' &&
                        node.argument.type === 'MemberExpression' &&
                        !node.argument.computed &&
                        node.argument.object.type === 'ThisExpression' &&
                        node.argument.property.type === 'Identifier'
                    ) {
                        reassigned.add(node.argument.property.name);
                    }
                    for (const key of Object.keys(node)) {
                        if (key === 'parent') {
                            continue;
                        }
                        const child = node[key];
                        if (Array.isArray(child)) {
                            child.forEach(collectReassignments);
                        } else if (child && typeof child.type === 'string') {
                            collectReassignments(child);
                        }
                    }
                };
                collectReassignments(classNode.body);

                for (const member of body) {
                    if (member.type !== 'PropertyDefinition' || member.static || member.key.type !== 'Identifier') {
                        continue;
                    }
                    const name = member.key.name;
                    if (!templateNames.has(name) || !reassigned.has(name)) {
                        continue;
                    }
                    if (isExemptInitializer(member.value)) {
                        continue;
                    }
                    // Reactive-forms controls manage their own change propagation and cannot be backed by a signal.
                    if (hasReactiveFormTypeAnnotation(member) || isReactiveFormValue(member.value) || reactiveFormNames.has(name)) {
                        continue;
                    }
                    // Direct target of a two-way binding `[(x)]="name"` — a signal cannot back `[(x)]`.
                    if (twoWayNames.has(name)) {
                        continue;
                    }
                    context.report({ node: member.key, messageId: 'preferSignalTemplateState', data: { name } });
                }
            },
        };
    },
});
