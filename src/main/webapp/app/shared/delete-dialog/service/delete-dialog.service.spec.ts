import { TestBed } from '@angular/core/testing';
import { DeleteDialogService } from 'app/shared/delete-dialog/service/delete-dialog.service';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { ActionType, DeleteDialogData } from 'app/shared/delete-dialog/delete-dialog.model';
import { EventEmitter } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { DeleteDialogComponent } from 'app/shared/delete-dialog/component/delete-dialog.component';
import { ButtonType } from 'app/shared/components/buttons/button/button.component';
import { AlertService } from 'app/shared/service/alert.service';

describe('Delete Dialog Service', () => {
    let service: DeleteDialogService;
    let dialogService: DialogService;
    let alertService: AlertService;

    const mockDialogRef = {
        onClose: new Subject<void>(),
        close: jest.fn(),
    } as unknown as DynamicDialogRef;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                DeleteDialogService,
                { provide: TranslateService, useClass: MockTranslateService },
                {
                    provide: DialogService,
                    useValue: {
                        open: jest.fn().mockReturnValue(mockDialogRef),
                    },
                },
                {
                    provide: AlertService,
                    useValue: {
                        closeAll: jest.fn(),
                        error: jest.fn(),
                    },
                },
            ],
        });
        service = TestBed.inject(DeleteDialogService);
        dialogService = TestBed.inject(DialogService);
        alertService = TestBed.inject(AlertService);
    });

    afterEach(() => {
        jest.resetAllMocks();
    });

    it('should open delete dialog', () => {
        expect(service.dialogRef()).toBeFalsy();
        const data: DeleteDialogData = {
            dialogError: new Observable<string>(),
            entityTitle: 'title',
            deleteQuestion: 'artemisApp.exercise.delete.question',
            translateValues: {},
            deleteConfirmationText: 'artemisApp.exercise.delete.typeNameToConfirm',
            actionType: ActionType.Delete,
            buttonType: ButtonType.ERROR,
            delete: new EventEmitter<any>(),
            requireConfirmationOnlyForAdditionalChecks: false,
        };
        const openDialogSpy = jest.spyOn(dialogService, 'open');
        service.openDeleteDialog(data);
        expect(openDialogSpy).toHaveBeenCalledOnce();
        expect(openDialogSpy).toHaveBeenCalledWith(
            DeleteDialogComponent,
            expect.objectContaining({
                header: expect.any(String),
                width: '50rem',
                modal: true,
                closable: true,
                closeOnEscape: true,
                dismissableMask: false,
                data: expect.objectContaining({
                    entityTitle: 'title',
                    deleteQuestion: 'artemisApp.exercise.delete.question',
                }),
            }),
        );
    });

    it('should display error via AlertService when dialogError emits an error message', () => {
        const dialogErrorSource = new Subject<string>();
        const data: DeleteDialogData = {
            dialogError: dialogErrorSource.asObservable(),
            entityTitle: 'title',
            deleteQuestion: 'artemisApp.exercise.delete.question',
            translateValues: {},
            deleteConfirmationText: 'artemisApp.exercise.delete.typeNameToConfirm',
            actionType: ActionType.Delete,
            buttonType: ButtonType.ERROR,
            delete: new EventEmitter<any>(),
            requireConfirmationOnlyForAdditionalChecks: false,
        };

        service.openDeleteDialog(data);

        // Emit an error message
        dialogErrorSource.next('Delete failed: entity not found');

        // Error should be displayed via AlertService
        expect(alertService.error).toHaveBeenCalledOnce();
        expect(alertService.error).toHaveBeenCalledWith('Delete failed: entity not found');
    });

    it('should not display error via AlertService when dialogError emits empty string (success)', () => {
        const dialogErrorSource = new Subject<string>();
        const data: DeleteDialogData = {
            dialogError: dialogErrorSource.asObservable(),
            entityTitle: 'title',
            deleteQuestion: 'artemisApp.exercise.delete.question',
            translateValues: {},
            deleteConfirmationText: 'artemisApp.exercise.delete.typeNameToConfirm',
            actionType: ActionType.Delete,
            buttonType: ButtonType.ERROR,
            delete: new EventEmitter<any>(),
            requireConfirmationOnlyForAdditionalChecks: false,
        };

        service.openDeleteDialog(data);

        // Emit empty string (success)
        dialogErrorSource.next('');

        // Error should NOT be displayed
        expect(alertService.error).not.toHaveBeenCalled();
    });
});
