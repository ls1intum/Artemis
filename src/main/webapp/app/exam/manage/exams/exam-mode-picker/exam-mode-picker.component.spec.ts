import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExamModePickerComponent } from 'app/exam/manage/exams/exam-mode-picker/exam-mode-picker.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

import { ExamMode } from 'app/exam/shared/entities/exam-mode.model';

describe('ExamModePickerComponent', () => {
    setupTestBed({ zoneless: true });

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

    it('should set exam mode TEST_WITH_SIMULATION', () => {
        fixture.componentRef.setInput('exam', exam);
        fixture.componentRef.setInput('disableInput', false);
        fixture.detectChanges();
        component.setExamMode(ExamMode.TEST_WITH_SIMULATION);
        expect(component.exam().examMode).toBe(ExamMode.TEST_WITH_SIMULATION);
        expect(component.exam().numberOfCorrectionRoundsInExam).toBe(0);
    });
});
