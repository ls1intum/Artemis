import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ConfirmFeedbackChannelCreationModalComponent } from 'app/programming/manage/grading/feedback-analysis/modal/confirm-feedback-channel-creation/confirm-feedback-channel-creation-modal.component';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateModule } from '@ngx-translate/core';

describe('ConfirmFeedbackChannelCreationModalComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ConfirmFeedbackChannelCreationModalComponent>;
    let component: ConfirmFeedbackChannelCreationModalComponent;
    let activeModal: NgbActiveModal;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ConfirmFeedbackChannelCreationModalComponent],
            providers: [NgbActiveModal],
        }).compileComponents();

        fixture = TestBed.createComponent(ConfirmFeedbackChannelCreationModalComponent);
        component = fixture.componentInstance;
        activeModal = TestBed.inject(NgbActiveModal);
        fixture.componentRef.setInput('affectedStudentsCount', 42);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize with the provided affectedStudentsCount', () => {
        expect(component.affectedStudentsCount()).toBe(42);
    });

    it('should call close on activeModal with true when confirm is triggered', () => {
        const closeSpy = vi.spyOn(activeModal, 'close');
        component.confirm();
        expect(closeSpy).toHaveBeenCalledExactlyOnceWith(true);
    });

    it('should call dismiss on activeModal when dismiss is triggered', () => {
        const dismissSpy = vi.spyOn(activeModal, 'dismiss');
        component.dismiss();
        expect(dismissSpy).toHaveBeenCalledOnce();
    });
});
