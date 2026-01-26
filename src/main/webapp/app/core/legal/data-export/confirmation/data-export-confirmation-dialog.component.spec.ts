import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { NgbActiveModal, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { DataExportConfirmationDialogComponent } from 'app/core/legal/data-export/confirmation/data-export-confirmation-dialog.component';
import { AlertService } from 'app/shared/service/alert.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { DebugElement, OutputEmitterRef } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { Observable, Subject } from 'rxjs';
import { ConfirmEntityNameComponent } from 'app/shared/confirm-entity-name/confirm-entity-name.component';

// Helper to create a mock OutputEmitterRef
function createMockOutputEmitterRef<T>(): OutputEmitterRef<T> & { emit: ReturnType<typeof vi.fn> } {
    const emitMock = vi.fn();
    return {
        emit: emitMock,
        subscribe: vi.fn(),
        destroyed: false,
        listeners: new Set(),
        errorHandler: undefined,
    } as unknown as OutputEmitterRef<T> & { emit: ReturnType<typeof vi.fn> };
}

describe('DataExportConfirmationDialogComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: DataExportConfirmationDialogComponent;
    let fixture: ComponentFixture<DataExportConfirmationDialogComponent>;
    let debugElement: DebugElement;
    let ngbActiveModal: NgbActiveModal;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [
                TranslateModule.forRoot(),
                ReactiveFormsModule,
                FormsModule,
                NgbModule,
                DataExportConfirmationDialogComponent,
                ConfirmEntityNameComponent,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(TranslateDirective),
            ],
            providers: [JhiLanguageHelper, AlertService, MockProvider(NgbActiveModal)],
        });
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(DataExportConfirmationDialogComponent);
        comp = fixture.componentInstance;
        debugElement = fixture.debugElement;
        ngbActiveModal = TestBed.inject(NgbActiveModal);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize dialog correctly', () => {
        const closeSpy = vi.spyOn(ngbActiveModal, 'close');
        comp.adminDialog.set(true);
        comp.expectedLogin.set('login');
        comp.expectedLoginOfOtherUser.set('other login');
        comp.dialogError = new Observable<string>();
        fixture.changeDetectorRef.detectChanges();
        const cancelButton = debugElement.query(By.css('.btn.btn-secondary'));
        const closeButton = debugElement.query(By.css('.btn-close'));
        expect(closeButton).not.toBeNull();
        closeButton.nativeElement.click();
        expect(closeSpy).toHaveBeenCalledOnce();
        expect(cancelButton).not.toBeNull();
        cancelButton.nativeElement.click();
        expect(closeSpy).toHaveBeenCalledTimes(2);

        const inputFormGroup = debugElement.query(By.css('.form-group'));
        expect(inputFormGroup).not.toBeNull();
    });

    it('should correctly enable and disable request button', async () => {
        // Form can't be submitted if the expected login doesn't match the entered login
        comp.expectedLogin.set('login');
        comp.enteredLogin.set('');
        comp.dialogError = new Observable<string>();
        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();
        expect(comp.dataExportConfirmationForm().invalid).toBe(true);
        expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(true);

        // User entered incorrect login --> button is disabled
        comp.enteredLogin.set('my login');
        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();
        expect(comp.dataExportConfirmationForm().invalid).toBe(true);
        expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(true);

        // User entered correct login --> button is enabled
        comp.enteredLogin.set('login');
        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();
        expect(comp.dataExportConfirmationForm().invalid).toBe(false);
        expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(false);
    });

    it('should handle dialog error events correctly', () => {
        comp.enteredLogin.set('login');
        comp.expectedLogin.set('login');
        const dialogErrorSource = new Subject<string>();
        comp.dialogError = dialogErrorSource.asObservable();
        comp.dataExportRequest = createMockOutputEmitterRef<void>();
        fixture.changeDetectorRef.detectChanges();
        let confirmButton = debugElement.query(By.css('.btn.btn-primary'));
        expect(confirmButton.nativeElement.disabled).toBe(false);

        // external component request method was executed
        comp.confirmDataExportRequest();
        fixture.changeDetectorRef.detectChanges();
        confirmButton = debugElement.query(By.css('.btn.btn-primary'));
        expect(confirmButton.nativeElement.disabled).toBe(true);

        // external component emits error to the dialog
        dialogErrorSource.next('example error');
        fixture.changeDetectorRef.detectChanges();
        confirmButton = debugElement.query(By.css('.btn.btn-primary'));
        expect(confirmButton.nativeElement.disabled).toBe(false);

        // external component completed request method successfully
        const clearStub = vi.spyOn(comp, 'clear');
        clearStub.mockReturnValue();
        dialogErrorSource.next('');
        expect(clearStub).toHaveBeenCalledOnce();
    });

    it.each([true, false])('should set the correct translation strings and values on checkbox change', (checkboxChecked: boolean) => {
        comp.expectedLogin.set('login');
        comp.expectedLoginOfOtherUser.set('other login');
        if (checkboxChecked) {
            comp.ownLogin = '';
            comp.onRequestDataExportForOtherUserChanged({ target: { checked: checkboxChecked } } as unknown as Event);

            expect(comp.expectedLogin()).toBe('other login');
            expect(comp.ownLogin).toBe('login');
            expect(comp.confirmationTextHint()).toBe('artemisApp.dataExport.typeUserLoginToConfirm');
            expect(comp.enteredLogin()).toBe('');
        } else {
            comp.ownLogin = 'login';
            comp.onRequestDataExportForOtherUserChanged({ target: { checked: checkboxChecked } } as unknown as Event);
            expect(comp.enteredLogin()).toBe('');
            expect(comp.confirmationTextHint()).toBe('artemisApp.dataExport.typeLoginToConfirm');
            expect(comp.expectedLogin()).toBe('login');
            expect(comp.expectedLoginOfOtherUser()).toBe('');
        }
    });

    it('should emit correct even when clicking confirm button', () => {
        comp.expectedLogin.set('login');
        const mockDataExportRequestForAnotherUser = createMockOutputEmitterRef<string>();
        comp.dataExportRequestForAnotherUser = mockDataExportRequestForAnotherUser;
        comp.requestForAnotherUser.set(true);
        comp.confirmDataExportRequest();
        expect(mockDataExportRequestForAnotherUser.emit).toHaveBeenCalledOnce();
        expect(mockDataExportRequestForAnotherUser.emit).toHaveBeenCalledWith('login');
        mockDataExportRequestForAnotherUser.emit.mockReset();

        const mockDataExportRequest = createMockOutputEmitterRef<void>();
        comp.dataExportRequest = mockDataExportRequest;
        comp.requestForAnotherUser.set(false);
        comp.confirmDataExportRequest();
        expect(mockDataExportRequest.emit).toHaveBeenCalledOnce();
        expect(mockDataExportRequestForAnotherUser.emit).not.toHaveBeenCalled();
    });
});
