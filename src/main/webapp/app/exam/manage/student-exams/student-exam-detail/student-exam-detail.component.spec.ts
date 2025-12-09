import { StudentExamDetailComponent } from 'app/exam/manage/student-exams/student-exam-detail/student-exam-detail.component';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Course } from 'app/core/course/shared/entities/course.model';
import { User } from 'app/core/user/user.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { MockProvider } from 'ng-mocks';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import dayjs from 'dayjs/esm';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { GradeType } from 'app/assessment/shared/entities/grading-scale.model';
import { StudentExamWithGradeDTO } from 'app/exam/manage/exam-scores/exam-score-dtos.model';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { UMLDiagramType } from '@ls1intum/apollon';
import { AlertService } from 'app/shared/service/alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';

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

    beforeEach(async () => {
        course = { id: 1 };

        student = {
            internal: true,
            name: 'name',
            login: 'login',
            email: 'email',
            visibleRegistrationNumber: 'visibleRegistrationNumber',
        };

        exam = {
            course,
            id: 1,
            examUsers: [Object.assign({ didCheckImage: false, didCheckLogin: false, didCheckName: false, didCheckRegistrationNumber: false }, student, { user: student })],
            visibleDate: dayjs().add(120, 'seconds'),
            startDate: dayjs().add(200, 'seconds'),
            endDate: dayjs().add(7400, 'seconds'),
        };

        result = { score: 40 };
        studentParticipation = new StudentParticipation(ParticipationType.STUDENT);
        const submission = new TextSubmission();
        submission.results = [result];
        submission.participation = studentParticipation;
        studentParticipation.submissions = [submission];

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

        await TestBed.configureTestingModule({
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
                {
                    provide: ActivatedRoute,
                    useValue: {
                        data: of({ studentExam: studentExamWithGrade }),
                        params: of({ courseId: 1 }),
                        url: of([]),
                    },
                },
                { provide: NgbModal, useClass: MockNgbModalService },
                MockProvider(AlertService),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        studentExamDetailComponentFixture = TestBed.createComponent(StudentExamDetailComponent);
        studentExamDetailComponent = studentExamDetailComponentFixture.componentInstance;
        studentExamService = TestBed.inject(StudentExamService);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('initialize', () => {
        studentExamDetailComponentFixture.detectChanges();

        expect(course.id).toBe(1);
        expect(studentExamDetailComponent.achievedTotalPoints).toBe(40);
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
        expect(studentExamDetailComponent.isWorkingTimeFormDisabled).toBeTrue();
    });

    it('should disable the working time form after a test run is submitted', () => {
        studentExamDetailComponent.isTestRun = true;
        studentExamDetailComponent.studentExam = studentExam;

        studentExamDetailComponent.studentExam.submitted = false;
        expect(studentExamDetailComponent.isWorkingTimeFormDisabled).toBeFalse();

        studentExamDetailComponent.studentExam.submitted = true;
        expect(studentExamDetailComponent.isWorkingTimeFormDisabled).toBeTrue();
    });

    it('should disable the working time form if there is no exam', () => {
        studentExamDetailComponent.isTestRun = false;
        studentExamDetailComponent.studentExam = studentExam;

        studentExamDetailComponent.studentExam.exam = undefined;
        expect(studentExamDetailComponent.isWorkingTimeFormDisabled).toBeTrue();
    });

    it('should get isExamOver', () => {
        studentExamDetailComponent.studentExam = studentExam;
        studentExam.exam!.gracePeriod = 100;
        expect(studentExamDetailComponent.isExamOver).toBeFalse();
        studentExam.exam!.startDate = dayjs().add(-20, 'seconds');
        expect(studentExamDetailComponent.isExamOver).toBeFalse();
        studentExam.exam!.startDate = dayjs().add(-7400, 'seconds');
        expect(studentExamDetailComponent.isExamOver).toBeTrue();
        studentExam.exam = undefined;
        expect(studentExamDetailComponent.isExamOver).toBeFalse();
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

    it.each([
        [true, undefined, 'artemisApp.studentExams.bonus'],
        [false, '2.0', 'artemisApp.studentExams.gradeBeforeBonus'],
        [false, undefined, 'artemisApp.studentExams.grade'],
    ])('should get the correct grade explanation label', (isBonus: boolean, gradeAfterBonus: string | undefined, gradeExplanation: string) => {
        studentExamDetailComponent.isBonus = isBonus;
        studentExamDetailComponent.gradeAfterBonus = gradeAfterBonus;
        expect(studentExamDetailComponent.gradeExplanation).toBe(gradeExplanation);
    });

    it('should set exam grade', () => {
        studentExamDetailComponent.gradingScaleExists = false;
        studentExamDetailComponent.passed = false;
        studentExamDetailComponent.isBonus = true;

        const studentExamWithGradeFromServer = Object.assign({}, studentExamWithGrade, {
            gradeType: GradeType.GRADE,
            studentResult: Object.assign({}, studentExamWithGrade.studentResult, {
                overallGrade: '1.3',
                gradeWithBonus: {
                    bonusGrade: 0.3,
                    finalGrade: 1.0,
                },
            }),
        });

        studentExamDetailComponent.setExamGrade(studentExamWithGradeFromServer);

        expect(studentExamDetailComponent.gradingScaleExists).toBeTrue();
        expect(studentExamDetailComponent.passed).toBeTrue();
        expect(studentExamDetailComponent.isBonus).toBeFalse();
        expect(studentExamDetailComponent.grade).toBe(studentExamWithGradeFromServer.studentResult.overallGrade);
        expect(studentExamDetailComponent.gradeAfterBonus).toBe(studentExamWithGradeFromServer.studentResult.gradeWithBonus.finalGrade.toString());
    });

    describe('change student exam to submitted button', () => {
        beforeEach(() => {
            setupComponentToDisplayExamSubmittedButton();
        });

        const ADJUST_SUBMITTED_STATE_BUTTON_ID = '#adjust-submitted-state-button';

        it('should NOT be disabled when individual working time is over', () => {
            const examIsOverSpy = jest.spyOn(studentExamDetailComponent, 'isExamOver', 'get').mockReturnValue(true);

            studentExamDetailComponentFixture.detectChanges();

            const buttonElement = studentExamDetailComponentFixture.nativeElement.querySelector(ADJUST_SUBMITTED_STATE_BUTTON_ID);
            expect(buttonElement).toBeTruthy();
            expect(examIsOverSpy).toHaveBeenCalled();
            expect(buttonElement.disabled).toBeFalse();
        });

        it('should be disabled when individual working time is NOT over', () => {
            const examIsOverSpy = jest.spyOn(studentExamDetailComponent, 'isExamOver', 'get').mockReturnValue(false);

            studentExamDetailComponentFixture.detectChanges();

            const buttonElement = studentExamDetailComponentFixture.nativeElement.querySelector(ADJUST_SUBMITTED_STATE_BUTTON_ID);
            expect(buttonElement).toBeTruthy();
            expect(examIsOverSpy).toHaveBeenCalled();
            expect(buttonElement.disabled).toBeTrue();
        });
    });

    /**
     * Sets up the component to be in a state where the button should be displayed when not considering {@link StudentExamDetailComponent#isExamOver}
     */
    function setupComponentToDisplayExamSubmittedButton() {
        studentExamDetailComponent.student = student;
        studentExamDetailComponent.studentExam = studentExam;
        studentExamDetailComponent.course = course;
        studentExamDetailComponent.course.isAtLeastInstructor = true;
        studentExamDetailComponent.isTestExam = false;
    }
});
