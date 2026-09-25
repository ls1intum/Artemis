import { ESLintUtils } from '@typescript-eslint/utils';

const createRule = ESLintUtils.RuleCreator(() => '');

const ANGULAR_ROUTER = '@angular/router';

const NAVIGATION_METHODS = new Set(['navigate', 'navigateByUrl']);

/** Functional guard and resolver types: a variable, an assertion or a function return type naming one of these is a guard. */
const FUNCTION_TYPE_KINDS = new Map([
    ['CanActivateFn', 'guard'],
    ['CanActivateChildFn', 'guard'],
    ['CanMatchFn', 'guard'],
    ['CanDeactivateFn', 'guard'],
    ['CanLoadFn', 'guard'],
    ['ResolveFn', 'resolver'],
]);

/** Class guard and resolver interfaces, with the method the router calls on an implementing class. */
const INTERFACE_METHODS = new Map([
    ['CanActivate', { method: 'canActivate', kind: 'guard' }],
    ['CanActivateChild', { method: 'canActivateChild', kind: 'guard' }],
    ['CanMatch', { method: 'canMatch', kind: 'guard' }],
    ['CanDeactivate', { method: 'canDeactivate', kind: 'guard' }],
    ['CanLoad', { method: 'canLoad', kind: 'guard' }],
    ['Resolve', { method: 'resolve', kind: 'resolver' }],
]);

/** Route keys holding an array of guards. The method name doubles as the one a class guard listed there implements. */
const ROUTE_GUARD_KEYS = new Set(['canActivate', 'canActivateChild', 'canMatch', 'canDeactivate', 'canLoad']);

/** Keys that mark an object literal as a route, so that an unrelated `canActivate` or `resolve` property is not taken for one. */
const ROUTE_MARKER_KEYS = new Set(['path', 'matcher', 'component', 'loadComponent', 'children', 'loadChildren', 'redirectTo']);

/** Subtrees that hold types only. They cannot navigate, and skipping them keeps type names out of the reachability walk. */
const TYPE_ONLY_KEYS = new Set(['typeAnnotation', 'returnType', 'typeParameters', 'typeArguments', 'superTypeArguments', 'implements']);

const FUNCTION_TYPES = new Set(['ArrowFunctionExpression', 'FunctionExpression', 'FunctionDeclaration']);

/**
 * Disallows `Router.navigate()` and `Router.navigateByUrl()` inside route guards and resolvers.
 *
 * A guard or resolver runs while the router is in the middle of a navigation. Navigating from there starts a second
 * navigation that supersedes the first, which then has to be cancelled by hand: `return false` from a guard, or
 * `return EMPTY` from a resolver so that it completes without emitting. The router supports redirects directly: a guard
 * returns `router.createUrlTree(...)` or a `RedirectCommand`, a resolver returns a `RedirectCommand`, and since Angular
 * 22.2 a `RedirectCommand` thrown anywhere in the guard or resolver (including inside an RxJS operator or a promise
 * callback) cancels the navigation with a redirect as well. The redirect inherits the options of the navigation it
 * replaces, and a caller awaiting that navigation receives the redirect's outcome.
 *
 * What counts as a guard or resolver:
 * - a variable annotated with, an expression asserted (`as` / `satisfies`) to, or a function returning one of the
 *   functional types `CanActivateFn`, `CanActivateChildFn`, `CanMatchFn`, `CanDeactivateFn`, `CanLoadFn`, `ResolveFn`;
 * - the `canActivate` / `canActivateChild` / `canMatch` / `canDeactivate` / `canLoad` / `resolve` method of a class that
 *   implements the matching interface (`CanActivate`, ..., `Resolve`);
 * - a function listed inline in a route's guard arrays or `resolve` map, or a same-file function or class listed there
 *   by name.
 * The types and interfaces must be imported from `@angular/router`; aliases are followed.
 *
 * Everything reachable from a guard or resolver is checked, not just its own body: nested callbacks and RxJS operators,
 * methods of the same class it reaches through `this.method`, and functions of the same file it references by name.
 *
 * Only calls on an Angular `Router` are reported: `inject(Router)` used directly, or a local, parameter, field or
 * constructor parameter property that holds `inject(Router)` or is annotated `Router`. A component calling
 * `router.navigate(...)` is out of scope, and so is a class whose `canDeactivate()` is not Angular's `CanDeactivate`.
 */
const rule = createRule({
    name: 'no-navigation-in-guard-or-resolver',
    meta: {
        type: 'problem',
        docs: { description: 'Disallow Router navigation inside route guards and resolvers; return or throw a redirect instead' },
        messages: {
            navigateInGuard:
                'Do not call Router.{{method}}() in a route guard: it starts a second navigation while this one is still running. Return router.createUrlTree(...) or new RedirectCommand(router.createUrlTree(...)) instead, or throw a RedirectCommand inside an observable or promise chain. The router then cancels this navigation and redirects.',
            navigateInResolver:
                'Do not call Router.{{method}}() in a route resolver: it starts a second navigation while this one is still running. Return new RedirectCommand(router.createUrlTree(...)) instead, or throw it inside an observable chain. The router then cancels this navigation and redirects. A UrlTree returned from a resolver becomes route data, not a redirect.',
        },
        schema: [],
    },
    defaultOptions: [],
    create(context) {
        const sourceCode = context.sourceCode;
        const visitorKeys = sourceCode.visitorKeys;

        /** Local name -> name exported by `@angular/router`, for every specifier imported from it. */
        const routerImports = new Map();
        /** Guard and resolver entry points: `{ node, kind }`, where `node` is the subtree to walk. */
        const roots = [];
        /** Route entries given by name, resolved once the whole file is known: `{ identifier, kind, method }`. */
        const namedRouteEntries = [];

        const importedAs = (localName) => routerImports.get(localName);

        /** Removes wrappers that do not change which value an expression denotes. */
        function unwrap(node) {
            let current = node;
            while (current && (current.type === 'TSNonNullExpression' || current.type === 'TSAsExpression' || current.type === 'TSSatisfiesExpression')) {
                current = current.expression;
            }
            return current;
        }

        /** The `@angular/router` export a type reference names, or undefined. */
        function routerTypeName(typeNode) {
            if (typeNode?.type === 'TSTypeReference' && typeNode.typeName.type === 'Identifier') {
                return importedAs(typeNode.typeName.name);
            }
            return undefined;
        }

        const functionTypeKind = (typeNode) => FUNCTION_TYPE_KINDS.get(routerTypeName(typeNode));

        const isRouterAnnotation = (annotation) => routerTypeName(annotation?.typeAnnotation) === 'Router';

        /** `inject(Router)`. */
        function isInjectRouter(node) {
            const call = unwrap(node);
            return (
                call?.type === 'CallExpression' &&
                call.callee.type === 'Identifier' &&
                call.callee.name === 'inject' &&
                call.arguments[0]?.type === 'Identifier' &&
                importedAs(call.arguments[0].name) === 'Router'
            );
        }

        /** The name a class member key or a `this.x` property stands for; private names keep their `#`. */
        function memberName(key) {
            if (key?.type === 'Identifier') {
                return key.name;
            }
            if (key?.type === 'PrivateIdentifier') {
                return `#${key.name}`;
            }
            if (key?.type === 'Literal' && typeof key.value === 'string') {
                return key.value;
            }
            return undefined;
        }

        /** The class whose instance `this` refers to at `node`, or undefined where a plain function rebinds `this`. */
        function enclosingThisClass(node) {
            let current = node.parent;
            while (current) {
                if (current.type === 'FunctionExpression' || current.type === 'FunctionDeclaration') {
                    // A method's own function keeps the class's `this`; any other plain function has its own.
                    if (current.parent?.type !== 'MethodDefinition') {
                        return undefined;
                    }
                } else if (current.type === 'ClassBody') {
                    return current.parent;
                }
                current = current.parent;
            }
            return undefined;
        }

        /** The variable an identifier reference resolves to, or undefined for a global or an unresolved name. */
        function resolveReference(identifier) {
            const reference = sourceCode.getScope(identifier).references.find((ref) => ref.identifier === identifier);
            return reference?.resolved ?? undefined;
        }

        /** Whether a variable holds a Router: `inject(Router)`, a `Router` annotation, or a parameter defaulting to `inject(Router)`. */
        function isRouterVariable(variable) {
            return variable.defs.some((def) => {
                if (def.type === 'Variable') {
                    return isInjectRouter(def.node.init) || isRouterAnnotation(def.node.id.typeAnnotation);
                }
                if (def.type === 'Parameter') {
                    const parent = def.name.parent;
                    return isRouterAnnotation(def.name.typeAnnotation) || (parent?.type === 'AssignmentPattern' && parent.left === def.name && isInjectRouter(parent.right));
                }
                return false;
            });
        }

        /** Whether an identifier (not a member) resolves to a Router variable. */
        function isRouterIdentifier(node) {
            if (node?.type !== 'Identifier') {
                return false;
            }
            const variable = resolveReference(node);
            return variable !== undefined && isRouterVariable(variable);
        }

        /** Whether the class stores a Router under `name`: a field, a constructor parameter property, or a constructor assignment. */
        function classHasRouterMember(classNode, name) {
            return classNode.body.body.some((member) => {
                if (member.type === 'PropertyDefinition' && !member.computed && memberName(member.key) === name) {
                    return isInjectRouter(member.value) || isRouterAnnotation(member.typeAnnotation);
                }
                if (member.type !== 'MethodDefinition' || member.kind !== 'constructor') {
                    return false;
                }
                const isParameterProperty = member.value.params.some((param) => {
                    if (param.type !== 'TSParameterProperty') {
                        return false;
                    }
                    const parameter = param.parameter;
                    const id = parameter.type === 'AssignmentPattern' ? parameter.left : parameter;
                    return (
                        id.type === 'Identifier' &&
                        id.name === name &&
                        (isRouterAnnotation(id.typeAnnotation) || (parameter.type === 'AssignmentPattern' && isInjectRouter(parameter.right)))
                    );
                });
                return isParameterProperty || constructorAssignsRouter(member.value.body, name);
            });
        }

        /** `this.<name> = inject(Router)` or `this.<name> = <Router identifier>` anywhere in a constructor body. */
        function constructorAssignsRouter(body, name) {
            let found = false;
            walk(body, (node) => {
                if (
                    !found &&
                    node.type === 'AssignmentExpression' &&
                    node.left.type === 'MemberExpression' &&
                    node.left.object.type === 'ThisExpression' &&
                    !node.left.computed &&
                    memberName(node.left.property) === name &&
                    (isInjectRouter(node.right) || isRouterIdentifier(unwrap(node.right)))
                ) {
                    found = true;
                }
            });
            return found;
        }

        /** Whether a call receiver is an Angular Router. */
        function isRouter(receiver) {
            const node = unwrap(receiver);
            if (!node) {
                return false;
            }
            if (node.type === 'CallExpression') {
                return isInjectRouter(node);
            }
            if (node.type === 'Identifier') {
                return isRouterIdentifier(node);
            }
            if (node.type === 'MemberExpression' && node.object.type === 'ThisExpression' && !node.computed) {
                const classNode = enclosingThisClass(node);
                const name = memberName(node.property);
                return classNode !== undefined && name !== undefined && classHasRouterMember(classNode, name);
            }
            return false;
        }

        /** Depth-first walk over every AST node below `node`, skipping type-only subtrees. */
        function walk(node, visit) {
            visit(node);
            for (const key of visitorKeys[node.type] ?? []) {
                if (TYPE_ONLY_KEYS.has(key)) {
                    continue;
                }
                const value = node[key];
                if (Array.isArray(value)) {
                    for (const child of value) {
                        if (child && typeof child.type === 'string') {
                            walk(child, visit);
                        }
                    }
                } else if (value && typeof value.type === 'string') {
                    walk(value, visit);
                }
            }
        }

        /** The function a class member of the given name holds: a method, a getter, or a field initialised with a function. */
        function classMemberFunction(classNode, name) {
            for (const member of classNode.body.body) {
                if (member.computed || memberName(member.key) !== name) {
                    continue;
                }
                if (member.type === 'MethodDefinition' && (member.kind === 'method' || member.kind === 'get')) {
                    return member.value;
                }
                if (member.type === 'PropertyDefinition' && FUNCTION_TYPES.has(member.value?.type)) {
                    return member.value;
                }
            }
            return undefined;
        }

        /** The function a same-file variable denotes: a function declaration, or a variable initialised with a function. */
        function variableFunction(variable) {
            for (const def of variable.defs) {
                if (def.type === 'FunctionName') {
                    return def.node;
                }
                if (def.type === 'Variable') {
                    const init = unwrap(def.node.init);
                    if (FUNCTION_TYPES.has(init?.type)) {
                        return init;
                    }
                }
            }
            return undefined;
        }

        /** Whether an identifier is a name being referenced, as opposed to a property key or the property of a member access. */
        function isValueReference(identifier) {
            const parent = identifier.parent;
            if (parent?.type === 'MemberExpression' && parent.property === identifier && !parent.computed) {
                return false;
            }
            if ((parent?.type === 'Property' || parent?.type === 'PropertyDefinition' || parent?.type === 'MethodDefinition') && parent.key === identifier && !parent.computed) {
                return parent.type === 'Property' && parent.shorthand;
            }
            return true;
        }

        /** The kind of guard entry a route key introduces, or undefined for any other key. */
        function routeKeyKind(name) {
            if (ROUTE_GUARD_KEYS.has(name)) {
                return 'guard';
            }
            return name === 'resolve' ? 'resolver' : undefined;
        }

        function isRouteObject(objectNode) {
            return (
                objectNode?.type === 'ObjectExpression' && objectNode.properties.some((property) => property.type === 'Property' && ROUTE_MARKER_KEYS.has(memberName(property.key)))
            );
        }

        /** Registers one entry of a route's guard array or resolve map. */
        function addRouteEntry(entry, kind, method) {
            const node = unwrap(entry);
            if (FUNCTION_TYPES.has(node?.type)) {
                roots.push({ node, kind });
            } else if (node?.type === 'Identifier') {
                namedRouteEntries.push({ identifier: node, kind, method });
            }
        }

        return {
            Program(program) {
                for (const statement of program.body) {
                    if (statement.type !== 'ImportDeclaration' || statement.source.value !== ANGULAR_ROUTER) {
                        continue;
                    }
                    for (const specifier of statement.specifiers) {
                        if (specifier.type === 'ImportSpecifier') {
                            const imported = specifier.imported.type === 'Identifier' ? specifier.imported.name : specifier.imported.value;
                            routerImports.set(specifier.local.name, imported);
                        }
                    }
                }
            },

            // const guard: CanActivateFn = (route, state) => { ... };
            VariableDeclarator(node) {
                const kind = functionTypeKind(node.id.typeAnnotation?.typeAnnotation);
                if (kind && node.init) {
                    roots.push({ node: node.init, kind });
                }
            },

            // export const guard = ((route, state) => { ... }) satisfies CanActivateFn;
            'TSAsExpression, TSSatisfiesExpression'(node) {
                const kind = functionTypeKind(node.typeAnnotation);
                if (kind) {
                    roots.push({ node: node.expression, kind });
                }
            },

            // export function featureGuard(feature: string): CanActivateFn { return () => { ... }; }
            ':function'(node) {
                const kind = functionTypeKind(node.returnType?.typeAnnotation);
                if (kind && node.body) {
                    roots.push({ node: node.body, kind });
                }
            },

            // class CourseGuard implements CanActivate { canActivate() { ... } }
            'ClassDeclaration, ClassExpression'(node) {
                for (const implemented of node.implements ?? []) {
                    if (implemented.expression.type !== 'Identifier') {
                        continue;
                    }
                    const entry = INTERFACE_METHODS.get(importedAs(implemented.expression.name));
                    const method = entry && classMemberFunction(node, entry.method);
                    if (method) {
                        roots.push({ node: method, kind: entry.kind });
                    }
                }
            },

            // { path: 'x', canActivate: [() => { ... }], resolve: { course: () => { ... } } }
            Property(node) {
                if (node.computed || !isRouteObject(node.parent)) {
                    return;
                }
                const name = memberName(node.key);
                const kind = routeKeyKind(name);
                if (kind === 'guard' && node.value.type === 'ArrayExpression') {
                    for (const element of node.value.elements) {
                        addRouteEntry(element, kind, name);
                    }
                } else if (kind === 'resolver' && node.value.type === 'ObjectExpression') {
                    for (const property of node.value.properties) {
                        if (property.type === 'Property') {
                            addRouteEntry(property.value, kind, 'resolve');
                        }
                    }
                }
            },

            'Program:exit'() {
                if (![...routerImports.values()].includes('Router')) {
                    // Without Angular's Router in scope, no call can be a Router navigation.
                    return;
                }

                for (const { identifier, kind, method } of namedRouteEntries) {
                    const variable = resolveReference(identifier);
                    if (!variable) {
                        continue;
                    }
                    const classDef = variable.defs.find((def) => def.type === 'ClassName');
                    const target = classDef ? classMemberFunction(classDef.node, method) : variableFunction(variable);
                    if (target) {
                        roots.push({ node: target, kind });
                    }
                }

                const walked = new Set();
                const reported = new Set();
                const queue = [...roots];
                while (queue.length > 0) {
                    const { node: root, kind } = queue.shift();
                    if (walked.has(root)) {
                        continue;
                    }
                    walked.add(root);

                    walk(root, (node) => {
                        if (node.type === 'CallExpression') {
                            const callee = node.callee;
                            if (
                                callee.type === 'MemberExpression' &&
                                !callee.computed &&
                                callee.property.type === 'Identifier' &&
                                NAVIGATION_METHODS.has(callee.property.name) &&
                                !reported.has(node) &&
                                isRouter(callee.object)
                            ) {
                                reported.add(node);
                                context.report({
                                    node,
                                    messageId: kind === 'resolver' ? 'navigateInResolver' : 'navigateInGuard',
                                    data: { method: callee.property.name },
                                });
                            }
                        } else if (node.type === 'MemberExpression' && node.object.type === 'ThisExpression' && !node.computed) {
                            // this.helper(...) — the guard's own helper methods run as part of the guard.
                            const classNode = enclosingThisClass(node);
                            const target = classNode && classMemberFunction(classNode, memberName(node.property));
                            if (target) {
                                queue.push({ node: target, kind });
                            }
                        } else if (node.type === 'Identifier' && isValueReference(node)) {
                            // helper(...) — a function of the same file, called or passed as a callback.
                            const variable = resolveReference(node);
                            const target = variable && variableFunction(variable);
                            if (target) {
                                queue.push({ node: target, kind });
                            }
                        }
                    });
                }
            },
        };
    },
});

export default rule;
