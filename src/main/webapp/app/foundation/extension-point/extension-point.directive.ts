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
 * \@ContentChild('overrideId') overrideAttribute: TemplateRef<any>;
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

    // Tracks whether jhiExtensionPoint has ever been processed, so the effect can distinguish a genuine change
    // of the override template (which recreates the view) from a context-only change (which only updates the
    // existing view's context) — reproducing the change-discrimination the previous ngOnChanges performed.
    private extensionPointInitialized = false;
    private lastExtensionPoint: TemplateRef<any> | undefined = undefined;

    constructor() {
        effect(() => {
            const extensionPoint = this.jhiExtensionPoint();
            const context = this.jhiExtensionPointContext();

            untracked(() => {
                const extensionPointChanged = !this.extensionPointInitialized || extensionPoint !== this.lastExtensionPoint;

                if (extensionPointChanged) {
                    this.extensionPointInitialized = true;
                    this.lastExtensionPoint = extensionPoint;

                    const viewContainerRef = this.viewContainerRef;

                    if (this.viewRef) {
                        viewContainerRef.remove(viewContainerRef.indexOf(this.viewRef));
                    }

                    this.viewRef = extensionPoint ? viewContainerRef.createEmbeddedView(extensionPoint, context) : viewContainerRef.createEmbeddedView(this.templateRef, context);
                } else if (this.viewRef && context) {
                    this.viewRef.context = context;
                }
            });
        });
    }
}
