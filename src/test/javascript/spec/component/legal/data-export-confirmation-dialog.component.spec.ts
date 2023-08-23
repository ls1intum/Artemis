import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { DataExportConfirmationDialogComponent } from 'app/core/legal/data-export/confirmation/data-export-confirmation-dialog.component';
import { AlertService } from 'app/core/util/alert.service';
import { ArtemisTestModule } from '../../test.module';
import { AlertOverlayComponent } from 'app/shared/alert/alert-overlay.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockDirective, MockPipe } from 'ng-mocks';
import { EventEmitter } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';

//TODO finish tests

describe('DataExportConfirmationDialogComponent', () => {
    let comp: DataExportConfirmationDialogComponent;
    let fixture: ComponentFixture<DataExportConfirmationDialogComponent>;
    //let debugElement: DebugElement;
    //let ngbActiveModal: NgbActiveModal;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, FormsModule, NgbModule],
            declarations: [DataExportConfirmationDialogComponent, AlertOverlayComponent, MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)],
            providers: [JhiLanguageHelper, AlertService],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(DataExportConfirmationDialogComponent);
                comp = fixture.componentInstance;
                // debugElement = fixture.debugElement;
                // ngbActiveModal = TestBed.inject(NgbActiveModal);
            });
    });

    // TODO: fix tests
    it('Dialog is correctly initialized', fakeAsync(() => {
        // const closeSpy = jest.spyOn(ngbActiveModal, 'close');
        // let inputFormGroup = debugElement.query(By.css('.form-group'));
        // expect(inputFormGroup).toBeNull();
        //
        // const modalTitle = fixture.debugElement.query(By.css('.modal-title'));
        // expect(modalTitle).not.toBeNull();
        // comp.entityTitle = 'title';
        // comp.deleteQuestion = 'artemisApp.exercise.delete.question';
        // comp.deleteConfirmationText = 'artemisApp.exercise.delete.typeNameToConfirm';
        // comp.dialogError = new Observable<string>();
        // comp.buttonType = ButtonType.ERROR;
        // fixture.detectChanges();
        //
        // const closeButton = fixture.debugElement.query(By.css('.btn-close'));
        // expect(closeButton).not.toBeNull();
        // closeButton.nativeElement.click();
        // expect(closeSpy).toHaveBeenCalledOnce();
        // expect(cancelButton).not.toBeNull();
        // cancelButton.nativeElement.click();
        // expect(closeSpy).toHaveBeenCalledTimes(2);
        //
        // inputFormGroup = debugElement.query(By.css('.form-group'));
        // expect(inputFormGroup).not.toBeNull();
    }));

    it('Form properly checked before submission', fakeAsync(() => {
        // // Form can't be submitted if there is deleteConfirmationText and user didn't input the entity title
        // comp.entityTitle = 'title';
        // comp.deleteConfirmationText = 'artemisApp.exercise.delete.typeNameToConfirm';
        // comp.confirmEntityName = '';
        // comp.dialogError = new Observable<string>();
        // comp.buttonType = ButtonType.ERROR;
        // fixture.detectChanges();
        // let submitButton = debugElement.query(By.css('.btn.btn-danger'));
        // expect(submitButton.nativeElement.disabled).toBeTrue();
        //
        // // User entered some title
        // comp.confirmEntityName = 'some title';
        // fixture.detectChanges();
        // submitButton = debugElement.query(By.css('.btn.btn-danger'));
        // expect(submitButton.nativeElement.disabled).toBeTrue();
        //
        // // User entered correct tile
        // comp.confirmEntityName = 'title';
        // expect(comp.confirmEntityName).toBe('title');
        // fixture.detectChanges();
        // submitButton = debugElement.query(By.css('.btn.btn-danger'));
        // expect(submitButton.nativeElement.disabled).toBeFalse();
    }));

    it('Error dialog events are correctly handled', fakeAsync(() => {}));

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
