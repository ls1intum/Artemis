import { StudentExamDetailComponent } from 'app/exam/manage/student-exams/student-exam-detail.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Course } from 'app/entities/course.model';
import { User } from 'app/core/user/user.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { JhiAlertService, JhiTranslateDirective } from 'ng-jhipster';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { ActivatedRoute, convertToParamMap, Params } from '@angular/router';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { TranslateModule } from '@ngx-translate/core';
import { RouterTestingModule } from '@angular/router/testing';
import * as sinon from 'sinon';
import * as sinonChai from 'sinon-chai';
import * as chai from 'chai';
import { ReactiveFormsModule } from '@angular/forms';
import { MockTranslateValuesDirective } from '../../../course/course-scores/course-scores.component.spec';
import { Exercise } from 'app/entities/exercise.model';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Exam } from 'app/entities/exam.model';
import * as moment from 'moment';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { Result } from 'app/entities/result.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('StudentExamDetailComponent', () => {
    let studentExamDetailComponentFixture: ComponentFixture<StudentExamDetailComponent>;
    let studentExamDetailComponent: StudentExamDetailComponent;
    let course: Course;
    let student: User;
    let studentExam: StudentExam;
    let exercise: Exercise;
    let exam: Exam;
    let studentParticipation: StudentParticipation;
    let result: Result;

    let courseManagementService: any;
    let studentExamService: any;

    beforeEach(() => {
        course = { id: 1 };

        student = {
            guidedTourSettings: [],
            name: 'name',
            login: 'login',
            email: 'email',
            visibleRegistrationNumber: 'visibleRegistrationNumber',
        };

        exam = {
            course,
            id: 1,
            registeredUsers: [student],
            visibleDate: moment().add(120, 'seconds'),
        };

        result = { score: 40 };
        studentParticipation = new StudentParticipation(ParticipationType.STUDENT);
        studentParticipation.results = [result];

        exercise = new ModelingExercise(UMLDiagramType.ActivityDiagram, course, new ExerciseGroup());
        exercise.maxPoints = 100;
        exercise.studentParticipations = [studentParticipation];

        studentExam = {
            id: 1,
            workingTime: 3600,
            exam,
            user: student,
            exercises: [exercise],
        };

        return TestBed.configureTestingModule({
            imports: [
                RouterTestingModule.withRoutes([]),
                ArtemisDataTableModule,
                NgbModule,
                NgxDatatableModule,
                FontAwesomeTestingModule,
                ReactiveFormsModule,
                TranslateModule.forRoot(),
            ],
            declarations: [
                StudentExamDetailComponent,
                MockComponent(AlertComponent),
                MockPipe(ArtemisDurationFromSecondsPipe),
                MockPipe(ArtemisDatePipe),
                MockTranslateValuesDirective,
            ],
            providers: [
                MockProvider(StudentExamService, {
                    updateWorkingTime: () => {
                        return of(
                            new HttpResponse({
                                body: studentExam,
                                status: 200,
                            }),
                        );
                    },
                }),
                MockProvider(CourseManagementService, {
                    find: () => {
                        return of(
                            new HttpResponse({
                                body: course,
                                status: 200,
                            }),
                        );
                    },
                }),
                MockPipe(ArtemisDurationFromSecondsPipe),
                MockProvider(JhiAlertService),
                MockDirective(JhiTranslateDirective),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        params: {
                            subscribe: (fn: (value: Params) => void) =>
                                fn({
                                    courseId: 1,
                                }),
                        },
                        data: {
                            subscribe: (fn: (value: any) => void) =>
                                fn({
                                    studentExam,
                                }),
                        },
                        snapshot: {
                            paramMap: convertToParamMap({
                                courseId: '1',
                            }),
                            url: [],
                        },
                    },
                },
            ],
        })
            .compileComponents()
            .then(() => {
                studentExamDetailComponentFixture = TestBed.createComponent(StudentExamDetailComponent);
                studentExamDetailComponent = studentExamDetailComponentFixture.componentInstance;
                courseManagementService = TestBed.inject(CourseManagementService);
                studentExamService = TestBed.inject(StudentExamService);
            });
    });
    afterEach(() => {
        sinon.restore();
    });

    it('initialize', () => {
        const findCourseSpy = sinon.spy(courseManagementService, 'find');
        studentExamDetailComponentFixture.detectChanges();

        expect(findCourseSpy).to.have.been.calledOnce;
        expect(course.id).to.equal(1);
        expect(studentExamDetailComponent.workingTimeForm).to.not.be.null;
        expect(studentExamDetailComponent.achievedTotalScore).to.equal(40);
    });

    it('should return the right icon based on exercise type', () => {
        exercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, new ExerciseGroup());
        expect(studentExamDetailComponent.exerciseIcon(exercise)).to.equal('project-diagram');

        exercise = new ProgrammingExercise(course, new ExerciseGroup());
        expect(studentExamDetailComponent.exerciseIcon(exercise)).to.equal('keyboard');

        exercise = new QuizExercise(course, new ExerciseGroup());
        expect(studentExamDetailComponent.exerciseIcon(exercise)).to.equal('check-double');

        exercise = new FileUploadExercise(course, new ExerciseGroup());
        expect(studentExamDetailComponent.exerciseIcon(exercise)).to.equal('file-upload');
    });

    it('should save working time', () => {
        const studentExamSpy = sinon.spy(studentExamService, 'updateWorkingTime');
        studentExamDetailComponentFixture.detectChanges();

        studentExamDetailComponent.saveWorkingTime();
        expect(studentExamSpy).to.have.been.calledOnce;
        expect(studentExamDetailComponent.isSavingWorkingTime).to.equal(false);
        expect(course.id).to.equal(1);
        expect(studentExamDetailComponent.workingTimeForm).to.not.be.null;
        expect(studentExamDetailComponent.achievedTotalScore).to.equal(40);
        expect(studentExamDetailComponent.maxTotalScore).to.equal(100);
    });

    it('should not increase points when save working time is called more than once', () => {
        const studentExamSpy = sinon.spy(studentExamService, 'updateWorkingTime');
        studentExamDetailComponentFixture.detectChanges();
        studentExamDetailComponent.saveWorkingTime();
        studentExamDetailComponent.saveWorkingTime();
        studentExamDetailComponent.saveWorkingTime();
        expect(studentExamSpy).to.have.been.calledThrice;
        expect(studentExamDetailComponent.isSavingWorkingTime).to.equal(false);
        expect(course.id).to.equal(1);
        expect(studentExamDetailComponent.workingTimeForm).to.not.be.null;
        expect(studentExamDetailComponent.achievedTotalScore).to.equal(40);
        expect(studentExamDetailComponent.maxTotalScore).to.equal(100);
    });
});
