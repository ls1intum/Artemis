import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { PlagiarismCasesOverviewComponent } from 'app/exam/manage/suspicious-behavior/plagiarism-cases-overview/plagiarism-cases-overview.component';
import { Exercise } from 'app/entities/exercise.model';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockRouter } from '../../../../helpers/mocks/mock-router';

describe('PlagiarismCasesOverviewComponent', () => {
    let fixture: ComponentFixture<PlagiarismCasesOverviewComponent>;
    let router: Router;
    const exercise1 = {
        type: 'text',
        id: 1,
        exerciseGroup: {
            id: 1,
            exam: {
                id: 1,
                course: {
                    id: 1,
                },
            },
        },
    } as Exercise;
    const exercise2 = {
        type: 'modeling',
        id: 2,
        exerciseGroup: {
            id: 2,
            exam: {
                id: 2,
                course: {
                    id: 2,
                },
            },
        },
    } as Exercise;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
            ],
        });
        fixture = TestBed.createComponent(PlagiarismCasesOverviewComponent);
        router = TestBed.inject(Router);
        fixture.componentRef.setInput('courseId', 1);
        fixture.componentRef.setInput('examId', 2);
        fixture.componentRef.setInput('exercises', [exercise1, exercise2]);
        fixture.componentRef.setInput(
            'plagiarismCasesPerExercise',
            new Map([
                [exercise1, 0],
                [exercise2, 1],
            ]),
        );
        fixture.componentRef.setInput(
            'plagiarismResultsPerExercise',
            new Map([
                [exercise1, 2],
                [exercise2, 4],
            ]),
        );

        fixture.detectChanges();
    });

    it('should navigate to plagiarism cases on view plagiarism cases click', () => {
        fixture.componentRef.setInput('anyPlagiarismCases', true);
        fixture.detectChanges();
        const viewCasesButton = fixture.debugElement.nativeElement.querySelector('#view-plagiarism-cases-btn');
        viewCasesButton.click();
        expect(router.navigate).toHaveBeenCalledWith(['/course-management', 1, 'exams', 2, 'plagiarism-cases']);
    });

    it('should not show view cases button if no cases exist', () => {
        fixture.componentRef.setInput('anyPlagiarismCases', false);
        fixture.detectChanges();
        const viewCasesButton = fixture.debugElement.nativeElement.querySelector('#view-plagiarism-cases-btn');
        expect(viewCasesButton).toBeNull();
    });

    it('should navigate to plagiarism results on view plagiarism results click', () => {
        const viewCasesButtonExercise1 = fixture.debugElement.nativeElement.querySelector('#view-plagiarism-results-btn-0');
        viewCasesButtonExercise1.click();
        expect(router.navigate).toHaveBeenCalledWith(['/course-management', 1, 'exams', 2, 'exercise-groups', 1, 'text-exercises', 1, 'plagiarism']);
        const viewCasesButtonExercise2 = fixture.debugElement.nativeElement.querySelector('#view-plagiarism-results-btn-1');
        viewCasesButtonExercise2.click();
        expect(router.navigate).toHaveBeenCalledWith(['/course-management', 1, 'exams', 2, 'exercise-groups', 2, 'modeling-exercises', 2, 'plagiarism']);
    });
});
