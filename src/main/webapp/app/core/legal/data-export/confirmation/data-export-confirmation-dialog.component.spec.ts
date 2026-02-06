import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { DataExportConfirmationDialogComponent } from 'app/core/legal/data-export/confirmation/data-export-confirmation-dialog.component';
import { AlertService } from 'app/shared/service/alert.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockDirective, MockPipe } from 'ng-mocks';
import { DebugElement } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { Subject } from 'rxjs';
import { ConfirmEntityNameComponent } from 'app/shared/confirm-entity-name/confirm-entity-name.component';

describe('DataExportConfirmationDialogComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: DataExportConfirmationDialogComponent;
    let fixture: ComponentFixture<DataExportConfirmationDialogComponent>;
    let debugElement: DebugElement;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [
                TranslateModule.forRoot(),
                ReactiveFormsModule,
                FormsModule,
                DataExportConfirmationDialogComponent,
                ConfirmEntityNameComponent,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(TranslateDirective),
            ],
            providers: [JhiLanguageHelper, AlertService],
        });
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(DataExportConfirmationDialogComponent);
        comp = fixture.componentInstance;
        debugElement = fixture.debugElement;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize dialog correctly', () => {
        fixture.componentRef.setInput('adminDialog', true);
        comp.expectedLogin.set('login');
        comp.expectedLoginOfOtherUser.set('other login');
        comp.visible.set(true);
        fixture.changeDetectorRef.detectChanges();
        const cancelButton = debugElement.query(By.css('.btn.btn-secondary'));
        expect(cancelButton).not.toBeNull();
        cancelButton.nativeElement.click();
        expect(comp.visible()).toBe(false);

        const inputFormGroup = debugElement.query(By.css('.form-group'));
        expect(inputFormGroup).not.toBeNull();
    });

    it('should correctly enable and disable request button', async () => {
        // Set expectedLogin via the component input so ngOnInit picks it up correctly
        fixture.componentRef.setInput('expectedLogin', 'login');
        comp.enteredLogin.set('');
        comp.visible.set(true);
        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();
        expect(comp.dataExportConfirmationForm().invalid).toBe(true);
        let confirmButton = debugElement.query(By.css('.btn.btn-primary'));
        expect(confirmButton.nativeElement.disabled).toBe(true);

        // User entered incorrect login --> button is disabled
        comp.enteredLogin.set('my login');
        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();
        expect(comp.dataExportConfirmationForm().invalid).toBe(true);
        confirmButton = debugElement.query(By.css('.btn.btn-primary'));
        expect(confirmButton.nativeElement.disabled).toBe(true);

        // User entered correct login --> button is enabled
        comp.enteredLogin.set('login');
        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();
        expect(comp.dataExportConfirmationForm().invalid).toBe(false);
        confirmButton = debugElement.query(By.css('.btn.btn-primary'));
        expect(confirmButton.nativeElement.disabled).toBe(false);
    });

    it('should handle dialog error events correctly', () => {
        comp.enteredLogin.set('login');
        comp.expectedLogin.set('login');
        const dialogErrorSource = new Subject<string>();
        fixture.componentRef.setInput('dialogError', dialogErrorSource.asObservable());
        comp.visible.set(true);
        // detectChanges triggers ngOnInit which subscribes to the dialogError input
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

    it('should emit correct event when clicking confirm button', () => {
        comp.expectedLogin.set('login');
        const dataExportRequestForAnotherUserSpy = vi.fn();
        comp.dataExportRequestForAnotherUser.subscribe(dataExportRequestForAnotherUserSpy);
        comp.requestForAnotherUser.set(true);
        comp.confirmDataExportRequest();
        expect(dataExportRequestForAnotherUserSpy).toHaveBeenCalledOnce();
        expect(dataExportRequestForAnotherUserSpy).toHaveBeenCalledWith('login');
        dataExportRequestForAnotherUserSpy.mockReset();

        const dataExportRequestSpy = vi.fn();
        comp.dataExportRequest.subscribe(dataExportRequestSpy);
        comp.requestForAnotherUser.set(false);
        comp.confirmDataExportRequest();
        expect(dataExportRequestSpy).toHaveBeenCalledOnce();
        expect(dataExportRequestForAnotherUserSpy).not.toHaveBeenCalled();
    });
});
