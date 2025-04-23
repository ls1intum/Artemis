import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ButtonSize, ButtonType } from 'app/shared/components/button/button.component';
import { CsvExportOptions, ExportModalComponent } from 'app/shared/export/modal/export-modal.component';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { ButtonComponent } from '../../components/button/button.component';

@Component({
    selector: 'jhi-csv-export-button',
    template: ` <jhi-button [btnType]="ButtonType.PRIMARY" [btnSize]="buttonSize" [icon]="icon" [disabled]="disabled" [title]="title" (onClick)="openExportModal($event)" /> `,
    imports: [ButtonComponent],
})
export class ExportButtonComponent {
    private modalService = inject(NgbModal);

    ButtonType = ButtonType;
    ButtonSize = ButtonSize;

    @Input() title: string;
    @Input() disabled: boolean;
    @Input() buttonSize: ButtonSize = ButtonSize.MEDIUM;
    @Input() icon: IconProp;

    @Output() onExport: EventEmitter<CsvExportOptions> = new EventEmitter();

    /**
     * Open up export option modal
     * @param {Event} event - Mouse Event which invoked the opening
     */
    openExportModal(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(ExportModalComponent, { size: 'lg', backdrop: 'static' });
        modalRef.result.then(
            (customCsvOptions) => this.onExport.emit(customCsvOptions),
            () => {},
        );
    }
}
