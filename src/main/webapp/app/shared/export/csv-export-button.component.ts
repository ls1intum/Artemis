import { Component, EventEmitter, Input, Output } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { CsvExportModalComponent, CsvExportOptions } from 'app/shared/export/csv-export-modal.component';
import { IconProp } from '@fortawesome/fontawesome-svg-core';

@Component({
    selector: 'jhi-csv-export-button',
    template: `
        <jhi-button [btnType]="ButtonType.PRIMARY" [btnSize]="buttonSize" [icon]="icon" [disabled]="disabled" [title]="title" (onClick)="openCsvExportModal($event)"></jhi-button>
    `,
})
export class CsvExportButtonComponent {
    ButtonType = ButtonType;
    ButtonSize = ButtonSize;

    @Input() title: string;
    @Input() disabled: boolean;
    @Input() buttonSize: ButtonSize = ButtonSize.MEDIUM;
    @Input() icon: IconProp;

    @Output() onExport: EventEmitter<CsvExportOptions> = new EventEmitter();

    constructor(private modalService: NgbModal) {}

    /**
     * Open up csv export option modal
     * @param {Event} event - Mouse Event which invoked the opening
     */
    openCsvExportModal(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(CsvExportModalComponent, { size: 'lg', backdrop: 'static' });
        modalRef.result.then(
            (customCsvOptions) => this.onExport.emit(customCsvOptions),
            () => {},
        );
    }
}
