import { EventEmitter } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { DeleteDialogComponent } from 'app/shared/delete-dialog/delete-dialog.component';
import { ActionType, DeleteDialogData } from 'app/shared/delete-dialog/delete-dialog.model';
import { DeleteDialogService } from 'app/shared/delete-dialog/delete-dialog.service';
import { MockComponent } from 'ng-mocks';
import { Observable } from 'rxjs';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';

describe('Delete Dialog Service', () => {
    let service: DeleteDialogService;
    let modalService: NgbModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            providers: [DeleteDialogService, { provide: TranslateService, useClass: MockTranslateService }],
            declarations: [MockComponent(DeleteDialogComponent)],
        });
        service = TestBed.inject(DeleteDialogService);
        modalService = TestBed.inject(NgbModal);
    });

    afterEach(() => {
        jest.resetAllMocks();
    });

    it('should open delete dialog', () => {
        expect(service.modalRef).toBeUndefined();
        const data: DeleteDialogData = {
            dialogError: new Observable<string>(),
            entityTitle: 'title',
            deleteQuestion: 'artemisApp.exercise.delete.question',
            deleteConfirmationText: 'artemisApp.exercise.delete.typeNameToConfirm',
            actionType: ActionType.Delete,
            delete: new EventEmitter<any>(),
            requireConfirmationOnlyForAdditionalChecks: false,
        };
        const componentInstance = {};
        const result = new Promise((resolve) => resolve({}));
        const openModalStub = jest.spyOn(modalService, 'open').mockReturnValue(<NgbModalRef>{
            componentInstance,
            result,
        });
        service.openDeleteDialog(data);
        expect(openModalStub).toHaveBeenCalledOnce();
        expect(openModalStub).toHaveBeenCalledWith(DeleteDialogComponent, { size: 'lg', backdrop: 'static' });
    });
});
