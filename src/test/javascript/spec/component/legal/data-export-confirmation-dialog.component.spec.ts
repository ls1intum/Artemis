import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { NgbActiveModal, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { DataExportConfirmationDialogComponent } from 'app/core/legal/data-export/confirmation/data-export-confirmation-dialog.component';
import { AlertService } from 'app/core/util/alert.service';
import { ArtemisTestModule } from '../../test.module';
import { AlertOverlayComponent } from 'app/shared/alert/alert-overlay.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockDirective, MockPipe } from 'ng-mocks';
import { DebugElement, EventEmitter } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { Observable, Subject } from 'rxjs';
import { ConfirmEntityNameComponent } from 'app/shared/confirm-entity-name/confirm-entity-name.component';

describe('DataExportConfirmationDialogComponent', () => {
    let comp: DataExportConfirmationDialogComponent;
    let fixture: ComponentFixture<DataExportConfirmationDialogComponent>;
    let debugElement: DebugElement;
    let ngbActiveModal: NgbActiveModal;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ReactiveFormsModule, FormsModule, NgbModule],
            declarations: [
                DataExportConfirmationDialogComponent,
                AlertOverlayComponent,
                ConfirmEntityNameComponent,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(TranslateDirective),
            ],
            providers: [JhiLanguageHelper, AlertService],
        }).compileComponents();
        fixture = TestBed.createComponent(DataExportConfirmationDialogComponent);
        comp = fixture.componentInstance;
        debugElement = fixture.debugElement;
        ngbActiveModal = TestBed.inject(NgbActiveModal);
    });

    it('should initialize dialog correctly', () => {
        const closeSpy = jest.spyOn(ngbActiveModal, 'close');
        comp.adminDialog = true;
        comp.expectedLogin = 'login';
        comp.expectedLoginOfOtherUser = 'other login';
        comp.dialogError = new Observable<string>();
        fixture.detectChanges();
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

    it('should correctly enable and disable request button', fakeAsync(async () => {
        // Form can't be submitted if the expected login doesn't match the entered login
        comp.expectedLogin = 'login';
        comp.enteredLogin = '';
        comp.dialogError = new Observable<string>();
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        expect(comp.dataExportConfirmationForm.invalid).toBeTrue();
        expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBeTrue();

        // User entered incorrect login --> button is disabled
        comp.enteredLogin = 'my login';
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        expect(comp.dataExportConfirmationForm.invalid).toBeTrue();
        expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBeTrue();

        // User entered correct login --> button is enabled
        comp.enteredLogin = 'login';
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        expect(comp.dataExportConfirmationForm.invalid).toBeFalse();
        expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBeFalse();
    }));

    it('should handle dialog error events correctly', () => {
        comp.enteredLogin = 'login';
        comp.expectedLogin = 'login';
        const dialogErrorSource = new Subject<string>();
        comp.dialogError = dialogErrorSource.asObservable();
        comp.dataExportRequest = new EventEmitter<void>();
        fixture.detectChanges();
        let confirmButton = debugElement.query(By.css('.btn.btn-primary'));
        expect(confirmButton.nativeElement.disabled).toBeFalse();

        // external component request method was executed
        comp.confirmDataExportRequest();
        fixture.detectChanges();
        confirmButton = debugElement.query(By.css('.btn.btn-primary'));
        expect(confirmButton.nativeElement.disabled).toBeTrue();

        // external component emits error to the dialog
        dialogErrorSource.next('example error');
        fixture.detectChanges();
        confirmButton = debugElement.query(By.css('.btn.btn-primary'));
        expect(confirmButton.nativeElement.disabled).toBeFalse();

        // external component completed request method successfully
        const clearStub = jest.spyOn(comp, 'clear');
        clearStub.mockReturnValue();
        dialogErrorSource.next('');
        expect(clearStub).toHaveBeenCalledOnce();
    });

    it.each([true, false])('should set the correct translation strings and values on checkbox change', (checkboxChecked: boolean) => {
        comp.expectedLogin = 'login';
        comp.expectedLoginOfOtherUser = 'other login';
        if (checkboxChecked) {
            comp.ownLogin = '';
            comp.onRequestDataExportForOtherUserChanged({ target: { checked: checkboxChecked } });

            expect(comp.expectedLogin).toBe('other login');
            expect(comp.ownLogin).toBe('login');
            expect(comp.confirmationTextHint).toBe('artemisApp.dataExport.typeUserLoginToConfirm');
            expect(comp.enteredLogin).toBe('');
        } else {
            comp.ownLogin = 'login';
            comp.onRequestDataExportForOtherUserChanged({ target: { checked: checkboxChecked } });
            expect(comp.enteredLogin).toBe('');
            expect(comp.confirmationTextHint).toBe('artemisApp.dataExport.typeLoginToConfirm');
            expect(comp.expectedLogin).toBe('login');
            expect(comp.expectedLoginOfOtherUser).toBe('');
        }
    });

    it('should emit correct even when clicking confirm button', () => {
        comp.expectedLogin = 'login';
        comp.dataExportRequestForAnotherUser = new EventEmitter<string>();
        const dataExportRequestForAnotherUserEmitSpy = jest.spyOn(comp.dataExportRequestForAnotherUser, 'emit');
        comp.requestForAnotherUser = true;
        comp.confirmDataExportRequest();
        expect(dataExportRequestForAnotherUserEmitSpy).toHaveBeenCalledOnce();
        expect(dataExportRequestForAnotherUserEmitSpy).toHaveBeenCalledWith('login');
        expect(comp.dataExportRequest).not.toHaveBeenCalledOnce();
        dataExportRequestForAnotherUserEmitSpy.mockReset();
        comp.dataExportRequest = new EventEmitter<void>();
        const dataExportRequestEmitSpy = jest.spyOn(comp.dataExportRequest, 'emit');
        comp.requestForAnotherUser = false;
        comp.confirmDataExportRequest();
        expect(dataExportRequestEmitSpy).toHaveBeenCalledOnce();
        expect(dataExportRequestForAnotherUserEmitSpy).not.toHaveBeenCalledOnce();
    });
});
