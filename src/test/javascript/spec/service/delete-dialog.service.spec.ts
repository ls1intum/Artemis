import { getTestBed, TestBed } from '@angular/core/testing';
import { DeleteDialogService } from 'app/shared/delete-dialog/delete-dialog.service';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { ActionType, DeleteDialogData } from 'app/shared/delete-dialog/delete-dialog.model';
import { EventEmitter } from '@angular/core';
import { Observable } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../test.module';
import { MockNgbModalService } from '../helpers/mocks/service/mock-ngb-modal.service';
import { DeleteDialogComponent } from 'app/shared/delete-dialog/delete-dialog.component';
import { AlertService } from 'app/core/util/alert.service';
import { MockAlertService } from '../helpers/mocks/service/mock-alert.service';
import { MockComponent, MockModule } from 'ng-mocks';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ExerciseUpdateWarningService } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.service';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../helpers/mocks/service/mock-metis-service.service';

describe('Delete Dialog Service', () => {
    let service: DeleteDialogService;
    let modalService: MockNgbModalService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
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
        const modalSpy = jest.spyOn(modalService, 'open');
        service.openDeleteDialog(data);
        expect(modalSpy).toHaveBeenCalledTimes(1);
        expect(modalSpy).toHaveBeenCalledWith('DeleteDialogComponent');
    });
});
