import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExamModeBadgeComponent } from 'app/exam/shared/exam-mode-badge/exam-mode-badge.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('ExamModeBadgeComponent', () => {
    setupTestBed({ zoneless: true });

    const createComponent = (examType?: ExamType, size: 'default' | 'large' = 'default'): ComponentFixture<ExamModeBadgeComponent> => {
        TestBed.configureTestingModule({
            imports: [ExamModeBadgeComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });
        const fixture = TestBed.createComponent(ExamModeBadgeComponent);
        fixture.componentRef.setInput('examType', examType);
        fixture.componentRef.setInput('size', size);
        fixture.detectChanges();
        return fixture;
    };

    it.each([
        [ExamType.REAL, 'artemisApp.examManagement.testExam.realExam', 'bg-success'],
        [ExamType.SIMULATION, 'artemisApp.examManagement.testExam.testExamSimulation', 'bg-primary'],
        [ExamType.PRACTICE, 'artemisApp.examManagement.testExam.testExamPractice', 'bg-primary'],
        [ExamType.SIMULATION_AND_PRACTICE, 'artemisApp.examManagement.testExam.testExamSimulationAndPractice', 'bg-primary'],
        [undefined, 'artemisApp.examManagement.testExam.realExam', 'bg-success'],
    ])('should render the exam mode badge for %s', (examType, translationKey, backgroundClass) => {
        const fixture = createComponent(examType);

        const badge = fixture.nativeElement.querySelector('.badge');
        expect(badge.textContent).toContain(translationKey);
        expect(badge.classList).toContain(backgroundClass);
    });

    it('should render a larger badge', () => {
        const fixture = createComponent(ExamType.PRACTICE, 'large');

        expect(fixture.nativeElement.querySelector('.badge').classList).toContain('fs-4');
    });
});
