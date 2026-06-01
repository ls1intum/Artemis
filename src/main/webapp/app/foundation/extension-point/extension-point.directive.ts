import { Directive, EmbeddedViewRef, TemplateRef, ViewContainerRef, effect, inject, input, untracked } from '@angular/core';

/**
 * @whatItDoes marks parts of a (parent) template as extendable to allow other (child) components to override them.
 * It's basically a variation of ngTemplateOutlet that renders the normal elements if the parameter is undefined
 * (whereas ngTemplateOutlet does not render anything in that case).
 * See ngTemplateOutlet for further information
 *
 * @howToUse
 * ```
 * parent template:
 * <div *jhiExtensionPoint="overrideAttribute; context: {key: value}">
 *     ...
 * </div>
 *
 * parent typescript:
 * @ContentChild('overrideId') overrideAttribute: TemplateRef<any>;
 *
 * child template:
 * <parent-selector>
 *     <ng-template #overrideId let-key="key">
 *         ...
 *     </ng-template>
 * </parent-selector>
 * ```
 * produces a child component looking exactly like the parent component but with the marked element overridden
 */
@Directive({ selector: '[jhiExtensionPoint]' })
export class ExtensionPointDirective {
    private viewContainerRef = inject(ViewContainerRef);
    private templateRef = inject<TemplateRef<any>>(TemplateRef);

    private viewRef: EmbeddedViewRef<any> | undefined = undefined;

    readonly jhiExtensionPoint = input<TemplateRef<any> | undefined>(undefined);
    readonly jhiExtensionPointContext = input<any>(undefined);

    constructor() {
        // (Re)create the embedded view whenever the template ref changes. The context is read untracked so a
        // context-only change does not recreate the view (which would destroy and rebuild any child components).
        effect(() => {
            const template = this.jhiExtensionPoint();
            const context = untracked(() => this.jhiExtensionPointContext());

            if (this.viewRef) {
                this.viewContainerRef.remove(this.viewContainerRef.indexOf(this.viewRef));
            }

            this.viewRef = template ? this.viewContainerRef.createEmbeddedView(template, context) : this.viewContainerRef.createEmbeddedView(this.templateRef, context);
        });

        // Update the existing view's context in place when only the context changes. Mutating viewRef.context
        // (rather than reassigning it) avoids Angular 21's deprecated context-object replacement; re-running this
        // effect refreshes the view so its bindings re-read the mutated context (no markForCheck needed under
        // zoneless). Drop keys that no longer exist so a shrinking context cannot leave stale values rendered, and
        // skip when target === context (the initial flush, where the view was created with this very object —
        // clearing it would wipe the values).
        effect(() => {
            const context = this.jhiExtensionPointContext();
            const target = this.viewRef?.context;
            if (target && context && target !== context) {
                for (const key of Object.keys(target)) {
                    if (!(key in context)) {
                        delete target[key];
                    }
                }
                Object.assign(target, context);
            }
        });
    }
}
