import { Component, inject, input, output } from '@angular/core';
import { DialogService } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { ButtonSize, ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { CsvExportOptions, ExportModalComponent, ExportModalResult } from 'app/shared-ui/export/modal/export-modal.component';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';

@Component({
    selector: 'jhi-csv-export-button',
    template: `
        <jhi-button [btnType]="ButtonType.PRIMARY" [btnSize]="buttonSize()" [icon]="icon()" [disabled]="disabled()" [title]="title()" (onClick)="openExportModal($event)" />
    `,
    imports: [ButtonComponent],
})
export class ExportButtonComponent {
    private dialogService = inject(DialogService);
    private translateService = inject(TranslateService);

    ButtonType = ButtonType;
    ButtonSize = ButtonSize;

    // Defaults match the child <jhi-button> input types (string / boolean) and the original behavior
    // where an unbound title rendered empty and an unbound disabled was falsy.
    title = input<string>('');
    disabled = input<boolean>(false);
    buttonSize = input<ButtonSize>(ButtonSize.MEDIUM);
    icon = input<IconProp>();

    // Emits the chosen CSV options for a CSV export, or `undefined` for an Excel export.
    // A dismissed/cancelled dialog does not emit. This preserves the original NgbModal contract
    // where confirming Excel resolved with `undefined` and dismissing rejected (no emit).
    onExport = output<CsvExportOptions | undefined>();

    /**
     * Open up export option modal
     * @param {Event} event - Mouse Event which invoked the opening
     */
    openExportModal(event: MouseEvent) {
        event.stopPropagation();
        const dialogRef = this.dialogService.open(ExportModalComponent, {
            header: this.translateService.instant('export.dialogTitle'),
            width: '50rem',
            modal: true,
            closable: true,
            closeOnEscape: true,
            // dismissableMask false reproduces the original NgbModal `backdrop: 'static'` (backdrop click does not dismiss).
            dismissableMask: false,
            // The original NgbModal dialog was neither draggable nor resizable; make that explicit.
            draggable: false,
            resizable: false,
        });
        dialogRef?.onClose.subscribe((result: ExportModalResult | undefined) => {
            if (!result) {
                // dialog dismissed/cancelled
                return;
            }
            if (result.type === 'csv') {
                this.onExport.emit(result.options);
            } else {
                // Excel export: preserve original contract of emitting `undefined`
                this.onExport.emit(undefined);
            }
        });
    }
}
