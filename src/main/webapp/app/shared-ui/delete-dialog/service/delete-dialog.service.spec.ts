import { vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { DeleteDialogService } from 'app/shared-ui/delete-dialog/service/delete-dialog.service';
import { TumAetUiDialogRef, TumAetUiDialogService } from '@tumaet/ui-angular';
import { ActionType, DeleteDialogData } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { ApplicationRef, EventEmitter } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { DeleteDialogComponent } from 'app/shared-ui/delete-dialog/component/delete-dialog.component';
import { ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { AlertService } from 'app/foundation/service/alert.service';

describe('Delete Dialog Service', () => {
    let service: DeleteDialogService;
    let dialogService: TumAetUiDialogService;
    let alertService: AlertService;

    let mockDialogRef: TumAetUiDialogRef;

    beforeEach(() => {
        mockDialogRef = new TumAetUiDialogRef(vi.fn(), vi.fn());
        TestBed.configureTestingModule({
            providers: [
                DeleteDialogService,
                { provide: TranslateService, useClass: MockTranslateService },
                {
                    provide: TumAetUiDialogService,
                    useValue: {
                        open: vi.fn().mockReturnValue(mockDialogRef),
                    },
                },
                {
                    provide: AlertService,
                    useValue: {
                        closeAll: vi.fn(),
                        error: vi.fn(),
                    },
                },
            ],
        });
        service = TestBed.inject(DeleteDialogService);
        dialogService = TestBed.inject(TumAetUiDialogService);
        alertService = TestBed.inject(AlertService);
    });

    afterEach(() => {
        vi.resetAllMocks();
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
            delete: new EventEmitter<Record<string, boolean>>(),
            requireConfirmationOnlyForAdditionalChecks: false,
        };
        const openDialogSpy = vi.spyOn(dialogService, 'open');
        service.openDeleteDialog(data);
        expect(openDialogSpy).toHaveBeenCalledOnce();
        expect(openDialogSpy).toHaveBeenCalledWith(
            DeleteDialogComponent,
            expect.objectContaining({
                header: expect.any(String),
                size: 'medium',
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
            delete: new EventEmitter<Record<string, boolean>>(),
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
            delete: new EventEmitter<Record<string, boolean>>(),
            requireConfirmationOnlyForAdditionalChecks: false,
        };

        service.openDeleteDialog(data);

        // Emit empty string (success)
        dialogErrorSource.next('');

        // Error should NOT be displayed
        expect(alertService.error).not.toHaveBeenCalled();
    });
});

async function renderDialogs() {
    TestBed.tick();
    await TestBed.inject(ApplicationRef).whenStable();
    TestBed.tick();
}

describe('DeleteDialogService with the shared dialog host', () => {
    let service: DeleteDialogService;
    let alertService: AlertService;
    let data: DeleteDialogData;
    let errors: Subject<string>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useValue: { closeAll: vi.fn(), error: vi.fn() } },
            ],
        });
        service = TestBed.inject(DeleteDialogService);
        alertService = TestBed.inject(AlertService);
        errors = new Subject<string>();
        data = {
            entityTitle: 'Lecture Alpha',
            deleteQuestion: 'artemisApp.lecture.delete.question',
            translateValues: {},
            deleteConfirmationText: 'artemisApp.lecture.delete.typeNameToConfirm',
            actionType: ActionType.Delete,
            buttonType: ButtonType.ERROR,
            delete: vi.fn<(values: Record<string, boolean>) => void>(),
            dialogError: errors.asObservable(),
            requireConfirmationOnlyForAdditionalChecks: false,
        };
    });

    afterEach(() => TestBed.inject(TumAetUiDialogService).closeAll());

    it('requires the entity name before confirmation and retains the error callback after closing', async () => {
        service.openDeleteDialog(data);
        await renderDialogs();
        const dialog = document.querySelector<HTMLElement>('[role="dialog"]')!;
        const confirm = dialog.querySelector<HTMLButtonElement>('[data-testid="delete-dialog-confirm-button"]')!;
        expect(confirm.disabled).toBe(true);
        const input = dialog.querySelector<HTMLInputElement>('#confirm-entity-name')!;
        input.value = 'Another lecture';
        input.dispatchEvent(new Event('input', { bubbles: true }));
        await renderDialogs();
        expect(confirm.disabled).toBe(true);
        input.value = 'Lecture Alpha';
        input.dispatchEvent(new Event('input', { bubbles: true }));
        await renderDialogs();
        expect(confirm.disabled).toBe(false);
        confirm.click();
        await renderDialogs();
        expect(data.delete).toHaveBeenCalledExactlyOnceWith({});
        expect(document.querySelector('[role="dialog"]')).toBeNull();
        errors.next('Delete failed');
        expect(alertService.error).toHaveBeenCalledExactlyOnceWith('Delete failed');
        errors.next('Ignored repeated result');
        expect(alertService.error).toHaveBeenCalledOnce();
    });

    it('emits dismissal without invoking deletion and releases the dialog reference', async () => {
        service.openDeleteDialog(data);
        await renderDialogs();
        const dismissed = vi.fn();
        const completed = vi.fn();
        service.dialogRef()!.onClose.subscribe({ next: dismissed, complete: completed });
        service.dialogRef()!.close();
        await renderDialogs();
        expect(dismissed).toHaveBeenCalledOnce();
        expect(completed).toHaveBeenCalledOnce();
        expect(data.delete).not.toHaveBeenCalled();
        expect(service.dialogRef()).toBeUndefined();
        expect(document.querySelector('[role="dialog"]')).toBeNull();
    });

    it('keeps the current reference when an older confirmation closes', async () => {
        service.openDeleteDialog(data);
        await renderDialogs();
        const first = service.dialogRef()!;
        service.openDeleteDialog(data);
        await renderDialogs();
        const second = service.dialogRef()!;
        first.close();
        await renderDialogs();
        expect(service.dialogRef()).toBe(second);
        expect(document.querySelectorAll('[role="dialog"]')).toHaveLength(1);
        second.close();
        await renderDialogs();
        expect(document.querySelector('[role="dialog"]')).toBeNull();
    });

    it('does not report success as an error', async () => {
        service.openDeleteDialog(data);
        await renderDialogs();
        errors.next('');
        expect(alertService.error).not.toHaveBeenCalled();
        service.dialogRef()!.close();
        await renderDialogs();
    });
});
