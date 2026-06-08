import { Component, inject, input, output } from '@angular/core';
import { DialogService } from 'primeng/dynamicdialog';
import { ButtonSize, ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { CsvExportOptions, ExportDialogCloseResult, ExportModalComponent, isExportDialogCancelledResult } from 'app/shared-ui/export/modal/export-modal.component';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';

@Component({
    selector: 'jhi-csv-export-button',
    template: ` <jhi-button
        [btnType]="ButtonType.PRIMARY"
        [btnSize]="buttonSize()"
        [icon]="icon()"
        [disabled]="disabled()"
        [title]="title()"
        (onClick)="openExportModal($event)"
    />`,
    imports: [ButtonComponent],
})
export class ExportButtonComponent {
    private dialogService = inject(DialogService);

    ButtonType = ButtonType;
    ButtonSize = ButtonSize;

    title = input<string>('');
    disabled = input<boolean>(false);
    buttonSize = input<ButtonSize>(ButtonSize.MEDIUM);
    icon = input<IconProp>();

    onExport = output<CsvExportOptions | undefined>();

    /**
     * Open up export option modal
     * @param {Event} event - Mouse Event which invoked the opening
     */
    openExportModal(event: MouseEvent) {
        event.stopPropagation();
        const dialogRef = this.dialogService.open(ExportModalComponent, {
            width: '50rem',
            modal: true,
            closable: false,
            closeOnEscape: false,
            dismissableMask: false,
        });
        dialogRef?.onClose.subscribe((result: ExportDialogCloseResult) => {
            if (!isExportDialogCancelledResult(result)) {
                this.onExport.emit(result);
            }
        });
    }
}
