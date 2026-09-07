import { ESLintUtils } from '@typescript-eslint/utils';

const createRule = ESLintUtils.RuleCreator(() => '');

/**
 * ESLint rule that enforces proper cleanup of resources on Angular component teardown.
 *
 * Detects:
 * - MutationObserver / ResizeObserver / IntersectionObserver created without .disconnect() on teardown
 * - addEventListener calls without removeEventListener on teardown
 * - interact() calls without .unset() on teardown
 * - Monaco disposable-returning calls (monaco.editor.create / createModel, monaco.languages.register*,
 *   editor.addAction) without a .dispose() on teardown. These retain the editor/model — and its whole
 *   detached DOM subtree — via Monaco's process-global registries until disposed (see PR #12976, where an
 *   undisposed editor.addCommand leaked every Monaco editor on navigation). The companion no-restricted-syntax
 *   rule bans editor.addCommand outright; this rule guards the disposables that are legitimate but must be
 *   torn down.
 *
 * Cleanup counts wherever Angular runs it on teardown: the `ngOnDestroy` method, or a callback registered
 * with `DestroyRef.onDestroy()`. The latter is the idiom the signal-based components here use, and treating
 * only `ngOnDestroy` as cleanup reported them as leaks even though they tear down correctly.
 *
 * Only checks Angular component files (.component.ts).
 */
const rule = createRule({
    name: 'enforce-cleanup-on-destroy',
    meta: {
        type: 'problem',
        docs: {
            description: 'Enforce cleanup of observers, event listeners, and interact.js handlers on component teardown',
        },
        messages: {
            missingObserverDisconnect:
                '{{observerType}} is created but never disconnected. Store the observer and call .disconnect() in ngOnDestroy() or in a DestroyRef.onDestroy() callback to prevent memory leaks.',
            missingRemoveEventListener:
                "addEventListener('{{eventName}}', ...) is called but the listener is not removed on teardown. Store the listener reference and call removeEventListener() in ngOnDestroy() or in a DestroyRef.onDestroy() callback.",
            missingInteractUnset:
                'interact() handler is created but never unset. Store the return value and call .unset() in ngOnDestroy() or in a DestroyRef.onDestroy() callback to prevent accumulated event handlers.',
            missingMonacoDispose:
                "{{api}} returns a Monaco disposable that is never disposed. Store it and call .dispose() in ngOnDestroy() or in a DestroyRef.onDestroy() callback — otherwise the editor/model (and its detached DOM) is retained via Monaco's process-global registries (see PR #12976).",
        },
        schema: [],
    },
    defaultOptions: [],
    create(context) {
        // Only apply to Angular component files
        const filename = context.filename || context.getFilename();
        if (!filename.endsWith('.component.ts')) {
            return {};
        }

        // Text of every teardown scope in the file: the ngOnDestroy body plus each DestroyRef.onDestroy()
        // callback. The checks below are text heuristics, so one accumulated string is all they need.
        let cleanupBody = '';

        // Names bound to an actual DestroyRef, so that an unrelated object with an onDestroy() method is not
        // mistaken for Angular's teardown hook. Filled while traversing and read back at Program:exit, because a
        // field can be declared after the constructor that uses it.
        const destroyRefBindings = new Set();
        const onDestroyCalls = []; // { receiverName, callbackText } — resolved once every binding is known

        const observers = []; // { type, node }
        const addEventListenerCalls = []; // { eventName, node }
        const interactCalls = []; // { node }
        const monacoDisposables = []; // { api, node, namespaced }
        let referencesMonaco = false; // file uses the `monaco.*` namespace at least once

        /** `inject(DestroyRef)`, the way every component here obtains one. */
        function isDestroyRefValue(node) {
            return node?.type === 'CallExpression' && node.callee?.name === 'inject' && node.arguments?.[0]?.name === 'DestroyRef';
        }

        /** A `: DestroyRef` annotation on a field, local or parameter. */
        function isDestroyRefTypeAnnotation(node) {
            return node?.typeAnnotation?.type === 'TSTypeReference' && node.typeAnnotation.typeName?.name === 'DestroyRef';
        }

        function addBinding(node) {
            if (node?.type === 'Identifier') {
                destroyRefBindings.add(node.name);
            }
        }

        /**
         * The name a receiver of `.onDestroy()` refers to: the identifier itself, the property behind `this.`, or
         * `''` for a `inject(DestroyRef)` called in place. Returns undefined for anything that cannot be a DestroyRef.
         */
        function resolveReceiverName(receiver) {
            if (!receiver) {
                return undefined;
            }
            if (receiver.type === 'Identifier') {
                return receiver.name;
            }
            if (receiver.type === 'MemberExpression' && receiver.object?.type === 'ThisExpression' && !receiver.computed) {
                return receiver.property?.name;
            }
            if (isDestroyRefValue(receiver)) {
                return '';
            }
            return undefined;
        }

        return {
            // Collect ngOnDestroy method body text for checking cleanup calls
            "MethodDefinition[key.name='ngOnDestroy']"(node) {
                if (node.value && node.value.body) {
                    cleanupBody += context.sourceCode.getText(node.value.body);
                }
            },

            // Remember every name that holds a DestroyRef: `inject(DestroyRef)` initialisers and `: DestroyRef`
            // annotations, whether on a field, a local or a constructor parameter.
            PropertyDefinition(node) {
                if (isDestroyRefValue(node.value) || isDestroyRefTypeAnnotation(node.typeAnnotation)) {
                    addBinding(node.key);
                }
            },
            VariableDeclarator(node) {
                if (isDestroyRefValue(node.init) || isDestroyRefTypeAnnotation(node.id?.typeAnnotation)) {
                    addBinding(node.id);
                }
            },
            TSParameterProperty(node) {
                if (isDestroyRefTypeAnnotation(node.parameter?.typeAnnotation)) {
                    addBinding(node.parameter);
                }
            },
            'MethodDefinition[kind="constructor"] > FunctionExpression > Identifier'(node) {
                if (isDestroyRefTypeAnnotation(node.typeAnnotation)) {
                    addBinding(node);
                }
            },

            // Collect DestroyRef.onDestroy(() => ...) callbacks, which run on teardown exactly like ngOnDestroy.
            // Resolved against the bindings above rather than the receiver's name, so that a `customDestroyRef`
            // that is not a DestroyRef cannot silence the rule with an onDestroy() of its own.
            "CallExpression[callee.property.name='onDestroy']"(node) {
                const receiverName = resolveReceiverName(node.callee.object);
                if (receiverName === undefined) {
                    return;
                }
                const callbackText = node.arguments.map((argument) => context.sourceCode.getText(argument)).join('');
                onDestroyCalls.push({ receiverName, callbackText });
            },

            // Detect new MutationObserver / ResizeObserver / IntersectionObserver
            'NewExpression[callee.name=/^(MutationObserver|ResizeObserver|IntersectionObserver)$/]'(node) {
                observers.push({ type: node.callee.name, node });
            },

            // Detect addEventListener calls
            "CallExpression[callee.property.name='addEventListener']"(node) {
                const args = node.arguments;
                if (args.length >= 2) {
                    const eventName = args[0].type === 'Literal' ? args[0].value : '<dynamic>';
                    addEventListenerCalls.push({ eventName, node });
                }
            },

            // Note whether the file uses the Monaco namespace at all (guards the editor-instance checks below)
            "MemberExpression[object.name='monaco']"() {
                referencesMonaco = true;
            },

            // Detect Monaco namespace factories that return a disposable: monaco.editor.create / createDiffEditor /
            // createModel, and monaco.languages.register* (completion/hover/etc. providers). These are unambiguously
            // Monaco because they are read off the `monaco.editor` / `monaco.languages` namespace.
            "CallExpression[callee.type='MemberExpression'][callee.property.name=/^(create|createDiffEditor|createModel)$/]"(node) {
                const objText = context.sourceCode.getText(node.callee.object);
                if (objText === 'monaco.editor') {
                    monacoDisposables.push({ api: `monaco.editor.${node.callee.property.name}`, node, namespaced: true });
                }
            },
            "CallExpression[callee.type='MemberExpression'][callee.property.name=/^register[A-Z]/]"(node) {
                const objText = context.sourceCode.getText(node.callee.object);
                if (objText === 'monaco.languages') {
                    monacoDisposables.push({ api: `monaco.languages.${node.callee.property.name}`, node, namespaced: true });
                }
            },
            // editor.addAction(...) returns an IDisposable. The receiver is an editor *instance* (a variable), so it
            // is not namespace-qualified; only flag it when the file otherwise references the `monaco` namespace.
            "CallExpression[callee.type='MemberExpression'][callee.property.name='addAction']"(node) {
                monacoDisposables.push({ api: 'editor.addAction', node, namespaced: false });
            },

            // Detect interact() calls
            "CallExpression[callee.name='interact']"(node) {
                // Check if result is stored in a variable/field
                const parent = node.parent;
                let isStored = false;
                // Walk up the chain (interact().resizable().on() etc.)
                let current = parent;
                while (current && current.type === 'CallExpression') {
                    current = current.parent;
                }
                if (current && (current.type === 'AssignmentExpression' || current.type === 'VariableDeclarator')) {
                    isStored = true;
                }
                // Also check ExpressionStatement chains like this.interactable = interact(...)...
                if (current && current.type === 'MemberExpression' && current.parent && current.parent.type === 'AssignmentExpression') {
                    isStored = true;
                }
                interactCalls.push({ node, isStored });
            },

            // At the end of the program, check if cleanup is present
            'Program:exit'() {
                // Only now is every DestroyRef binding known, so the collected callbacks can be attributed.
                // `''` is the marker for `inject(DestroyRef).onDestroy(...)`, which needs no binding at all.
                for (const call of onDestroyCalls) {
                    if (call.receiverName === '' || destroyRefBindings.has(call.receiverName)) {
                        cleanupBody += call.callbackText;
                    }
                }

                // Check observers
                for (const obs of observers) {
                    if (!cleanupBody.includes('.disconnect()') && !cleanupBody.includes('disconnect()')) {
                        context.report({
                            node: obs.node,
                            messageId: 'missingObserverDisconnect',
                            data: { observerType: obs.type },
                        });
                    }
                }

                // Check addEventListener — only report if removeEventListener is missing from every teardown scope
                for (const listener of addEventListenerCalls) {
                    // Skip @HostListener-style calls (those on 'document' or 'window' objects managed by Angular)
                    const callee = listener.node.callee;
                    if (callee && callee.object) {
                        const objText = context.sourceCode.getText(callee.object);
                        if (objText === 'document' || objText === 'window') {
                            continue;
                        }
                    }
                    if (!cleanupBody.includes('removeEventListener')) {
                        context.report({
                            node: listener.node,
                            messageId: 'missingRemoveEventListener',
                            data: { eventName: listener.eventName },
                        });
                    }
                }

                // Check interact()
                for (const call of interactCalls) {
                    if (!cleanupBody.includes('.unset()') && !cleanupBody.includes('unset()')) {
                        context.report({
                            node: call.node,
                            messageId: 'missingInteractUnset',
                        });
                    }
                }

                // Check Monaco disposables — flag only when there is no .dispose() in any teardown scope (mirrors
                // the .disconnect()/.unset() heuristics above). Editor-instance calls (addAction) are reported only
                // when the file references the monaco namespace, to avoid matching unrelated .addAction() methods.
                if (!cleanupBody.includes('.dispose()')) {
                    for (const disposable of monacoDisposables) {
                        if (!disposable.namespaced && !referencesMonaco) {
                            continue;
                        }
                        context.report({
                            node: disposable.node,
                            messageId: 'missingMonacoDispose',
                            data: { api: disposable.api },
                        });
                    }
                }
            },
        };
    },
});

export default rule;
