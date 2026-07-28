import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TumUiButtonSeverity, TumUiButtonSize, TumUiButtonVariant, tumUiButtonClasses } from './tum-ui-button.variants';

/**
 * Attribute form of the tum-aet-ui button, applied to a native `<a>` or `<button>`.
 *
 * This is the directive counterpart of {@link TumUiButtonComponent} — the drop-in for PrimeNG's
 * `pButton` directive (as `tum-ui-button` is the drop-in for `<p-button>`). It exists because `pButton`
 * is used on `<a [routerLink]>` anchors (which must stay anchors to navigate) and on `<button>` elements
 * composed with other directives (e.g. `jhiDeleteButton`); the element component cannot host those.
 *
 * It is implemented as an attribute-selector `@Component` (the Angular Material `button[mat-button]`
 * pattern) rather than a plain `@Directive` because it needs a scoped stylesheet for the hover/focus
 * state layer — identical to the one in `tum-ui-button.component.scss`, but keyed on `:host` because the
 * host element (the `<a>`/`<button>`) is itself the styled button. The template is a bare `<ng-content>`,
 * so the anchor/button keeps its own projected icon + label, `routerLink`, `(click)`, `disabled`, etc.
 *
 * Reuses {@link tumUiButtonClasses}, so the directive and the element component render identically.
 */
@Component({
    selector: 'a[tumUiButton], button[tumUiButton]',
    template: '<ng-content />',
    styleUrl: './tum-ui-button.directive.scss',
    host: {
        '[class]': 'hostClasses()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiButtonDirective {
    readonly severity = input<TumUiButtonSeverity>('primary');
    readonly size = input<TumUiButtonSize>('default');
    /** Fill style: `'solid'` (default), `'outlined'`, or `'text'` — mirrors the element component. */
    readonly variant = input<TumUiButtonVariant>('solid');

    protected readonly hostClasses = computed(() => tumUiButtonClasses({ severity: this.severity(), size: this.size(), variant: this.variant() }));
}
