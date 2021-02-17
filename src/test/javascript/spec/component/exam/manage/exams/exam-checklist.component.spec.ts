import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { stub } from 'sinon';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslatePipe } from '@ngx-translate/core';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { ActivatedRoute, Data } from '@angular/router';
import { JhiTranslateDirective } from 'ng-jhipster';
import { RouterTestingModule } from '@angular/router/testing';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';
import { Exam } from 'app/entities/exam.model';
import { AccountService } from 'app/core/auth/account.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { Course } from 'app/entities/course.model';
import { Component } from '@angular/core';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { ExamChecklistCheckComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist-check/exam-checklist-check.component';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { of } from 'rxjs';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { HttpResponse } from '@angular/common/http';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ExamChecklistComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist.component';
import { ExamChecklist } from 'app/entities/exam-checklist.model';
import { ProgressBarComponent } from 'app/shared/dashboards/tutor-participation-graph/progress-bar/progress-bar.component';
import { ExamChecklistExerciseGroupTableComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist-exercisegroup-table/exam-checklist-exercisegroup-table.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';

chai.use(sinonChai);
const expect = chai.expect;

@Component({
    template: '',
})
class DummyComponent {}

describe('ExamChecklistComponent', () => {
    let examChecklistComponentFixture: ComponentFixture<ExamChecklistComponent>;
    let examDetailComponent: ExamChecklistComponent;
    let findAllForExamStub;
    const exam = new Exam();
    const examChecklist = new ExamChecklist();
    const dueDateStatArray = [{ inTime: 0, late: 0, total: 0 }];
    let exerciseGroupService: ExerciseGroupService;

    function getExerciseGroups(equalPoints: boolean) {
        const exerciseGroups = [
            {
                id: 1,
                exercises: [
                    { id: 3, maxPoints: 100, numberOfAssessmentsOfCorrectionRounds: dueDateStatArray, studentAssignedTeamIdComputed: false, secondCorrectionEnabled: false },
                    { id: 2, maxPoints: 100, numberOfAssessmentsOfCorrectionRounds: dueDateStatArray, studentAssignedTeamIdComputed: false, secondCorrectionEnabled: false },
                ],
            },
        ];
        if (!equalPoints) {
            exerciseGroups[0].exercises[0].maxPoints = 50;
        }
        return exerciseGroups;
    }

    const responseExerciseGroup = { body: getExerciseGroups(false) } as HttpResponse<ExerciseGroup[]>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                RouterTestingModule.withRoutes([
                    { path: 'course-management/:courseId/exams/:examId/edit', component: DummyComponent },
                    { path: 'course-management/:courseId/exams/:examId/exercise-groups', component: DummyComponent },
                    { path: 'course-management/:courseId/exams/:examId/tutor-exam-dashboard', component: DummyComponent },
                    { path: 'course-management/:courseId/exams/:examId/scores', component: DummyComponent },
                    { path: 'course-management/:courseId/exams/:examId/student-exams', component: DummyComponent },
                    { path: 'course-management/:courseId/exams/:examId/test-runs', component: DummyComponent },
                    { path: 'course-management/:courseId/exams/:examId/students', component: DummyComponent },
                ]),
                HttpClientTestingModule,
            ],
            declarations: [
                ExamChecklistComponent,
                DummyComponent,
                MockPipe(TranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockComponent(AlertComponent),
                MockComponent(AlertErrorComponent),
                MockDirective(JhiTranslateDirective),
                MockDirective(HasAnyAuthorityDirective),
                ExamChecklistCheckComponent,
                ExamChecklistExerciseGroupTableComponent,
                ProgressBarComponent,
                MockDirective(NgbTooltip),
                MockComponent(FaIconComponent),
            ],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: {
                        data: {
                            subscribe: (fn: (value: Data) => void) =>
                                fn({
                                    exam,
                                }),
                        },
                        snapshot: {},
                    },
                },
                MockProvider(AccountService, {
                    isAtLeastInstructorInCourse: () => true,
                }),
            ],
        })
            .compileComponents()
            .then(() => {
                examChecklistComponentFixture = TestBed.createComponent(ExamChecklistComponent);
                examDetailComponent = examChecklistComponentFixture.componentInstance;
                exerciseGroupService = TestBed.inject(ExerciseGroupService);
                findAllForExamStub = stub(exerciseGroupService, 'findAllForExam');
                findAllForExamStub.returns(of(responseExerciseGroup));
            });
    });

    beforeEach(() => {
        // reset exam
        exam.id = 1;
        exam.title = 'Example Exam';
        exam.numberOfRegisteredUsers = 3;
        exam.maxPoints = 100;
        exam.course = new Course();
        exam.course.id = 1;
        examDetailComponent.exam = exam;

        examChecklist.numberOfGeneratedStudentExams = 1;
    });

    afterEach(() => {
        sinon.restore();
    });

    describe('test checkTotalPointsMandatory', () => {
        beforeEach(() => {
            examDetailComponent.exam.exerciseGroups = getExerciseGroups(true);
        });

        it('should set totalPointsMandatory to false', () => {
            examDetailComponent.checkTotalPointsMandatory();
            expect(examDetailComponent.totalPointsMandatory).to.be.equal(false);
        });

        it('should set checkTotalPointsMandatory to true', () => {
            examDetailComponent.pointsExercisesEqual = true;
            examDetailComponent.checkTotalPointsMandatory();
            expect(examDetailComponent.totalPointsMandatory).to.be.equal(true);
        });

        it('should set totalPointsMandatoryOptional to false', () => {
            examDetailComponent.checkTotalPointsMandatory();
            expect(examDetailComponent.totalPointsMandatoryOptional).to.be.equal(false);
        });

        it('should set checkTotalPointsMandatoryOptional to true', () => {
            examDetailComponent.pointsExercisesEqual = true;
            examDetailComponent.checkTotalPointsMandatory();
            expect(examDetailComponent.totalPointsMandatoryOptional).to.be.equal(true);
        });
    });

    describe('test checkAllExamsGenerated', () => {
        beforeEach(() => {
            examDetailComponent.examChecklist = examChecklist;
            examDetailComponent.exam.exerciseGroups = getExerciseGroups(true);
        });

        it('should set allExamsGenerated to true', () => {
            examChecklist.numberOfGeneratedStudentExams = 3;
            examDetailComponent.checkAllExamsGenerated();
            expect(examDetailComponent.allExamsGenerated).to.be.equal(true);
        });

        it('should set allExamsGenerated to false', () => {
            examDetailComponent.checkAllExamsGenerated();
            expect(examDetailComponent.allExamsGenerated).to.be.equal(false);
        });
    });

    describe('test checkEachGroupContainsExercise', () => {
        it('should set allGroupsContainExercise to true', () => {
            examDetailComponent.exam.exerciseGroups = getExerciseGroups(false);
            examDetailComponent.checkAllGroupContainsExercise();
            expect(examDetailComponent.allGroupsContainExercise).to.be.equal(true);
        });

        it('should set allGroupsContainExercise to false', () => {
            examDetailComponent.exam.exerciseGroups = [{ id: 1, exercises: [] }];
            examDetailComponent.checkAllGroupContainsExercise();
            expect(examDetailComponent.allGroupsContainExercise).to.be.equal(false);
        });
    });

    describe('test function checkPointsExercisesEqual', () => {
        it('should return checkPointsExercisesEqual as true ', () => {
            examDetailComponent.exam.exerciseGroups = getExerciseGroups(true);
            examDetailComponent.checkPointsExercisesEqual();
            expect(examDetailComponent.pointsExercisesEqual).to.be.equal(true);
        });
        it('should return checkPointsExercisesEqual as false', () => {
            examDetailComponent.exam.exerciseGroups = getExerciseGroups(false);
            examDetailComponent.checkPointsExercisesEqual();
            expect(examDetailComponent.pointsExercisesEqual).to.be.equal(false);
        });
    });
});
