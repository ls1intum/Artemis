import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExamModePickerComponent } from 'app/exam/manage/exams/exam-mode-picker/exam-mode-picker.component';
import { ExamType } from 'app/exam/shared/entities/exam.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

const exam = {
    id: 2,
};
describe('ExamModePickerComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ExamModePickerComponent;
    let fixture: ComponentFixture<ExamModePickerComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExamModePickerComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamModePickerComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should be in readonly mode', () => {
        const examCopy = { ...exam };
        fixture.componentRef.setInput('exam', exam);
        fixture.componentRef.setInput('disableInput', true);
        fixture.detectChanges();
        component.setExamMode(true);
        expect(component.exam()).toEqual(examCopy);
    });

    it('should set exam mode test', () => {
        fixture.componentRef.setInput('exam', exam);
        fixture.componentRef.setInput('disableInput', false);
        fixture.detectChanges();
        component.setExamMode(true);
        expect(component.exam().examType).toBe(ExamType.PRACTICE);
        expect(component.exam().numberOfCorrectionRoundsInExam).toBe(0);
    });

    it('should set exam mode test false', () => {
        fixture.componentRef.setInput('exam', exam);
        fixture.componentRef.setInput('disableInput', false);
        fixture.detectChanges();
        component.setExamMode(false);
        expect(component.exam().examType).toBe(ExamType.REAL);
        expect(component.exam().numberOfCorrectionRoundsInExam).toBe(1);
    });
});
