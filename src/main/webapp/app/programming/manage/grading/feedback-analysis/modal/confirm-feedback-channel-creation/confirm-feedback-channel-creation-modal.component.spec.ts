import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ConfirmFeedbackChannelCreationModalComponent } from 'app/programming/manage/grading/feedback-analysis/modal/confirm-feedback-channel-creation/confirm-feedback-channel-creation-modal.component';
import { provideTranslateService } from '@ngx-translate/core';

describe('ConfirmFeedbackChannelCreationModalComponent', () => {
    let fixture: ComponentFixture<ConfirmFeedbackChannelCreationModalComponent>;
    let component: ConfirmFeedbackChannelCreationModalComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ConfirmFeedbackChannelCreationModalComponent],
            providers: [provideTranslateService()],
        }).compileComponents();

        fixture = TestBed.createComponent(ConfirmFeedbackChannelCreationModalComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('affectedStudentsCount', 42);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize with the provided affectedStudentsCount', () => {
        expect(component.affectedStudentsCount()).toBe(42);
    });

    it('should emit true when confirm is triggered', () => {
        const closedSpy = vi.fn();
        component.closed.subscribe(closedSpy);
        component.confirm();
        expect(closedSpy).toHaveBeenCalledExactlyOnceWith(true);
    });

    it('should emit false when dismiss is triggered', () => {
        const closedSpy = vi.fn();
        component.closed.subscribe(closedSpy);
        component.dismiss();
        expect(closedSpy).toHaveBeenCalledExactlyOnceWith(false);
    });

    it('should emit false when the dialog is closed with its close button', () => {
        const closedSpy = vi.fn();
        component.closed.subscribe(closedSpy);
        (document.querySelector('[role="dialog"] button[aria-label]') as HTMLButtonElement).click();
        expect(closedSpy).toHaveBeenCalledExactlyOnceWith(false);
    });
});
