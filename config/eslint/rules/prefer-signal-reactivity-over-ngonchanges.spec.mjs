import { describe, it } from 'vitest';
import rule from './prefer-signal-reactivity-over-ngonchanges.mjs';
import { createTypeScriptRuleTester } from './rule-tester.mjs';

const ruleTester = createTypeScriptRuleTester();

describe('prefer-signal-reactivity-over-ngonchanges', () => {
    it('flags ngOnChanges wherever it is declared, including undecorated base classes', () => {
        ruleTester.run('prefer-signal-reactivity-over-ngonchanges', rule, {
            valid: [
                // The idiomatic replacements.
                {
                    code: `
                        @Component({ selector: 'jhi-example' })
                        export class ExampleComponent {
                            value = input.required<number>();
                            doubled = computed(() => this.value() * 2);
                        }
                    `,
                },
                // Other lifecycle hooks are untouched — only ngOnChanges is banned.
                {
                    code: `
                        @Component({ selector: 'jhi-example' })
                        export class ExampleComponent {
                            ngOnInit() {}
                            ngOnDestroy() {}
                            ngAfterViewInit() {}
                        }
                    `,
                },
                // Angular only invokes the *instance* hook, so an unrelated static helper is not a lifecycle hook.
                {
                    code: `
                        @Component({ selector: 'jhi-example' })
                        export class ExampleComponent {
                            static ngOnChanges() {}
                        }
                    `,
                },
                // A type-only declaration is a TSMethodSignature, not a class member: Angular never calls it.
                { code: 'export interface OnChangesLike { ngOnChanges(changes: SimpleChanges): void; }' },
                // An object literal (e.g. a hand-rolled mock) is a Property, not a class member.
                { code: 'const mock = { ngOnChanges: () => {} };' },
                // A local variable or function merely named ngOnChanges is not a class member either.
                { code: 'function ngOnChanges() {}' },
                // A key computed from a variable cannot be resolved statically, so no lint rule can see it. Recorded
                // here as a known, accepted limitation: writing a lifecycle hook this way is indistinguishable from
                // deliberately disabling the rule.
                {
                    code: `
                        const hookName = 'ngOnChanges';
                        @Component({ selector: 'jhi-example' })
                        export class ExampleComponent {
                            [hookName]() {}
                        }
                    `,
                },
                // A static member is never a lifecycle hook, in the quoted form either.
                {
                    code: `
                        @Component({ selector: 'jhi-example' })
                        export class ExampleComponent {
                            static ['ngOnChanges']() {}
                        }
                    `,
                },
            ],
            invalid: [
                // The plain case: a decorated component.
                {
                    code: `
                        @Component({ selector: 'jhi-example' })
                        export class ExampleComponent {
                            ngOnChanges(changes: SimpleChanges) { this.recompute(changes); }
                        }
                    `,
                    errors: [{ messageId: 'preferSignalReactivity' }],
                },
                // A decorated directive.
                {
                    code: `
                        @Directive({ selector: '[jhiExample]' })
                        export class ExampleDirective {
                            ngOnChanges() {}
                        }
                    `,
                    errors: [{ messageId: 'preferSignalReactivity' }],
                },
                // The reason the rule does not require a decorator: Angular calls an inherited ngOnChanges on the
                // component instance exactly like one declared on the component, so an undecorated base class would
                // otherwise be a way to reintroduce the hook without any lint coverage.
                {
                    code: `
                        export abstract class ExampleBase {
                            ngOnChanges() {}
                        }
                    `,
                    errors: [{ messageId: 'preferSignalReactivity' }],
                },
                // A class with no decorator and no Angular-looking shape at all is still flagged: the client has no
                // legitimate use for the name, so the ban stays unconditional rather than heuristic.
                {
                    code: `
                        export class PlainHelper {
                            ngOnChanges() {}
                        }
                    `,
                    errors: [{ messageId: 'preferSignalReactivity' }],
                },
                // The arrow-property form Angular also honours.
                {
                    code: `
                        @Component({ selector: 'jhi-example' })
                        export class ExampleComponent {
                            ngOnChanges = (changes: SimpleChanges) => this.recompute(changes);
                        }
                    `,
                    errors: [{ messageId: 'preferSignalReactivity' }],
                },
                // A bare decorator (no call expression) used to be a separate code path in the decorator lookup;
                // keep it covered so the simplification cannot silently regress.
                {
                    code: `
                        @Component
                        export class ExampleComponent {
                            ngOnChanges() {}
                        }
                    `,
                    errors: [{ messageId: 'preferSignalReactivity' }],
                },
                // The quoted and computed spellings declare the same prototype member and Angular calls them the
                // same way, so the ban must cover them too — matching only `key.name` let all three through.
                {
                    code: `
                        @Component({ selector: 'jhi-example' })
                        export class ExampleComponent {
                            'ngOnChanges'() {}
                        }
                    `,
                    errors: [{ messageId: 'preferSignalReactivity' }],
                },
                {
                    code: `
                        @Component({ selector: 'jhi-example' })
                        export class ExampleComponent {
                            ['ngOnChanges']() {}
                        }
                    `,
                    errors: [{ messageId: 'preferSignalReactivity' }],
                },
                {
                    code: `
                        @Component({ selector: 'jhi-example' })
                        export class ExampleComponent {
                            ['ngOnChanges'] = (changes: SimpleChanges) => this.recompute(changes);
                        }
                    `,
                    errors: [{ messageId: 'preferSignalReactivity' }],
                },
                {
                    code: `
                        @Component({ selector: 'jhi-example' })
                        export class ExampleComponent {
                            [\`ngOnChanges\`]() {}
                        }
                    `,
                    errors: [{ messageId: 'preferSignalReactivity' }],
                },
            ],
        });
    });
});
