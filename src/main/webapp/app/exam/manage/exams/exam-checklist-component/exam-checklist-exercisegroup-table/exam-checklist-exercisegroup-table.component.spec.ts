import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { Component } from '@angular/core';
import { HasAnyAuthorityDirective } from 'app/foundation/auth/has-any-authority.directive';
import { ChecklistCheckComponent } from 'app/ui/components/checklist-check/checklist-check.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ProgressBarComponent } from 'app/ui/dashboards/tutor-participation-graph/progress-bar/progress-bar.component';
import { ExamChecklistExerciseGroupTableComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist-exercisegroup-table/exam-checklist-exercisegroup-table.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ExerciseGroupVariantColumn } from 'app/exam/shared/entities/exercise-group-variant-column.model';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

@Component({
    template: '',
})
class DummyComponent {}

describe('ExamChecklistExerciseGroupTableComponent', () => {
    setupTestBed({ zoneless: true });

    let examChecklistComponentFixture: ComponentFixture<ExamChecklistExerciseGroupTableComponent>;
    let examChecklistExerciseGroupTableComponent: ExamChecklistExerciseGroupTableComponent;

    const dueDateStatArray = [{ inTime: 0, late: 0, total: 0 }];
    function getExerciseGroups(equalPoints: boolean) {
        const exerciseGroups = [
            {
                id: 1,
                exercises: [
                    {
                        id: 3,
                        title: 'A',
                        maxPoints: 101,
                        numberOfAssessmentsOfCorrectionRounds: dueDateStatArray,
                        studentAssignedTeamIdComputed: false,
                        secondCorrectionEnabled: false,
                        numberOfParticipations: 23,
                    },
                    {
                        id: 2,
                        title: 'B',
                        maxPoints: 101,
                        numberOfAssessmentsOfCorrectionRounds: dueDateStatArray,
                        studentAssignedTeamIdComputed: false,
                        secondCorrectionEnabled: false,
                        numberOfParticipations: 22,
                    },
                ],
            },
        ];
        if (!equalPoints) {
            exerciseGroups[0].exercises[0].maxPoints = 50;
        }
        return exerciseGroups;
    }

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                DummyComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockDirective(TranslateDirective),
                MockDirective(HasAnyAuthorityDirective),
                ChecklistCheckComponent,
                ExamChecklistExerciseGroupTableComponent,
                ProgressBarComponent,
                MockComponent(FaIconComponent),
            ],
            providers: [
                provideRouter([
                    { path: 'course-management/:courseId/exams/:examId/edit', component: DummyComponent },
                    { path: 'course-management/:courseId/exams/:examId/exercise-groups', component: DummyComponent },
                    { path: 'course-management/:courseId/exams/:examId/assessment-dashboard', component: DummyComponent },
                    { path: 'course-management/:courseId/exams/:examId/scores', component: DummyComponent },
                    { path: 'course-management/:courseId/exams/:examId/test-runs', component: DummyComponent },
                    { path: 'course-management/:courseId/exams/:examId/students', component: DummyComponent },
                ]),
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        examChecklistComponentFixture = TestBed.createComponent(ExamChecklistExerciseGroupTableComponent);
        examChecklistExerciseGroupTableComponent = examChecklistComponentFixture.componentInstance;
        examChecklistComponentFixture.componentRef.setInput('quizExamMaxPoints', 0);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('test onChanges', () => {
        it('should set properties false', () => {
            examChecklistComponentFixture.componentRef.setInput('exerciseGroups', getExerciseGroups(false));
            examChecklistComponentFixture.detectChanges();
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns).toHaveLength(2);
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[0].indexExerciseGroup).toBe(1);
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[0].indexExercise).toBe(1);
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[0].exerciseGroupPointsEqual).toBe(false);
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[0].exerciseTitle).toBe('A');
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[0].exerciseMaxPoints).toBe(50);
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[0].exerciseNumberOfParticipations).toBe(23);

            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[1].indexExerciseGroup).toBeUndefined();
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[1].indexExercise).toBe(2);
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[1].exerciseGroupPointsEqual).toBeUndefined();
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[1].exerciseTitle).toBe('B');
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[1].exerciseMaxPoints).toBe(101);
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[1].exerciseNumberOfParticipations).toBe(22);
        });

        it('should set properties true', () => {
            examChecklistComponentFixture.componentRef.setInput('exerciseGroups', getExerciseGroups(true));
            examChecklistComponentFixture.detectChanges();
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns).not.toHaveLength(0);
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[0].indexExerciseGroup).toBe(1);
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[0].indexExercise).toBe(1);
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[0].exerciseGroupPointsEqual).toBe(true);
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[0].exerciseTitle).toBe('A');
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[0].exerciseMaxPoints).toBe(101);
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[0].exerciseNumberOfParticipations).toBe(23);
        });

        it('should reset group variant columns first', () => {
            examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns = [{} as ExerciseGroupVariantColumn];
            examChecklistComponentFixture.componentRef.setInput('exerciseGroups', []);
            examChecklistComponentFixture.detectChanges();
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns).toEqual([]);
        });
    });
});
