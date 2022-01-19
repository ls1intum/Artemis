import { StudentExamDetailComponent } from 'app/exam/manage/student-exams/student-exam-detail.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Course } from 'app/entities/course.model';
import { User } from 'app/core/user/user.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { ActivatedRoute, convertToParamMap, Params } from '@angular/router';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { TranslateModule } from '@ngx-translate/core';
import { RouterTestingModule } from '@angular/router/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { Exercise } from 'app/entities/exercise.model';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Exam } from 'app/entities/exam.model';
import dayjs from 'dayjs/esm';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { Result } from 'app/entities/result.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { StudentExamDetailTableRowComponent } from 'app/exam/manage/student-exams/student-exam-detail-table-row/student-exam-detail-table-row.component';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { AlertService } from 'app/core/util/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockTranslateValuesDirective } from '../../../../helpers/mocks/directive/mock-translate-values.directive';

describe('StudentExamDetailComponent', () => {
    let studentExamDetailComponentFixture: ComponentFixture<StudentExamDetailComponent>;
    let studentExamDetailComponent: StudentExamDetailComponent;
    let course: Course;
    let student: User;
    let studentExam: StudentExam;
    let studentExam2: StudentExam;
    let exercise: Exercise;
    let exam: Exam;
    let studentParticipation: StudentParticipation;
    let result: Result;

    let courseManagementService: any;
    let studentExamService: any;
    let gradingSystemService: GradingSystemService;

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
            visibleDate: dayjs().add(120, 'seconds'),
        };

        result = { score: 40 };
        studentParticipation = new StudentParticipation(ParticipationType.STUDENT);
        studentParticipation.results = [result];

        exercise = new ModelingExercise(UMLDiagramType.ActivityDiagram, course, new ExerciseGroup());
        exercise.maxPoints = 100;
        exercise.studentParticipations = [studentParticipation];

        studentExam = {
            id: 1,
            workingTime: 12002,
            exam,
            user: student,
            exercises: [exercise],
        };
        studentExam2 = {
            id: 1,
            workingTime: 3600,
            exam,
            user: student,
            submitted: true,
            submissionDate: dayjs(),
            exercises: [exercise],
        };

        return TestBed.configureTestingModule({
            imports: [
                RouterTestingModule.withRoutes([]),
                NgbModule,
                NgxDatatableModule,
                FontAwesomeTestingModule,
                ReactiveFormsModule,
                TranslateModule.forRoot(),
                HttpClientTestingModule,
            ],
            declarations: [
                StudentExamDetailComponent,
                MockComponent(AlertComponent),
                MockComponent(DataTableComponent),
                MockPipe(ArtemisDurationFromSecondsPipe),
                MockPipe(ArtemisDatePipe),
                MockTranslateValuesDirective,
                MockPipe(ArtemisTranslatePipe),
                StudentExamDetailTableRowComponent,
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
                    toggleSubmittedState: () => {
                        return of(
                            new HttpResponse({
                                body: studentExam2,
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
                MockProvider(AlertService),
                MockDirective(TranslateDirective),
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
                gradingSystemService = TestBed.inject(GradingSystemService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('initialize', () => {
        const findCourseSpy = jest.spyOn(courseManagementService, 'find');
        const gradeSpy = jest.spyOn(gradingSystemService, 'matchPercentageToGradeStepForExam');
        studentExamDetailComponentFixture.detectChanges();

        expect(findCourseSpy).toHaveBeenCalledTimes(1);
        expect(gradeSpy).toHaveBeenCalledTimes(1);
        expect(course.id).toBe(1);
        expect(studentExamDetailComponent.workingTimeForm).not.toBe(null);
        expect(studentExamDetailComponent.achievedTotalPoints).toBe(40);

        // 12002 sec working time = 200 minutes 2 seconds = 3h 20 min 2 s
        expect(studentExamDetailComponent.workingTimeForm.controls.hours.value).toBe(3);
        expect(studentExamDetailComponent.workingTimeForm.controls.minutes.value).toBe(20);
        expect(studentExamDetailComponent.workingTimeForm.controls.seconds.value).toBe(2);
    });

    it('should save working time', () => {
        const studentExamSpy = jest.spyOn(studentExamService, 'updateWorkingTime');
        studentExamDetailComponentFixture.detectChanges();

        studentExamDetailComponent.saveWorkingTime();
        expect(studentExamSpy).toHaveBeenCalledTimes(1);
        expect(studentExamDetailComponent.isSavingWorkingTime).toBe(false);
        expect(course.id).toBe(1);
        expect(studentExamDetailComponent.workingTimeForm).not.toBe(null);
        expect(studentExamDetailComponent.achievedTotalPoints).toBe(40);
        expect(studentExamDetailComponent.maxTotalPoints).toBe(100);
    });

    it('should not increase points when save working time is called more than once', () => {
        const studentExamSpy = jest.spyOn(studentExamService, 'updateWorkingTime');
        studentExamDetailComponentFixture.detectChanges();
        studentExamDetailComponent.saveWorkingTime();
        studentExamDetailComponent.saveWorkingTime();
        studentExamDetailComponent.saveWorkingTime();
        expect(studentExamSpy).toHaveBeenCalledTimes(3);
        expect(studentExamDetailComponent.isSavingWorkingTime).toBe(false);
        expect(course.id).toBe(1);
        expect(studentExamDetailComponent.workingTimeForm).not.toBe(null);
        expect(studentExamDetailComponent.achievedTotalPoints).toBe(40);
        expect(studentExamDetailComponent.maxTotalPoints).toBe(100);
    });

    it('should get examIsOver', () => {
        studentExamDetailComponent.studentExam = studentExam;
        studentExam.exam!.gracePeriod = 100;
        expect(studentExamDetailComponent.examIsOver()).toBe(false);
        studentExam.exam!.endDate = dayjs().add(-20, 'seconds');
        expect(studentExamDetailComponent.examIsOver()).toBe(false);
        studentExam.exam!.endDate = dayjs().add(-200, 'seconds');
        expect(studentExamDetailComponent.examIsOver()).toBe(true);
        studentExam.exam = undefined;
        expect(studentExamDetailComponent.examIsOver()).toBe(false);
    });

    it('should toggle to unsubmitted', () => {
        const toggleSubmittedStateSpy = jest.spyOn(studentExamService, 'toggleSubmittedState');
        studentExamDetailComponentFixture.detectChanges();
        expect(studentExamDetailComponent.studentExam.submitted).toBe(undefined);
        expect(studentExamDetailComponent.studentExam.submissionDate).toBe(undefined);

        studentExamDetailComponent.toggle();

        expect(toggleSubmittedStateSpy).toHaveBeenCalledTimes(1);
        expect(studentExamDetailComponent.studentExam.submitted).toBe(true);
        // the toggle uses the current time as submission date,
        // therefore no useful assertion about a concrete value is possible here
        expect(studentExamDetailComponent.studentExam.submissionDate).not.toBe(undefined);
    });
});
