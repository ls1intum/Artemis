import { describe, it } from 'vitest';
import rule from './enforce-cleanup-on-destroy.mjs';
import { createTypeScriptRuleTester } from './rule-tester.mjs';

const ruleTester = createTypeScriptRuleTester();

// The rule only looks at component files, so every case has to be named like one.
const filename = 'src/main/webapp/app/example/example.component.ts';

describe('enforce-cleanup-on-destroy', () => {
    it('accepts cleanup in ngOnDestroy and in a DestroyRef.onDestroy callback alike', () => {
        ruleTester.run('enforce-cleanup-on-destroy', rule, {
            valid: [
                // The classic lifecycle hook.
                {
                    filename,
                    code: `
                        class ExampleComponent {
                            private observer = new ResizeObserver(() => {});
                            ngOnDestroy() {
                                this.observer.disconnect();
                            }
                        }
                    `,
                },
                // DestroyRef.onDestroy runs on teardown exactly like ngOnDestroy, so it counts too. This is what
                // the signal-based components use, and reporting it was the false positive this case pins down.
                {
                    filename,
                    code: `
                        class ExampleComponent {
                            private readonly destroyRef = inject(DestroyRef);
                            private observer = new ResizeObserver(() => {});
                            constructor() {
                                this.destroyRef.onDestroy(() => this.observer.disconnect());
                            }
                        }
                    `,
                },
                // Registered on a locally held DestroyRef rather than through \`this\`.
                {
                    filename,
                    code: `
                        class ExampleComponent {
                            constructor(destroyRef: DestroyRef) {
                                const observer = new ResizeObserver(() => {});
                                destroyRef.onDestroy(() => observer.disconnect());
                            }
                        }
                    `,
                },
                // Listeners and interact() handlers torn down the same way.
                {
                    filename,
                    code: `
                        class ExampleComponent {
                            private readonly destroyRef = inject(DestroyRef);
                            constructor() {
                                const el = document.querySelector('div')!;
                                el.addEventListener('scroll', this.onScroll);
                                this.destroyRef.onDestroy(() => el.removeEventListener('scroll', this.onScroll));
                            }
                        }
                    `,
                },
                // A DestroyRef obtained and used in one expression.
                {
                    filename,
                    code: `
                        class ExampleComponent {
                            private observer = new ResizeObserver(() => {});
                            constructor() {
                                inject(DestroyRef).onDestroy(() => this.observer.disconnect());
                            }
                        }
                    `,
                },
                // The field is declared after the constructor that registers on it, so the binding can only be
                // resolved once the whole file has been walked.
                {
                    filename,
                    code: `
                        class ExampleComponent {
                            constructor() {
                                this.destroyRef.onDestroy(() => this.observer.disconnect());
                            }
                            private readonly destroyRef = inject(DestroyRef);
                            private observer = new ResizeObserver(() => {});
                        }
                    `,
                },
                // A non-component file is out of scope entirely.
                {
                    filename: 'src/main/webapp/app/example/example.service.ts',
                    code: `const observer = new ResizeObserver(() => {});`,
                },
            ],
            invalid: [
                // No teardown at all.
                {
                    filename,
                    code: `
                        class ExampleComponent {
                            private observer = new ResizeObserver(() => {});
                        }
                    `,
                    errors: [{ messageId: 'missingObserverDisconnect' }],
                },
                // An \`onDestroy\` on something that is not a DestroyRef must not be mistaken for cleanup,
                // otherwise the rule could be silenced by an unrelated method of the same name.
                {
                    filename,
                    code: `
                        class ExampleComponent {
                            private observer = new ResizeObserver(() => {});
                            constructor(private readonly editor: SomeEditor) {
                                this.editor.onDestroy(() => this.observer.disconnect());
                            }
                        }
                    `,
                    errors: [{ messageId: 'missingObserverDisconnect' }],
                },
                // A name that merely looks like a DestroyRef is not one. Its \`onDestroy\` may store the callback
                // and never call it, so the observer is still leaked.
                {
                    filename,
                    code: `
                        class ExampleComponent {
                            private readonly customDestroyRef = new CustomLifecycle();
                            private observer = new ResizeObserver(() => {});
                            constructor() {
                                this.customDestroyRef.onDestroy(() => this.observer.disconnect());
                            }
                        }
                    `,
                    errors: [{ messageId: 'missingObserverDisconnect' }],
                },
            ],
        });
    });
});
