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
import { Dialog, DialogRef, DialogRole } from '@angular/cdk/dialog';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faXmark } from '@fortawesome/free-solid-svg-icons';
import { TumUiTranslatePipe } from '../i18n/tum-ui-translate.pipe';

let nextDialogId = 0;

/** Controlled modal dialog built on Angular CDK Dialog. */
@Component({
    selector: 'tum-ui-dialog',
    templateUrl: './tum-ui-dialog.component.html',
    imports: [NgTemplateOutlet, FaIconComponent, TumUiTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiDialogComponent implements OnDestroy {
    private readonly dialog = inject(Dialog);
    private readonly viewContainerRef = inject(ViewContainerRef);

    /** Controlled open state; dismissal writes `false`. */
    readonly visible = model(false);
    /** Visible title and default accessible name. */
    readonly header = input<string>();
    /** Hides the header; an `ariaLabel` is then required. */
    readonly showHeader = input(true);
    /** Shows the close button without changing Escape or backdrop behavior. */
    readonly closable = input(true);
    /** Allows Escape to close the dialog. */
    readonly closeOnEscape = input(true);
    /** Allows a backdrop click to close the dialog. */
    readonly dismissableMask = input(false);
    readonly style = input<Record<string, string>>({});
    readonly contentStyle = input<Record<string, string>>({});
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
