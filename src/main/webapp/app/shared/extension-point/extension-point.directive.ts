import { Directive, EmbeddedViewRef, Input, OnChanges, SimpleChanges, TemplateRef, ViewContainerRef } from '@angular/core';

/**
 * @whatItDoes marks parts of a (parent) template as extendable to allow other (child) components to override them.
 * Its basically a variation of ngTemplateOutlet that renders the normal elements if the parameter is undefined
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
export class ExtensionPointDirective implements OnChanges {
    private _viewRef: EmbeddedViewRef<any> | undefined = undefined;

    @Input() public jhiExtensionPoint: TemplateRef<any> | undefined = undefined;
    @Input() public jhiExtensionPointContext: Object | undefined = undefined;

    constructor(private _viewContainerRef: ViewContainerRef, private _templateRef: TemplateRef<any>) {}

    ngOnChanges(changes: SimpleChanges) {
        if (changes['jhiExtensionPoint']) {
            const viewContainerRef = this._viewContainerRef;

            if (this._viewRef) {
                viewContainerRef.remove(viewContainerRef.indexOf(this._viewRef));
            }

            this._viewRef = this.jhiExtensionPoint
                ? viewContainerRef.createEmbeddedView(this.jhiExtensionPoint, this.jhiExtensionPointContext)
                : viewContainerRef.createEmbeddedView(this._templateRef, this.jhiExtensionPointContext);
        } else if (this._viewRef && changes['jhiExtensionPointContext'] && this.jhiExtensionPointContext) {
            this._viewRef.context = this.jhiExtensionPointContext;
        }
    }
}
