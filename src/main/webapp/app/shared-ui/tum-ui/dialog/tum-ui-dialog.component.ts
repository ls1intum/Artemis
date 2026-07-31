import {
    ChangeDetectionStrategy,
    Component,
    OnDestroy,
    TemplateRef,
    ViewContainerRef,
    computed,
    contentChild,
    effect,
    inject,
    input,
    model,
    output,
    viewChild,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { A11yModule } from '@angular/cdk/a11y';
import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faXmark } from '@fortawesome/free-solid-svg-icons';

// Per-instance unique id for the title element so `aria-labelledby` on the dialog resolves even when
// several dialogs are declared on the same page.
let nextDialogId = 0;

// Page scroll lock shared by all dialogs, counted so nested dialogs unlock once.
let openDialogCount = 0;
let previousRootOverflow = '';
let previousRootPaddingRight = '';

function lockPageScroll(): void {
    if (openDialogCount++ > 0) {
        return;
    }
    const root = document.documentElement;
    previousRootOverflow = root.style.overflow;
    previousRootPaddingRight = root.style.paddingRight;
    // Reserve the scrollbar width so the page behind the mask does not reflow.
    const scrollbarWidth = window.innerWidth - root.clientWidth;
    root.style.overflow = 'hidden';
    if (scrollbarWidth > 0) {
        root.style.paddingRight = `${scrollbarWidth}px`;
    }
}

function unlockPageScroll(): void {
    if (openDialogCount === 0 || --openDialogCount > 0) {
        return;
    }
    const root = document.documentElement;
    root.style.overflow = previousRootOverflow;
    root.style.paddingRight = previousRootPaddingRight;
}

/**
 * Owned Artemis modal dialog on Angular CDK overlay, part of the tum-aet-ui kit (future @tumaet/ui-angular).
 *
 * Declarative drop-in replacement for PrimeNG's `p-dialog`: same two-way `[(visible)]` control, header
 * string / projected `#header` template, projected `#footer` template, and modal mask. Matched to the Aura
 * `dialog` tokens (title 1.25rem/600, border-radius `xl` = 12px, shadow = the Aura modal shadow ≙
 * `shadow-xl`), with ONE deliberate house-style deviation: header/content/footer padding is 1rem instead of
 * the Aura 1.25rem, because the Aura value reads too airy for short prompts (review feedback on #13303).
 * Applies to every dialog, so the density stays consistent app-wide. Rides the shared kit overlay backdrop
 * (`.tum-ui-overlay-backdrop`) plus `cdkTrapFocus` and Escape-to-close, exactly like {@link TumUiPopoverComponent}.
 *
 * The panel is portaled into the CDK overlay container (equivalent to PrimeNG `appendTo="body"`), so its
 * `position: fixed`/centered geometry is immune to ancestor transforms. Content is captured in an
 * `ng-template` and attached on open; default `<ng-content>` is the body, `#header`/`#footer` templates
 * fill the header title and footer regions.
 */
@Component({
    selector: 'tum-ui-dialog',
    templateUrl: './tum-ui-dialog.component.html',
    imports: [A11yModule, NgTemplateOutlet, FaIconComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiDialogComponent implements OnDestroy {
    private readonly overlay = inject(Overlay);
    private readonly viewContainerRef = inject(ViewContainerRef);

    /** Two-way visibility, supports `[(visible)]`. Opening/closing is driven declaratively off this model. */
    readonly visible = model(false);
    /** Show a dimmed, pointer-blocking mask behind the dialog. All admin dialogs set this true. */
    readonly modal = input(true);
    /** Plain-text header title. Ignored when a `#header` template is projected. */
    readonly header = input<string>();
    /** Render the header region (title + close button). Parity with p-dialog `showHeader` (default true). */
    readonly showHeader = input(true);
    /** Show the `×` close button in the header. Parity with p-dialog `closable` (default true). */
    readonly closable = input(true);
    /** Close on Escape. Parity with p-dialog `closeOnEscape` (default true). */
    readonly closeOnEscape = input(true);
    /** Close on mask click. Parity with p-dialog `dismissableMask` (default false). */
    readonly dismissableMask = input(false);
    /**
     * Accepted for p-dialog parity but a no-op: the kit dialog is not draggable (all admin usages set
     * `[draggable]="false"`, so nothing is lost). Kept so callers can migrate without removing the binding.
     */
    readonly draggable = input(false);
    /**
     * Accepted for p-dialog parity but a no-op: the panel is always portaled into the CDK overlay container
     * (a body-level element), which is exactly what admin's `[appendTo]="'body'"` asked for.
     */
    readonly appendTo = input<string>();
    /** Style record applied to the dialog panel — supports the `[style]="{ width: '50dvw' }"` shape used in admin. */
    readonly style = input<Record<string, string>>({});
    /** Style record applied to the scrollable content region — supports `[contentStyle]="{ overflow: 'auto' }"`. */
    readonly contentStyle = input<Record<string, string>>({});
    /** Accessible name fallback used when neither `header` nor a `#header` template is present. */
    readonly ariaLabel = input<string>();
    /** Accessible label for the `×` close button. */
    readonly closeButtonAriaLabel = input('Close');
    /** ARIA role of the panel. Defaults to `'dialog'`; set `'alertdialog'` for a confirmation/alert that needs a response. */
    readonly role = input<string>('dialog');
    /** Forwarded to the panel's `aria-describedby`, e.g. to point at a message element that describes the dialog. */
    readonly ariaDescribedBy = input<string>();

    /** Emitted when the dialog becomes visible (parity with p-dialog `onShow`). */
    readonly onShow = output<void>();
    /** Emitted whenever the dialog is hidden — via `×`, Escape, mask, or the parent clearing `visible`. */
    readonly onHide = output<void>();

    private readonly panel = viewChild.required('panel', { read: TemplateRef });
    /** Projected `<ng-template #header>` (optional) — takes precedence over the `header` string. */
    protected readonly headerTemplate = contentChild('header', { read: TemplateRef });
    /** Projected `<ng-template #footer>` (optional) — the footer region renders only when present. */
    protected readonly footerTemplate = contentChild('footer', { read: TemplateRef });

    protected readonly titleId = `tum-ui-dialog-title-${nextDialogId++}`;
    protected readonly faXmark = faXmark;
    protected readonly labelledBy = computed(() => (this.showHeader() && (this.header() || this.headerTemplate()) ? this.titleId : undefined));

    private overlayRef?: OverlayRef;

    // Declarative open/close: react to the `visible` model. The effect never writes `visible`, so there is
    // no feedback loop — user-driven close paths call close() which flips the model, and this effect then
    // tears the overlay down. Open/close are idempotent (guarded on overlayRef presence).
    private readonly visibilitySync = effect(() => {
        if (this.visible()) {
            this.openOverlay();
        } else {
            this.closeOverlay();
        }
    });

    /** Close the dialog. Flips `visible` (keeping `[(visible)]` in sync); the effect disposes the overlay. */
    close(): void {
        this.visible.set(false);
    }

    private openOverlay(): void {
        if (this.overlayRef) {
            return;
        }
        this.overlayRef = this.overlay.create({
            positionStrategy: this.overlay.position().global().centerHorizontally().centerVertically(),
            // Scrolling is locked by lockPageScroll() instead.
            scrollStrategy: this.overlay.scrollStrategies.noop(),
            usePopover: false,
            hasBackdrop: true,
            // Shared kit backdrop (structural CSS in global.scss); the dim colour is applied below.
            backdropClass: 'tum-ui-overlay-backdrop',
        });
        this.overlayRef.attach(new TemplatePortal(this.panel(), this.viewContainerRef));
        lockPageScroll();
        this.applyMaskColor();
        this.overlayRef.backdropClick().subscribe(() => {
            if (this.dismissableMask()) {
                this.close();
            }
        });
        this.overlayRef.keydownEvents().subscribe((event) => {
            if (event.key === 'Escape' && this.closeOnEscape()) {
                this.close();
            }
        });
        this.onShow.emit();
    }

    private closeOverlay(): void {
        if (!this.overlayRef) {
            return;
        }
        this.overlayRef.dispose();
        this.overlayRef = undefined;
        unlockPageScroll();
        this.onHide.emit();
    }

    // Aura modal mask background, set inline because component styles cannot reach the portaled backdrop.
    private applyMaskColor(): void {
        const backdrop = this.overlayRef?.backdropElement;
        if (!backdrop) {
            return;
        }
        if (!this.modal()) {
            backdrop.style.backgroundColor = 'transparent';
            return;
        }
        const dark = document.documentElement.getAttribute('data-theme') === 'dark';
        backdrop.style.backgroundColor = dark ? 'rgba(0, 0, 0, 0.6)' : 'rgba(0, 0, 0, 0.4)';
    }

    ngOnDestroy(): void {
        this.visibilitySync.destroy();
        if (this.overlayRef) {
            this.overlayRef.dispose();
            this.overlayRef = undefined;
            unlockPageScroll();
        }
    }
}
