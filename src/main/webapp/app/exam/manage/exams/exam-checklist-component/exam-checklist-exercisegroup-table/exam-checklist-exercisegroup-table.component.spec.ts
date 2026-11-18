import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { Component } from '@angular/core';
import { HasAnyAuthorityDirective } from 'app/foundation/auth/has-any-authority.directive';
import { ChecklistCheckComponent } from 'app/shared-ui/components/checklist-check/checklist-check.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ProgressBarComponent } from 'app/exercise/dashboards/tutor-participation-graph/progress-bar/progress-bar.component';
import { ExamChecklistExerciseGroupTableComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist-exercisegroup-table/exam-checklist-exercisegroup-table.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ExerciseGroupColumn } from 'app/exam/shared/entities/exercise-group-column.model';

@Component({
    template: '',
})
class DummyComponent {}

describe('ExamChecklistExerciseGroupTableComponent', () => {
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
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupColumns()).toHaveLength(1);
            const group = examChecklistExerciseGroupTableComponent.exerciseGroupColumns()[0];
            expect(group.indexExerciseGroup).toBe(1);
            expect(group.exerciseGroupPointsEqual).toBe(false);
            expect(group.variants).toHaveLength(2);
            expect(group.variants[0].indexExercise).toBe(1);
            expect(group.variants[0].exerciseTitle).toBe('A');
            expect(group.variants[0].exerciseMaxPoints).toBe(50);
            expect(group.variants[0].exerciseNumberOfParticipations).toBe(23);

            expect(group.variants[1].indexExercise).toBe(2);
            expect(group.variants[1].exerciseTitle).toBe('B');
            expect(group.variants[1].exerciseMaxPoints).toBe(101);
            expect(group.variants[1].exerciseNumberOfParticipations).toBe(22);
        });

        it('should set properties true', () => {
            examChecklistComponentFixture.componentRef.setInput('exerciseGroups', getExerciseGroups(true));
            examChecklistComponentFixture.detectChanges();
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupColumns()).toHaveLength(1);
            const group = examChecklistExerciseGroupTableComponent.exerciseGroupColumns()[0];
            expect(group.indexExerciseGroup).toBe(1);
            expect(group.exerciseGroupPointsEqual).toBe(true);
            expect(group.variants).not.toHaveLength(0);
            expect(group.variants[0].indexExercise).toBe(1);
            expect(group.variants[0].exerciseTitle).toBe('A');
            expect(group.variants[0].exerciseMaxPoints).toBe(101);
            expect(group.variants[0].exerciseNumberOfParticipations).toBe(23);
        });

        it('should reset group variant columns first', () => {
            examChecklistExerciseGroupTableComponent.exerciseGroupColumns.set([{} as ExerciseGroupColumn]);
            examChecklistComponentFixture.componentRef.setInput('exerciseGroups', []);
            examChecklistComponentFixture.detectChanges();
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupColumns()).toEqual([]);
        });
    });
});
