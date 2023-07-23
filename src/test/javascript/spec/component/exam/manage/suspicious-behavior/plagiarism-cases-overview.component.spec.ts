import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { PlagiarismCasesOverviewComponent } from 'app/exam/manage/suspicious-behavior/plagiarism-cases-overview/plagiarism-cases-overview.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { MockRouter } from '../../../../helpers/mocks/mock-router';
import { Exercise } from 'app/entities/exercise.model';

describe('PlagiarismCasesOverviewComponent', () => {
    let component: PlagiarismCasesOverviewComponent;
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
            declarations: [PlagiarismCasesOverviewComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                {
                    provide: Router,
                    useClass: MockRouter,
                },
            ],
        });
        fixture = TestBed.createComponent(PlagiarismCasesOverviewComponent);
        component = fixture.componentInstance;
        router = TestBed.inject(Router);
        component.courseId = 1;
        component.examId = 2;
        component.exercises = [exercise1, exercise2];
        component.plagiarismCasesPerExercise = new Map([
            [exercise1, 0],
            [exercise2, 1],
        ]);
        component.plagiarismResultsPerExercise = new Map([
            [exercise1, 2],
            [exercise2, 4],
        ]);
        fixture.detectChanges();
    });

    it('should navigate to plagiarism cases on view plagiarism cases click', () => {
        component.anyPlagiarismCases = true;
        fixture.detectChanges();
        const viewCasesButton = fixture.debugElement.nativeElement.querySelector('#view-plagiarism-cases-btn');
        viewCasesButton.click();
        expect(router.navigate).toHaveBeenCalledWith(['/course-management', 1, 'exams', 2, 'plagiarism-cases']);
    });

    it('should not show view cases button if no cases exist', () => {
        component.anyPlagiarismCases = false;
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
