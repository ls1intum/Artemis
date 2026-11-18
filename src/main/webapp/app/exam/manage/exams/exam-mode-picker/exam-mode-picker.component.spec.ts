import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExamModePickerComponent } from 'app/exam/manage/exams/exam-mode-picker/exam-mode-picker.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ExamMode } from 'app/exam/shared/entities/exam-mode.model';

describe('ExamModePickerComponent', () => {
    let component: ExamModePickerComponent;
    let fixture: ComponentFixture<ExamModePickerComponent>;
    let exam: any;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExamModePickerComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamModePickerComponent);
        component = fixture.componentInstance;
        exam = { id: 2 };
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should be in readonly mode', () => {
        const examCopy = { ...exam };
        fixture.componentRef.setInput('exam', exam);
        fixture.componentRef.setInput('disableInput', true);
        fixture.detectChanges();
        component.setExamMode(ExamMode.TEST);
        expect(component.exam()).toEqual(examCopy);
    });

    it('should set exam mode test', () => {
        fixture.componentRef.setInput('exam', exam);
        fixture.componentRef.setInput('disableInput', false);
        fixture.detectChanges();
        component.setExamMode(ExamMode.TEST);
        expect(component.exam().examMode).toBe(ExamMode.TEST);
        expect(component.exam().numberOfCorrectionRoundsInExam).toBe(0);
    });

    it('should set exam mode test false', () => {
        fixture.componentRef.setInput('exam', exam);
        fixture.componentRef.setInput('disableInput', false);
        fixture.detectChanges();
        component.setExamMode(ExamMode.REAL);
        expect(component.exam().examMode).toBe(ExamMode.REAL);
        expect(component.exam().numberOfCorrectionRoundsInExam).toBe(1);
    });

    it('should set exam mode TEST_WITH_SUPERVISED_SESSION', () => {
        fixture.componentRef.setInput('exam', exam);
        fixture.componentRef.setInput('disableInput', false);
        fixture.detectChanges();
        component.setExamMode(ExamMode.TEST_WITH_SUPERVISED_SESSION);
        expect(component.exam().examMode).toBe(ExamMode.TEST_WITH_SUPERVISED_SESSION);
        expect(component.exam().numberOfCorrectionRoundsInExam).toBe(0);
    });

    it('should only offer the supervised session for a practice exam', () => {
        fixture.componentRef.setInput('exam', { id: 2, examMode: ExamMode.REAL });
        fixture.componentRef.setInput('disableInput', false);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="supervised-session-option"]')).toBeNull();

        fixture.nativeElement.querySelector('[data-testid="test-mode"]').click();
        fixture.detectChanges();
        expect(component.exam().examMode).toBe(ExamMode.TEST);
        expect(fixture.nativeElement.querySelector('[data-testid="supervised-session-option"]')).not.toBeNull();
    });

    it('should switch between practice exam and practice exam with supervised session', () => {
        fixture.componentRef.setInput('exam', { id: 2, examMode: ExamMode.TEST });
        fixture.componentRef.setInput('disableInput', false);
        const emitSpy = vi.spyOn(component.examModeChanged, 'emit');
        fixture.detectChanges();
        expect(component.hasSupervisedSession()).toBe(false);

        component.setSupervisedSession(true);
        expect(component.exam().examMode).toBe(ExamMode.TEST_WITH_SUPERVISED_SESSION);
        expect(component.hasSupervisedSession()).toBe(true);

        component.setSupervisedSession(false);
        expect(component.exam().examMode).toBe(ExamMode.TEST);
        expect(emitSpy).toHaveBeenCalledTimes(2);
    });

    it('should keep the supervised session when the practice exam is selected again', () => {
        fixture.componentRef.setInput('exam', { id: 2, examMode: ExamMode.TEST_WITH_SUPERVISED_SESSION });
        fixture.componentRef.setInput('disableInput', false);
        fixture.detectChanges();
        component.selectPracticeExam();
        expect(component.exam().examMode).toBe(ExamMode.TEST_WITH_SUPERVISED_SESSION);
    });

    it('should not change the supervised session when the input is disabled', () => {
        fixture.componentRef.setInput('exam', { id: 2, examMode: ExamMode.TEST });
        fixture.componentRef.setInput('disableInput', true);
        fixture.detectChanges();
        component.setSupervisedSession(true);
        expect(component.exam().examMode).toBe(ExamMode.TEST);
    });
});
