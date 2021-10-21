import { getTestBed, TestBed } from '@angular/core/testing';
import { DeleteDialogService } from 'app/shared/delete-dialog/delete-dialog.service';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ActionType, DeleteDialogData } from 'app/shared/delete-dialog/delete-dialog.model';
import { EventEmitter } from '@angular/core';
import { Observable } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { MockNgbModalService } from '../helpers/mocks/service/mock-ngb-modal.service';
import { DeleteDialogComponent } from 'app/shared/delete-dialog/delete-dialog.component';
import { MockComponent, MockModule, MockPipe } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('Delete Dialog Service', () => {
    let service: DeleteDialogService;
    let modalService: NgbModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(FormsModule)],
            providers: [
                { provide: DeleteDialogService, useClass: DeleteDialogService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: NgbModal, useClass: MockNgbModalService },
            ],
            declarations: [DeleteDialogComponent, MockComponent(FaIconComponent), MockComponent(AlertComponent), MockComponent(TranslateDirective), MockPipe(ArtemisTranslatePipe)],
        });

        const injector = getTestBed();
        service = injector.get(DeleteDialogService);
        modalService = injector.get(NgbModal);
    });

    it('should open delete dialog', () => {
        expect(service.modalRef).toBe(undefined);
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
        const modalSpy = jest.spyOn(modalService, 'open').mockReturnValue(<NgbModalRef>{ componentInstance, result });
        service.openDeleteDialog(data);
        expect(modalSpy).toHaveBeenCalledTimes(1);
        expect(modalSpy).toHaveBeenCalledWith(DeleteDialogComponent, { size: 'lg', backdrop: 'static' });
    });
});
