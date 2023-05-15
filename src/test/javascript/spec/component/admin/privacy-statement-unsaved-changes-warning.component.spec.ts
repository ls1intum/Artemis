import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PrivacyStatementUnsavedChangesWarningComponent } from 'app/admin/privacy-statement/unsaved-changes-warning/privacy-statement-unsaved-changes-warning.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { MockComponent } from 'ng-mocks';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTestModule } from '../../test.module';

describe('UnsavedChangesWarningComponent', () => {
    let fixture: ComponentFixture<PrivacyStatementUnsavedChangesWarningComponent>;
    let activeModal: NgbActiveModal;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [PrivacyStatementUnsavedChangesWarningComponent, MockComponent(ButtonComponent)],
        }).compileComponents();

        fixture = TestBed.createComponent(PrivacyStatementUnsavedChangesWarningComponent);
        activeModal = TestBed.inject(NgbActiveModal);
        fixture.detectChanges();
    });
    it('should close modal on clicking yes', () => {
        jest.spyOn(activeModal, 'close');
        fixture.nativeElement.querySelector('#discard-content-btn').click();
        expect(activeModal.close).toHaveBeenCalledOnce();
    });
    it('should dismiss modal on clicking no', () => {
        jest.spyOn(activeModal, 'dismiss');
        fixture.nativeElement.querySelector('#continue-editing-btn').click();
        expect(activeModal.dismiss).toHaveBeenCalledOnce();
    });
    it('should dismiss modal on clicking close', () => {
        jest.spyOn(activeModal, 'dismiss');
        fixture.nativeElement.querySelector('#close-modal-btn').click();
        expect(activeModal.dismiss).toHaveBeenCalledOnce();
    });
});
