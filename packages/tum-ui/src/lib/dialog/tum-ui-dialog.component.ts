import {
    ChangeDetectionStrategy,
    Component,
    OnDestroy,
    TemplateRef,
    ViewContainerRef,
    booleanAttribute,
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
import { Dialog, DialogRef, DialogRole } from '@angular/cdk/dialog';
import { Overlay } from '@angular/cdk/overlay';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faXmark } from '@fortawesome/free-solid-svg-icons';
import { TumUiTranslatePipe } from '../i18n/tum-ui-translate.pipe';

export type TumUiDialogSize = 'small' | 'medium' | 'large' | 'full';

const DIALOG_SIZE_CLASSES: Record<TumUiDialogSize, string> = {
    small: 'tum:w-[min(32rem,90dvw)]',
    medium: 'tum:w-[min(48rem,90dvw)]',
    large: 'tum:w-[min(72rem,90dvw)]',
    full: 'tum:h-[90dvh] tum:w-[90dvw]',
};

let nextDialogId = 0;

// Page scroll lock shared by all dialogs, counted so nested dialogs unlock only once the last one closes.
// CDK's own BlockScrollStrategy is deliberately not used: it pins the root with `position: fixed` and a
// negative `top`, which makes the page behind the mask jump. Overflow plus a scrollbar-width pad holds the
// page exactly where it was.
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

/** Controlled modal dialog built on Angular CDK Dialog. */
@Component({
    selector: 'tum-ui-dialog',
    templateUrl: './tum-ui-dialog.component.html',
    imports: [NgTemplateOutlet, FaIconComponent, TumUiTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiDialogComponent implements OnDestroy {
    private readonly dialog = inject(Dialog);
    private readonly overlay = inject(Overlay);
    private readonly viewContainerRef = inject(ViewContainerRef);

    /** Controlled open state; dismissal writes `false`. */
    readonly visible = model(false);
    /** Visible title and default accessible name. */
    readonly header = input<string>();
    /** Hides the header; an `ariaLabel` is then required. */
    readonly showHeader = input(true, { transform: booleanAttribute });
    /** Shows the close button without changing Escape or backdrop behavior. */
    readonly closable = input(true, { transform: booleanAttribute });
    /** Allows Escape to close the dialog. */
    readonly closeOnEscape = input(true, { transform: booleanAttribute });
    /** Allows a backdrop click to close the dialog. */
    readonly dismissableMask = input(false, { transform: booleanAttribute });
    /** Responsive dialog dimensions. Omit for content-sized dialogs. */
    readonly size = input<TumUiDialogSize>();
    /** Accessible name used when no visible header or header template is present. */
    readonly ariaLabel = input<string>();
    readonly closeButtonAriaLabel = input<string>();
    readonly role = input<DialogRole>('dialog');
    readonly ariaDescribedBy = input<string>();

    readonly shown = output<void>();
    readonly hidden = output<void>();

    private readonly panel = viewChild.required('panel', { read: TemplateRef });
    protected readonly headerTemplate = contentChild('header', { read: TemplateRef });
    protected readonly footerTemplate = contentChild('footer', { read: TemplateRef });

    protected readonly titleId = `tum-ui-dialog-title-${nextDialogId++}`;
    protected readonly faXmark = faXmark;
    protected readonly labelledBy = computed(() => (this.showHeader() && (this.header()?.trim() || this.headerTemplate()) ? this.titleId : undefined));
    protected readonly panelClasses = computed(() => {
        const base =
            'tum-ui-dialog tum:flex tum:max-h-[90dvh] tum:max-w-[90dvw] tum:flex-col tum:overflow-hidden tum:rounded-xl tum:border tum:border-border tum:bg-overlay-background tum:text-text tum:shadow-xl';
        const size = this.size();
        return size ? `${base} ${DIALOG_SIZE_CLASSES[size]}` : base;
    });

    private dialogRef?: DialogRef;

    private readonly visibilitySync = effect(() => {
        if (this.visible()) {
            this.open();
        } else {
            this.dialogRef?.close();
        }
    });

    close(): void {
        this.visible.set(false);
    }

    private open(): void {
        if (this.dialogRef) {
            return;
        }
        const ariaLabel = this.ariaLabel()?.trim();
        const labelledBy = this.labelledBy();
        if (!ariaLabel && !labelledBy) {
            throw new Error('tum-ui-dialog requires a visible header, a header template, or ariaLabel');
        }
        const ref = this.dialog.open(this.panel(), {
            viewContainerRef: this.viewContainerRef,
            // Page scrolling is held by lockPageScroll() instead — see the note on that function.
            scrollStrategy: this.overlay.scrollStrategies.noop(),
            hasBackdrop: true,
            backdropClass: 'cdk-overlay-dark-backdrop',
            disableClose: true,
            ariaModal: true,
            role: this.role(),
            ariaLabel: ariaLabel ?? null,
            ariaLabelledBy: labelledBy ?? null,
            ariaDescribedBy: this.ariaDescribedBy() ?? null,
            restoreFocus: true,
        });
        this.dialogRef = ref;
        lockPageScroll();
        ref.backdropClick.subscribe(() => {
            if (this.dismissableMask()) {
                this.close();
            }
        });
        ref.keydownEvents.subscribe((event) => {
            if (event.key === 'Escape' && this.closeOnEscape()) {
                this.close();
            }
        });
        ref.closed.subscribe(() => {
            // Released before the guard below: `closed` fires exactly once per opened dialog, so this covers the
            // destroy-while-open path too, where ngOnDestroy has already cleared `dialogRef`.
            unlockPageScroll();
            if (this.dialogRef !== ref) {
                return;
            }
            this.dialogRef = undefined;
            if (this.visible()) {
                this.visible.set(false);
            }
            this.hidden.emit();
        });
        this.shown.emit();
    }

    ngOnDestroy(): void {
        this.visibilitySync.destroy();
        const ref = this.dialogRef;
        this.dialogRef = undefined;
        ref?.close();
    }
}
