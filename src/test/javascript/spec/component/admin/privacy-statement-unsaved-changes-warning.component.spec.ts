import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PrivacyStatementUnsavedChangesWarningComponent } from 'app/admin/privacy-statement/unsaved-changes-warning/privacy-statement-unsaved-changes-warning.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { MockComponent } from 'ng-mocks';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTestModule } from '../../test.module';

describe('UnsavedChangesWarningComponent', () => {
    let component: PrivacyStatementUnsavedChangesWarningComponent;
    let fixture: ComponentFixture<PrivacyStatementUnsavedChangesWarningComponent>;
    let activeModal: NgbActiveModal;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [PrivacyStatementUnsavedChangesWarningComponent, MockComponent(ButtonComponent)],
        }).compileComponents();

        fixture = TestBed.createComponent(PrivacyStatementUnsavedChangesWarningComponent);
        component = fixture.componentInstance;
        activeModal = TestBed.inject(NgbActiveModal);
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
