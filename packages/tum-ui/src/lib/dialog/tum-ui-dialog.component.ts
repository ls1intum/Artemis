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

let nextDialogId = 0;

/** Declarative modal dialog backed by Angular CDK Dialog. */
@Component({
    selector: 'tum-ui-dialog',
    templateUrl: './tum-ui-dialog.component.html',
    imports: [NgTemplateOutlet, FaIconComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiDialogComponent implements OnDestroy {
    private readonly dialog = inject(Dialog);
    private readonly viewContainerRef = inject(ViewContainerRef);

    readonly visible = model(false);
    readonly header = input<string>();
    readonly showHeader = input(true);
    readonly closable = input(true);
    readonly closeOnEscape = input(true);
    readonly dismissableMask = input(false);
    readonly style = input<Record<string, string>>({});
    readonly contentStyle = input<Record<string, string>>({});
    readonly ariaLabel = input<string>();
    readonly closeButtonAriaLabel = input('Close');
    readonly role = input<DialogRole>('dialog');
    readonly ariaDescribedBy = input<string>();

    readonly onShow = output<void>();
    readonly onHide = output<void>();

    private readonly panel = viewChild.required('panel', { read: TemplateRef });
    protected readonly headerTemplate = contentChild('header', { read: TemplateRef });
    protected readonly footerTemplate = contentChild('footer', { read: TemplateRef });

    protected readonly titleId = `tum-ui-dialog-title-${nextDialogId++}`;
    protected readonly faXmark = faXmark;
    protected readonly labelledBy = computed(() => (this.showHeader() && (this.header() || this.headerTemplate()) ? this.titleId : undefined));

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
        const ref = this.dialog.open(this.panel(), {
            viewContainerRef: this.viewContainerRef,
            hasBackdrop: true,
            backdropClass: 'cdk-overlay-dark-backdrop',
            disableClose: true,
            ariaModal: true,
            role: this.role(),
            ariaLabel: this.ariaLabel() ?? null,
            ariaLabelledBy: this.labelledBy() ?? null,
            ariaDescribedBy: this.ariaDescribedBy() ?? null,
            autoFocus: 'dialog',
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
            this.onHide.emit();
        });
        this.onShow.emit();
    }

    ngOnDestroy(): void {
        this.visibilitySync.destroy();
        const ref = this.dialogRef;
        this.dialogRef = undefined;
        ref?.close();
    }
}
