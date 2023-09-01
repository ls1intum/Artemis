import { TestBed } from '@angular/core/testing';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { EventEmitter } from '@angular/core';
import { Observable } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { DeleteDialogComponent } from 'app/shared/delete-dialog/delete-dialog.component';
import { MockComponent } from 'ng-mocks';
import { DataExportConfirmationDialogData } from 'app/core/legal/data-export/confirmation/data-export-confirmation-dialog.model';
import { DataExportConfirmationDialogService } from 'app/core/legal/data-export/confirmation/data-export-confirmation-dialog.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { DataExportConfirmationDialogComponent } from 'app/core/legal/data-export/confirmation/data-export-confirmation-dialog.component';

describe('DataExportConfirmationDialogService', () => {
    let service: DataExportConfirmationDialogService;
    let modalService: NgbModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [DataExportConfirmationDialogService, { provide: TranslateService, useClass: MockTranslateService }],
            declarations: [MockComponent(DeleteDialogComponent)],
        });
        service = TestBed.inject(DataExportConfirmationDialogService);
        modalService = TestBed.inject(NgbModal);
    });

    afterEach(() => {
        jest.resetAllMocks();
    });

    it('should open confirmation dialog', () => {
        expect(service.modalRef).toBeUndefined();
        const data: DataExportConfirmationDialogData = {
            dialogError: new Observable<string>(),
            userLogin: 'userLogin',
            dataExportRequest: new EventEmitter<any>(),
            dataExportRequestForAnotherUser: new EventEmitter<string>(),
            adminDialog: false,
        };
        const componentInstance = {};
        const result = new Promise((resolve) => resolve({}));
        const openModalStub = jest.spyOn(modalService, 'open').mockReturnValue(<NgbModalRef>{
            componentInstance,
            result,
        });
        service.openConfirmationDialog(data);
        expect(openModalStub).toHaveBeenCalledOnce();
        expect(openModalStub).toHaveBeenCalledWith(DataExportConfirmationDialogComponent, { size: 'lg', backdrop: 'static' });
    });
});
