import { DebugElement, EventEmitter } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, flush } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import '@angular/localize/init';
import { By } from '@angular/platform-browser';
import { NgbActiveModal, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateModule } from '@ngx-translate/core';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { AlertService } from 'app/core/util/alert.service';
import { AlertOverlayComponent } from 'app/shared/alert/alert-overlay.component';
import { DeleteDialogComponent } from 'app/shared/delete-dialog/delete-dialog.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockDirective, MockPipe } from 'ng-mocks';
import { Observable, Subject } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';

describe('DeleteDialogComponent', () => {
    let comp: DeleteDialogComponent;
    let fixture: ComponentFixture<DeleteDialogComponent>;
    let debugElement: DebugElement;
    let ngbActiveModal: NgbActiveModal;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, FormsModule, NgbModule],
            declarations: [DeleteDialogComponent, AlertOverlayComponent, MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)],
            providers: [JhiLanguageHelper, AlertService],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(DeleteDialogComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                ngbActiveModal = TestBed.inject(NgbActiveModal);
            });
    });

    it('Dialog is correctly initialized', fakeAsync(() => {
        const closeSpy = jest.spyOn(ngbActiveModal, 'close');
        let inputFormGroup = debugElement.query(By.css('.form-group'));
        expect(inputFormGroup).toBeNull();

        const modalTitle = fixture.debugElement.query(By.css('.modal-title'));
        expect(modalTitle).not.toBeNull();
        comp.entityTitle = 'title';
        comp.deleteQuestion = 'artemisApp.exercise.delete.question';
        comp.deleteConfirmationText = 'artemisApp.exercise.delete.typeNameToConfirm';
        comp.dialogError = new Observable<string>();
        fixture.detectChanges();

        const closeButton = fixture.debugElement.query(By.css('.btn-close'));
        expect(closeButton).not.toBeNull();
        closeButton.nativeElement.click();
        expect(closeSpy).toHaveBeenCalledOnce();

        const cancelButton = fixture.debugElement.query(By.css('.btn.btn-secondary'));
        expect(cancelButton).not.toBeNull();
        cancelButton.nativeElement.click();
        expect(closeSpy).toHaveBeenCalledTimes(2);

        inputFormGroup = debugElement.query(By.css('.form-group'));
        expect(inputFormGroup).not.toBeNull();
    }));

    it('Form properly checked before submission', fakeAsync(() => {
        // Form can't be submitted if there is deleteConfirmationText and user didn't input the entity title
        comp.entityTitle = 'title';
        comp.deleteConfirmationText = 'artemisApp.exercise.delete.typeNameToConfirm';
        comp.confirmEntityName = '';
        comp.dialogError = new Observable<string>();
        fixture.detectChanges();
        let submitButton = debugElement.query(By.css('.btn.btn-danger'));
        expect(submitButton.nativeElement.disabled).toBeTrue();

        // User entered some title
        comp.confirmEntityName = 'some title';
        fixture.detectChanges();
        submitButton = debugElement.query(By.css('.btn.btn-danger'));
        expect(submitButton.nativeElement.disabled).toBeTrue();

        // User entered correct tile
        comp.confirmEntityName = 'title';
        expect(comp.confirmEntityName).toBe('title');
        fixture.detectChanges();
        submitButton = debugElement.query(By.css('.btn.btn-danger'));
        expect(submitButton.nativeElement.disabled).toBeFalse();
    }));

    it('Error dialog events are correctly handled', fakeAsync(() => {
        comp.entityTitle = 'title';
        comp.deleteConfirmationText = 'artemisApp.exercise.delete.typeNameToConfirm';
        comp.confirmEntityName = 'title';
        const dialogErrorSource = new Subject<string>();
        comp.dialogError = dialogErrorSource.asObservable();
        comp.delete = new EventEmitter<{ [p: string]: boolean }>();
        fixture.detectChanges();
        let deleteButton = debugElement.query(By.css('.btn.btn-danger'));
        expect(deleteButton.nativeElement.disabled).toBeFalse();

        // external component delete method was executed
        comp.confirmDelete();
        fixture.detectChanges();
        deleteButton = debugElement.query(By.css('.btn.btn-danger'));
        expect(deleteButton.nativeElement.disabled).toBeTrue();

        // external component emits error to the dialog
        dialogErrorSource.next('example error');
        fixture.detectChanges();
        deleteButton = debugElement.query(By.css('.btn.btn-danger'));
        expect(deleteButton.nativeElement.disabled).toBeFalse();

        // external component completed delete method successfully
        const clearStub = jest.spyOn(comp, 'clear');
        clearStub.mockReturnValue();
        dialogErrorSource.next('');
        expect(clearStub).toHaveBeenCalledOnce();

        fixture.destroy();
        flush();
    }));
});
