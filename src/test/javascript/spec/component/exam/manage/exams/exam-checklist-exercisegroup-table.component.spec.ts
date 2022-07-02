import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { RouterTestingModule } from '@angular/router/testing';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { Component } from '@angular/core';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { ExamChecklistCheckComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist-check/exam-checklist-check.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ProgressBarComponent } from 'app/shared/dashboards/tutor-participation-graph/progress-bar/progress-bar.component';
import { ExamChecklistExerciseGroupTableComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist-exercisegroup-table/exam-checklist-exercisegroup-table.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';

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

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                RouterTestingModule.withRoutes([
                    { path: 'course-management/:courseId/exams/:examId/edit', component: DummyComponent },
                    { path: 'course-management/:courseId/exams/:examId/exercise-groups', component: DummyComponent },
                    { path: 'course-management/:courseId/exams/:examId/assessment-dashboard', component: DummyComponent },
                    { path: 'course-management/:courseId/exams/:examId/scores', component: DummyComponent },
                    { path: 'course-management/:courseId/exams/:examId/student-exams', component: DummyComponent },
                    { path: 'course-management/:courseId/exams/:examId/test-runs', component: DummyComponent },
                    { path: 'course-management/:courseId/exams/:examId/students', component: DummyComponent },
                ]),
                HttpClientTestingModule,
            ],
            declarations: [
                DummyComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockDirective(TranslateDirective),
                MockDirective(HasAnyAuthorityDirective),
                ExamChecklistCheckComponent,
                ExamChecklistExerciseGroupTableComponent,
                ProgressBarComponent,
                MockDirective(NgbTooltip),
                MockComponent(FaIconComponent),
            ],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                examChecklistComponentFixture = TestBed.createComponent(ExamChecklistExerciseGroupTableComponent);
                examChecklistExerciseGroupTableComponent = examChecklistComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('test onChanges', () => {
        it('should set properties false', () => {
            examChecklistExerciseGroupTableComponent.ngOnChanges();
            examChecklistExerciseGroupTableComponent.exerciseGroups = getExerciseGroups(false);
            examChecklistExerciseGroupTableComponent.ngOnChanges();
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns.length).toEqual(2);
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[0].indexExerciseGroup).toEqual(1);
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[0].indexExercise).toEqual(1);
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[0].exerciseGroupPointsEqual).toBeFalse();
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[0].exerciseTitle).toEqual('A');
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[0].exerciseMaxPoints).toEqual(50);
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[0].exerciseNumberOfParticipations).toEqual(23);

            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[1].indexExerciseGroup).toEqual(undefined);
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[1].indexExercise).toEqual(2);
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[1].exerciseGroupPointsEqual).toEqual(undefined);
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[1].exerciseTitle).toEqual('B');
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[1].exerciseMaxPoints).toEqual(101);
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[1].exerciseNumberOfParticipations).toEqual(22);
        });

        it('should set properties true', () => {
            examChecklistExerciseGroupTableComponent.exerciseGroups = getExerciseGroups(true);
            examChecklistExerciseGroupTableComponent.ngOnChanges();
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns.length).not.toEqual(0);
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[0].indexExerciseGroup).toEqual(1);
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[0].indexExercise).toEqual(1);
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[0].exerciseGroupPointsEqual).toBeTrue();
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[0].exerciseTitle).toEqual('A');
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[0].exerciseMaxPoints).toEqual(101);
            expect(examChecklistExerciseGroupTableComponent.exerciseGroupVariantColumns[0].exerciseNumberOfParticipations).toEqual(23);
        });
    });
});
