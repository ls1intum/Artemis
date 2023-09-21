import { StudentExamDetailComponent } from 'app/exam/manage/student-exams/student-exam-detail.component';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Course } from 'app/entities/course.model';
import { User } from 'app/core/user/user.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { ActivatedRoute, Params, convertToParamMap } from '@angular/router';
import { NgbModal, NgbModalRef, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { TranslateModule } from '@ngx-translate/core';
import { RouterTestingModule } from '@angular/router/testing';
import { NgForm, NgModel, ReactiveFormsModule } from '@angular/forms';
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
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { AlertService } from 'app/core/util/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockTranslateValuesDirective } from '../../../../helpers/mocks/directive/mock-translate-values.directive';
import { StudentExamWorkingTimeComponent } from 'app/exam/shared/student-exam-working-time/student-exam-working-time.component';
import { GradeType } from 'app/entities/grading-scale.model';
import { StudentExamWithGradeDTO } from 'app/exam/exam-scores/exam-score-dtos.model';
import { MockNgbModalService } from '../../../../helpers/mocks/service/mock-ngb-modal.service';

describe('StudentExamDetailComponent', () => {
    let studentExamDetailComponentFixture: ComponentFixture<StudentExamDetailComponent>;
    let studentExamDetailComponent: StudentExamDetailComponent;
    let course: Course;
    let student: User;
    let studentExam: StudentExam;
    let studentExam2: StudentExam;
    let studentExamWithGrade: StudentExamWithGradeDTO;
    let exercise: Exercise;
    let exam: Exam;
    let studentParticipation: StudentParticipation;
    let result: Result;

    let studentExamService: any;

    beforeEach(() => {
        course = { id: 1 };

        student = {
            internal: true,
            guidedTourSettings: [],
            name: 'name',
            login: 'login',
            email: 'email',
            visibleRegistrationNumber: 'visibleRegistrationNumber',
        };

        exam = {
            course,
            id: 1,
            examUsers: [{ didCheckImage: false, didCheckLogin: false, didCheckName: false, didCheckRegistrationNumber: false, ...student, user: student }],
            visibleDate: dayjs().add(120, 'seconds'),
            startDate: dayjs().add(200, 'seconds'),
            endDate: dayjs().add(7400, 'seconds'),
        };

        result = { score: 40 };
        studentParticipation = new StudentParticipation(ParticipationType.STUDENT);
        studentParticipation.results = [result];

        exercise = new ModelingExercise(UMLDiagramType.ActivityDiagram, course, new ExerciseGroup());
        exercise.maxPoints = 100;
        exercise.studentParticipations = [studentParticipation];

        studentExam = {
            id: 1,
            workingTime: 7200,
            exam,
            user: student,
            exercises: [exercise],
            numberOfExamSessions: 0,
        };
        studentExam2 = {
            id: 2,
            workingTime: 3600,
            exam,
            user: student,
            submitted: true,
            submissionDate: dayjs(),
            exercises: [exercise],
            numberOfExamSessions: 0,
        };

        studentExamWithGrade = {
            studentExam,
            maxPoints: 100,
            gradeType: GradeType.NONE,
            studentResult: {
                userId: 1,
                name: 'user1',
                login: 'user1',
                email: 'user1@tum.de',
                registrationNumber: '111',
                overallPointsAchieved: 40,
                overallScoreAchieved: 40,
                overallPointsAchievedInFirstCorrection: 90,
                submitted: true,
                hasPassed: true,
            },
        } as StudentExamWithGradeDTO;

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
                MockComponent(DataTableComponent),
                MockComponent(StudentExamWorkingTimeComponent),
                MockDirective(NgForm),
                MockDirective(NgModel),
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
                            subscribe: (fn: (value: any) => void) => fn({ studentExam: studentExamWithGrade }),
                        },
                        snapshot: {
                            paramMap: convertToParamMap({
                                courseId: '1',
                            }),
                            url: [],
                        },
                    },
                },
                { provide: NgbModal, useClass: MockNgbModalService },
            ],
        })
            .compileComponents()
            .then(() => {
                studentExamDetailComponentFixture = TestBed.createComponent(StudentExamDetailComponent);
                studentExamDetailComponent = studentExamDetailComponentFixture.componentInstance;
                studentExamService = TestBed.inject(StudentExamService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    const expectDuration = (hours: number, minutes: number, seconds: number) => {
        expect(studentExamDetailComponent.workingTimeFormValues.hours).toBe(hours);
        expect(studentExamDetailComponent.workingTimeFormValues.minutes).toBe(minutes);
        expect(studentExamDetailComponent.workingTimeFormValues.seconds).toBe(seconds);
    };

    it('initialize', () => {
        studentExamDetailComponentFixture.detectChanges();

        expect(course.id).toBe(1);
        expect(studentExamDetailComponent.achievedTotalPoints).toBe(40);

        expectDuration(2, 0, 0);
        expect(studentExamDetailComponent.workingTimeFormValues.percent).toBe(0);
    });

    it('should save working time', () => {
        const studentExamSpy = jest.spyOn(studentExamService, 'updateWorkingTime');
        studentExamDetailComponentFixture.detectChanges();

        studentExamDetailComponent.saveWorkingTime();
        expect(studentExamSpy).toHaveBeenCalledOnce();
        expect(studentExamDetailComponent.isSavingWorkingTime).toBeFalse();
        expect(course.id).toBe(1);
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
        expect(studentExamDetailComponent.isSavingWorkingTime).toBeFalse();
        expect(course.id).toBe(1);
        expect(studentExamDetailComponent.achievedTotalPoints).toBe(40);
        expect(studentExamDetailComponent.maxTotalPoints).toBe(100);
    });

    it('should disable the working time form while saving', () => {
        studentExamDetailComponent.isSavingWorkingTime = true;
        expect(studentExamDetailComponent.isFormDisabled()).toBeTrue();
    });

    it('should disable the working time form after a test run is submitted', () => {
        studentExamDetailComponent.isTestRun = true;
        studentExamDetailComponent.studentExam = studentExam;

        studentExamDetailComponent.studentExam.submitted = false;
        expect(studentExamDetailComponent.isFormDisabled()).toBeFalse();

        studentExamDetailComponent.studentExam.submitted = true;
        expect(studentExamDetailComponent.isFormDisabled()).toBeTrue();
    });

    it('should disable the working time form if there is no exam', () => {
        studentExamDetailComponent.isTestRun = false;
        studentExamDetailComponent.studentExam = studentExam;

        studentExamDetailComponent.studentExam.exam = undefined;
        expect(studentExamDetailComponent.isFormDisabled()).toBeTrue();
    });

    it('should get examIsOver', () => {
        studentExamDetailComponent.studentExam = studentExam;
        studentExam.exam!.gracePeriod = 100;
        expect(studentExamDetailComponent.examIsOver()).toBeFalse();
        studentExam.exam!.endDate = dayjs().add(-20, 'seconds');
        expect(studentExamDetailComponent.examIsOver()).toBeFalse();
        studentExam.exam!.endDate = dayjs().add(-200, 'seconds');
        expect(studentExamDetailComponent.examIsOver()).toBeTrue();
        studentExam.exam = undefined;
        expect(studentExamDetailComponent.examIsOver()).toBeFalse();
    });

    it('should toggle to unsubmitted', () => {
        const toggleSubmittedStateSpy = jest.spyOn(studentExamService, 'toggleSubmittedState');
        studentExamDetailComponentFixture.detectChanges();
        expect(studentExamDetailComponent.studentExam.submitted).toBeUndefined();
        expect(studentExamDetailComponent.studentExam.submissionDate).toBeUndefined();

        studentExamDetailComponent.toggle();

        expect(toggleSubmittedStateSpy).toHaveBeenCalledOnce();
        expect(studentExamDetailComponent.studentExam.submitted).toBeTrue();
        // the toggle uses the current time as submission date,
        // therefore no useful assertion about a concrete value is possible here
        expect(studentExamDetailComponent.studentExam.submissionDate).toBeDefined();
    });

    it('should open confirmation modal', fakeAsync(() => {
        const modalService = TestBed.inject(NgbModal);

        const mockReturnValue = { result: Promise.resolve('confirm') } as NgbModalRef;
        const modalServiceSpy = jest.spyOn(modalService, 'open').mockReturnValue(mockReturnValue);

        const toggleSpy = jest.spyOn(studentExamDetailComponent, 'toggle').mockImplementation();

        const content = 'Modal content';
        studentExamDetailComponent.openConfirmationModal(content);

        tick();

        expect(modalServiceSpy).toHaveBeenCalledOnce();
        expect(modalServiceSpy).toHaveBeenCalledWith(content);
        expect(toggleSpy).toHaveBeenCalledOnce();
    }));

    it('should update the percent difference when the absolute working time changes', () => {
        studentExamDetailComponent.ngOnInit();

        studentExamDetailComponent.workingTimeFormValues.hours = 4;
        studentExamDetailComponent.updateWorkingTimePercent();
        expect(studentExamDetailComponent.workingTimeFormValues.percent).toBe(100);

        studentExamDetailComponent.workingTimeFormValues.hours = 3;
        studentExamDetailComponent.updateWorkingTimePercent();
        expect(studentExamDetailComponent.workingTimeFormValues.percent).toBe(50);

        studentExamDetailComponent.workingTimeFormValues.hours = 0;
        studentExamDetailComponent.updateWorkingTimePercent();
        expect(studentExamDetailComponent.workingTimeFormValues.percent).toBe(-100);

        // small change, not a full percent
        studentExamDetailComponent.workingTimeFormValues.hours = 2;
        studentExamDetailComponent.workingTimeFormValues.seconds = 12;
        studentExamDetailComponent.updateWorkingTimePercent();
        expect(studentExamDetailComponent.workingTimeFormValues.percent).toBe(0.17);
    });

    it('should update the absolute working time when changing the percent difference', () => {
        studentExamDetailComponent.ngOnInit();

        studentExamDetailComponent.workingTimeFormValues.percent = 26;
        studentExamDetailComponent.updateWorkingTimeDuration();
        expectDuration(2, 31, 12);

        studentExamDetailComponent.workingTimeFormValues.percent = -100;
        studentExamDetailComponent.updateWorkingTimeDuration();
        expectDuration(0, 0, 0);
    });

    it.each([
        [true, undefined, 'artemisApp.studentExams.bonus'],
        [false, '2.0', 'artemisApp.studentExams.gradeBeforeBonus'],
        [false, undefined, 'artemisApp.studentExams.grade'],
    ])('should get the correct grade explanation label', (isBonus: boolean, gradeAfterBonus: string | undefined, gradeExplanation: string) => {
        studentExamDetailComponent.isBonus = isBonus;
        studentExamDetailComponent.gradeAfterBonus = gradeAfterBonus;
        expect(studentExamDetailComponent.getGradeExplanation()).toBe(gradeExplanation);
    });

    it('should set exam grade', () => {
        studentExamDetailComponent.gradingScaleExists = false;
        studentExamDetailComponent.passed = false;
        studentExamDetailComponent.isBonus = true;

        const studentExamWithGradeFromServer = {
            ...studentExamWithGrade,
            gradeType: GradeType.GRADE,
            studentResult: {
                ...studentExamWithGrade.studentResult,
                overallGrade: '1.3',
                gradeWithBonus: {
                    bonusGrade: 0.3,
                    finalGrade: 1.0,
                },
            },
        };

        studentExamDetailComponent.setExamGrade(studentExamWithGradeFromServer);

        expect(studentExamDetailComponent.gradingScaleExists).toBeTrue();
        expect(studentExamDetailComponent.passed).toBeTrue();
        expect(studentExamDetailComponent.isBonus).toBeFalse();
        expect(studentExamDetailComponent.grade).toBe(studentExamWithGradeFromServer.studentResult.overallGrade);
        expect(studentExamDetailComponent.gradeAfterBonus).toBe(studentExamWithGradeFromServer.studentResult.gradeWithBonus.finalGrade.toString());
    });
});
