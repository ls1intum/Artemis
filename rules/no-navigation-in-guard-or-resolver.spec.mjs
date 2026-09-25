import { describe, it } from 'vitest';
import rule from './no-navigation-in-guard-or-resolver.mjs';
import { createTypeScriptRuleTester } from './rule-tester.mjs';

const ruleTester = createTypeScriptRuleTester();

const guardError = (method = 'navigate') => ({ messageId: 'navigateInGuard', data: { method } });
const resolverError = (method = 'navigate') => ({ messageId: 'navigateInResolver', data: { method } });

// Every case imports what it uses: the rule only recognises guard types and the Router when they come from @angular/router.
const functionalImports = `import { inject } from '@angular/core';
import { CanActivateFn, CanActivateChildFn, CanMatchFn, CanDeactivateFn, ResolveFn, RedirectCommand, Router } from '@angular/router';`;
const classImports = `import { Injectable, inject } from '@angular/core';
import { CanActivate, CanActivateChild, CanDeactivate, CanMatch, Resolve, RedirectCommand, Router } from '@angular/router';`;

describe('no-navigation-in-guard-or-resolver', () => {
    it('accepts redirects that are returned or thrown', () => {
        ruleTester.run('no-navigation-in-guard-or-resolver', rule, {
            valid: [
                // A functional guard returning a UrlTree.
                {
                    code: `${functionalImports}
                        export const lectureGuard: CanActivateFn = () => {
                            const router = inject(Router);
                            return isEnabled() || router.createUrlTree(['/']);
                        };`,
                },
                // A class guard returning a RedirectCommand with navigation options.
                {
                    code: `${classImports}
                        @Injectable({ providedIn: 'root' })
                        export class PasskeyGuard implements CanActivate {
                            private readonly router = inject(Router);
                            canActivate(route, state) {
                                return new RedirectCommand(this.router.createUrlTree(['/passkey-required'], { queryParams: { returnUrl: state.url } }), { replaceUrl: true });
                            }
                        }`,
                },
                // A resolver that throws a RedirectCommand inside an RxJS operator.
                {
                    code: `${classImports}
                        export class CourseResolver implements Resolve<Course> {
                            private router = inject(Router);
                            resolve(route) {
                                return this.service.find(route.params.courseId).pipe(
                                    map((course) => {
                                        if (!course.isAtLeastTutor) {
                                            throw new RedirectCommand(this.router.createUrlTree(['/courses']));
                                        }
                                        return course;
                                    }),
                                );
                            }
                        }`,
                },
                // An inline route guard that already returns a UrlTree.
                {
                    code: `${functionalImports}
                        export const routes = [{ path: '', canActivate: [() => inject(Router).parseUrl('/courses')] }];`,
                },
            ],
            invalid: [],
        });
    });

    it('ignores navigation outside guards and resolvers', () => {
        ruleTester.run('no-navigation-in-guard-or-resolver', rule, {
            valid: [
                // A component navigating on a user action is the normal case.
                {
                    code: `${classImports}
                        export class CourseDetailComponent {
                            private router = inject(Router);
                            openExercises() {
                                void this.router.navigate(['/courses', 1, 'exercises']);
                            }
                        }`,
                },
                // A component with its own canDeactivate() implements the application's ComponentCanDeactivate, not
                // Angular's CanDeactivate, so it is not a guard.
                {
                    code: `${classImports}
                        import { ComponentCanDeactivate } from 'app/foundation/guard/can-deactivate.model';
                        export class EditorComponent implements ComponentCanDeactivate {
                            private router = inject(Router);
                            canDeactivate() {
                                void this.router.navigate(['/']);
                                return true;
                            }
                        }`,
                },
                // A guard class method that the guard entry point never reaches.
                {
                    code: `${classImports}
                        export class LectureGuard implements CanActivate {
                            private router = inject(Router);
                            canActivate() {
                                return true;
                            }
                            leave() {
                                void this.router.navigate(['/']);
                            }
                        }`,
                },
                // A plain function that is not typed as a guard, such as a service helper.
                {
                    code: `${functionalImports}
                        export function goHome() {
                            void inject(Router).navigate(['/']);
                        }`,
                },
                // navigate() on something other than Angular's Router.
                {
                    code: `${functionalImports}
                        export const courseGuard: CanActivateFn = () => {
                            const navigation = inject(NavigationService);
                            navigation.navigate(['/']);
                            return true;
                        };`,
                },
                // A guard interface and a Router that do not come from @angular/router.
                {
                    code: `import { inject } from '@angular/core';
                        import { CanActivate, Router } from 'app/custom/router';
                        export class CustomGuard implements CanActivate {
                            private router = inject(Router);
                            canActivate() {
                                void this.router.navigate(['/']);
                                return false;
                            }
                        }`,
                },
                // A canActivate property on an object that is not a route.
                {
                    code: `${functionalImports}
                        export const permissions = { canActivate: [() => inject(Router).navigate(['/'])] };`,
                },
                // A plain function nested in a guard rebinds this, so this.router is not the guard's Router.
                {
                    code: `${classImports}
                        export class LegacyGuard implements CanActivate {
                            private router = inject(Router);
                            canActivate() {
                                return new Promise(function (resolve) {
                                    this.router.navigate(['/']);
                                    resolve(true);
                                });
                            }
                        }`,
                },
            ],
            invalid: [],
        });
    });

    it('reports navigation in functional guards and resolvers', () => {
        ruleTester.run('no-navigation-in-guard-or-resolver', rule, {
            valid: [],
            invalid: [
                // Each functional type, with the Router held in a local.
                ...['CanActivateFn', 'CanActivateChildFn', 'CanMatchFn', 'CanDeactivateFn<EditorComponent>'].map((type) => ({
                    code: `${functionalImports}
                        export const guard: ${type} = () => {
                            const router = inject(Router);
                            void router.navigate(['/']);
                            return false;
                        };`,
                    errors: [guardError()],
                })),
                {
                    code: `${functionalImports}
                        export const courseResolver: ResolveFn<Course> = () => {
                            const router = inject(Router);
                            void router.navigate(['/courses']);
                            return EMPTY;
                        };`,
                    errors: [resolverError()],
                },
                // inject(Router) used in place, and navigateByUrl.
                {
                    code: `${functionalImports}
                        export const guard: CanActivateFn = () => {
                            void inject(Router).navigateByUrl('/sign-in');
                            return false;
                        };`,
                    errors: [guardError('navigateByUrl')],
                },
                // A Router annotation instead of an inject(Router) initialiser.
                {
                    code: `${functionalImports}
                        export const guard: CanActivateFn = () => {
                            const router: Router = lookUpRouter();
                            void router.navigate(['/']);
                            return false;
                        };`,
                    errors: [guardError()],
                },
                // Guards typed by an assertion rather than an annotation.
                {
                    code: `${functionalImports}
                        export const guard = (() => {
                            void inject(Router).navigate(['/']);
                            return false;
                        }) satisfies CanActivateFn;`,
                    errors: [guardError()],
                },
                {
                    code: `${functionalImports}
                        export const courseResolver = (() => {
                            void inject(Router).navigate(['/']);
                            return EMPTY;
                        }) as ResolveFn<Course>;`,
                    errors: [resolverError()],
                },
                // A factory returning a guard: the returned function runs inside the navigation.
                {
                    code: `${functionalImports}
                        export function featureGuard(feature: string): CanActivateFn {
                            return () => {
                                const router = inject(Router);
                                if (!isActive(feature)) {
                                    void router.navigate(['/']);
                                    return false;
                                }
                                return true;
                            };
                        }`,
                    errors: [guardError()],
                },
                // Inside an RxJS operator callback.
                {
                    code: `${functionalImports}
                        export const guard: CanActivateFn = () => {
                            const router = inject(Router);
                            return loadTabs().pipe(
                                map((tabs) => {
                                    if (!tabs.lectures) {
                                        void router.navigate(['/courses/1/exercises']);
                                    }
                                    return tabs.lectures;
                                }),
                            );
                        };`,
                    errors: [guardError()],
                },
                // A same-file helper the guard calls, receiving the Router as a parameter.
                {
                    code: `${functionalImports}
                        function redirectHome(router: Router) {
                            void router.navigate(['/']);
                            return false;
                        }
                        export const guard: CanActivateFn = () => redirectHome(inject(Router));`,
                    errors: [guardError()],
                },
                // A same-file helper passed as a callback.
                {
                    code: `${functionalImports}
                        const denyAndLeave = () => {
                            void inject(Router).navigate(['/']);
                            return false;
                        };
                        export const guard: CanActivateFn = () => check().pipe(map(denyAndLeave));`,
                    errors: [guardError()],
                },
                // Imports under an alias.
                {
                    code: `import { inject } from '@angular/core';
                        import { CanActivateFn as Guard, Router as NgRouter } from '@angular/router';
                        export const guard: Guard = () => {
                            void inject(NgRouter).navigate(['/']);
                            return false;
                        };`,
                    errors: [guardError()],
                },
            ],
        });
    });

    it('reports navigation in class guards and resolvers', () => {
        ruleTester.run('no-navigation-in-guard-or-resolver', rule, {
            valid: [],
            invalid: [
                // Each interface with its method, the Router held in an inject(Router) field.
                ...[
                    ['CanActivate', 'canActivate'],
                    ['CanActivateChild', 'canActivateChild'],
                    ['CanMatch', 'canMatch'],
                    ['CanDeactivate<EditorComponent>', 'canDeactivate'],
                ].map(([type, method]) => ({
                    code: `${classImports}
                        @Injectable({ providedIn: 'root' })
                        export class ExampleGuard implements ${type} {
                            private router = inject(Router);
                            ${method}() {
                                void this.router.navigate(['/']);
                                return false;
                            }
                        }`,
                    errors: [guardError()],
                })),
                // A resolver navigating inside switchMap, then completing with EMPTY.
                {
                    code: `${classImports}
                        @Injectable({ providedIn: 'root' })
                        export class CourseResolver implements Resolve<Course> {
                            private router = inject(Router);
                            resolve(route) {
                                return this.service.find(route.params.courseId).pipe(
                                    switchMap((course) => {
                                        if (!course.isAtLeastTutor) {
                                            void this.router.navigate(['/courses']);
                                            return EMPTY;
                                        }
                                        return of(course);
                                    }),
                                );
                            }
                        }`,
                    errors: [resolverError()],
                },
                // Constructor injection through a parameter property.
                {
                    code: `${classImports}
                        export class ExampleGuard implements CanActivate {
                            constructor(private readonly router: Router) {}
                            canActivate() {
                                void this.router.navigate(['/']);
                                return false;
                            }
                        }`,
                    errors: [guardError()],
                },
                // Constructor injection assigned to a field by hand.
                {
                    code: `${classImports}
                        export class ExampleGuard implements CanActivate {
                            private router;
                            constructor(router: Router) {
                                this.router = router;
                            }
                            canActivate() {
                                void this.router.navigate(['/']);
                                return false;
                            }
                        }`,
                    errors: [guardError()],
                },
                // A private field, reached through optional chaining.
                {
                    code: `${classImports}
                        export class ExampleGuard implements CanActivate {
                            readonly #router = inject(Router);
                            canActivate() {
                                void this.#router?.navigate(['/']);
                                return false;
                            }
                        }`,
                    errors: [guardError()],
                },
                // A helper method reached from an RxJS callback, the shape of a guard that decides and redirects in one place.
                {
                    code: `${classImports}
                        export class CourseOverviewGuard implements CanActivate {
                            private router = inject(Router);
                            canActivate(route) {
                                return this.tabs.load().pipe(map((tabs) => this.decideAccess(tabs, route)));
                            }
                            decideAccess(tabs, route) {
                                if (!tabs.lectures) {
                                    void this.router.navigate(['/courses/1/exercises']);
                                }
                                return tabs.lectures;
                            }
                        }`,
                    errors: [guardError()],
                },
                // Reachability is transitive, and a promise callback is part of the guard too.
                {
                    code: `${classImports}
                        export class AccessGuard implements CanActivate {
                            private router = inject(Router);
                            canActivate(route, state) {
                                return this.checkLogin(state.url);
                            }
                            checkLogin(url) {
                                return this.account.identity().then((account) => this.deny(account, url));
                            }
                            private deny = (account, url) => {
                                void this.router.navigate(['accessdenied']).then(() => this.router.navigate(['/sign-in']));
                                return false;
                            };
                        }`,
                    errors: [guardError(), guardError()],
                },
                // One class implementing two interfaces: both entry points are checked.
                {
                    code: `${classImports}
                        export class ExampleGuard implements CanActivate, CanDeactivate<EditorComponent> {
                            private router = inject(Router);
                            canActivate() {
                                void this.router.navigate(['/a']);
                                return false;
                            }
                            canDeactivate() {
                                void this.router.navigateByUrl('/b');
                                return false;
                            }
                        }`,
                    errors: [guardError(), guardError('navigateByUrl')],
                },
            ],
        });
    });

    it('reports navigation in guards and resolvers declared in a route', () => {
        ruleTester.run('no-navigation-in-guard-or-resolver', rule, {
            valid: [],
            invalid: [
                // Inline guard and inline resolver.
                {
                    code: `${functionalImports}
                        export const routes = [
                            {
                                path: 'lectures',
                                canActivate: [
                                    () => {
                                        void inject(Router).navigate(['/']);
                                        return false;
                                    },
                                ],
                                resolve: {
                                    course: () => {
                                        void inject(Router).navigate(['/courses']);
                                        return EMPTY;
                                    },
                                },
                            },
                        ];`,
                    errors: [guardError(), resolverError()],
                },
                // Guards and resolvers listed by name: a same-file function and a same-file class.
                {
                    code: `${classImports}
                        function lectureGuard() {
                            void inject(Router).navigate(['/']);
                            return false;
                        }
                        class CourseResolver {
                            private router = inject(Router);
                            resolve() {
                                void this.router.navigate(['/courses']);
                                return EMPTY;
                            }
                        }
                        export const routes = [{ path: 'lectures', canMatch: [lectureGuard], resolve: { course: CourseResolver } }];`,
                    errors: [guardError(), resolverError()],
                },
            ],
        });
    });
});
