import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { ThemeService } from 'app/core/theme/shared/theme.service';
import { User } from 'app/core/user/user.model';
import { PlagiarismCasesService } from 'app/plagiarism/shared/services/plagiarism-cases.service';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { GradeType } from 'app/assessment/shared/entities/grading-scale.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizSubmission } from 'app/quiz/shared/entities/quiz-submission.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { ExerciseResult, StudentExamWithGradeDTO, StudentResult } from 'app/exam/manage/exam-scores/exam-score-dtos.model';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { ExamResultSummaryComponent, ResultSummaryExerciseInfo } from 'app/exam/overview/summary/exam-result-summary.component';
import { ModelingExamSummaryComponent } from 'app/exam/overview/summary/exercises/modeling-exam-summary/modeling-exam-summary.component';
import { TextExamSummaryComponent } from 'app/exam/overview/summary/exercises/text-exam-summary/text-exam-summary.component';
import { ExamResultOverviewComponent } from 'app/exam/overview/summary/result-overview/exam-result-overview.component';
import { ArtemisServerDateService } from 'app/shared/service/server-date.service';
import dayjs from 'dayjs/esm';
import { MockComponent } from 'ng-mocks';
import { of } from 'rxjs';
import { MockExamParticipationService } from 'test/helpers/mocks/service/mock-exam-participation.service';
import { MockArtemisServerDateService } from 'test/helpers/mocks/service/mock-server-date.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import * as ExamUtils from 'app/exam/overview/exam.utils';
import { ProgrammingExamSummaryComponent } from 'app/exam/overview/summary/exercises/programming-exam-summary/programming-exam-summary.component';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

let fixture: ComponentFixture<ExamResultSummaryComponent>;
let component: ExamResultSummaryComponent;
let artemisServerDateService: ArtemisServerDateService;
let examParticipationService: ExamParticipationService;

const user = { id: 1, name: 'Test User' } as User;

const visibleDate = dayjs().subtract(6, 'hours');
const startDate = dayjs().subtract(5, 'hours');
const endDate = dayjs().subtract(4, 'hours');
const publishResultsDate = dayjs().subtract(3, 'hours');
const examStudentReviewStart = dayjs().subtract(2, 'hours');
const examStudentReviewEnd = dayjs().add(1, 'hours');

const course = { id: 1, accuracyOfScores: 2 } as Course;

const exam = {
    id: 1,
    title: 'ExamForTesting',
    visibleDate,
    startDate,
    endDate,
    publishResultsDate,
    examStudentReviewStart,
    examStudentReviewEnd,
    testExam: false,
    course,
} as Exam;

const testExam = {
    id: 2,
    title: 'TestExam for Testing',
    visibleDate,
    startDate,
    endDate,
    testExam: true,
    course,
} as Exam;

const exerciseGroup = {
    exam,
    title: 'exercise group',
} as ExerciseGroup;

const textSubmission = { id: 1, submitted: true } as TextSubmission;
const quizSubmission = { id: 1 } as QuizSubmission;
const modelingSubmission = { id: 1 } as ModelingSubmission;
const programmingSubmission = { id: 1 } as ProgrammingSubmission;

const textParticipation = { id: 1, student: user, submissions: [textSubmission] } as StudentParticipation;
const quizParticipation = { id: 2, student: user, submissions: [quizSubmission] } as StudentParticipation;
const modelingParticipation = { id: 3, student: user, submissions: [modelingSubmission] } as StudentParticipation;
const programmingParticipation = { id: 4, student: user, submissions: [programmingSubmission] } as StudentParticipation;

const textExercise = {
    id: 1,
    type: ExerciseType.TEXT,
    studentParticipations: [textParticipation],
    exerciseGroup,
} as TextExercise;
const quizExercise = {
    id: 2,
    type: ExerciseType.QUIZ,
    studentParticipations: [quizParticipation],
    exerciseGroup,
} as QuizExercise;
const modelingExercise = {
    id: 3,
    type: ExerciseType.MODELING,
    studentParticipations: [modelingParticipation],
    exerciseGroup,
} as ModelingExercise;
const programmingExercise = {
    id: 4,
    type: ExerciseType.PROGRAMMING,
    studentParticipations: [programmingParticipation],
    exerciseGroup,
} as ProgrammingExercise;
const exercises = [textExercise, quizExercise, modelingExercise, programmingExercise];

const studentExam = {
    id: 1,
    exam,
    user,
    exercises,
} as StudentExam;

const studentExamForTestExam = {
    id: 2,
    exam: testExam,
    user,
    exercises,
} as StudentExam;

const textExerciseResult = {
    exerciseId: textExercise.id,
    achievedScore: 60,
    achievedPoints: 6,
    maxScore: textExercise.maxPoints,
} as ExerciseResult;

const gradeInfo: StudentExamWithGradeDTO = {
    maxPoints: 100,
    maxBonusPoints: 10,
    studentResult: {} as StudentResult,
    gradeType: GradeType.GRADE,
    achievedPointsPerExercise: {
        1: 20,
        2: 10,
    },
};

function sharedSetup(url: string[]) {
    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [FaIconComponent],
            declarations: [
                ExamResultSummaryComponent,
                MockComponent(ExamResultOverviewComponent),
                MockComponent(ProgrammingExamSummaryComponent),
                MockComponent(ModelingExamSummaryComponent),
                MockComponent(TextExamSummaryComponent),
            ],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: {
                            url,
                        },
                        parent: {
                            parent: {
                                snapshot: {
                                    paramMap: convertToParamMap({
                                        courseId: '1',
                                    }),
                                },
                            },
                        },
                    },
                },

                { provide: ArtemisServerDateService, useClass: MockArtemisServerDateService },
                { provide: ExamParticipationService, useClass: MockExamParticipationService },
                provideHttpClient(),
                provideHttpClientTesting(),
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                SessionStorageService,
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamResultSummaryComponent);
                component = fixture.componentInstance;
                component.studentExam = studentExam;
                artemisServerDateService = TestBed.inject(ArtemisServerDateService);
                examParticipationService = TestBed.inject(ExamParticipationService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });
}

describe('ExamResultSummaryComponent', () => {
    sharedSetup(['', '']);

    it('should expand all exercises and call print when Export PDF is clicked', fakeAsync(() => {
        const printStub = jest.spyOn(TestBed.inject(ThemeService), 'print').mockResolvedValue(undefined);
        fixture.detectChanges();
        const exportToPDFButton = fixture.debugElement.query(By.css('#exportToPDFButton'));

        expect(exportToPDFButton).not.toBeNull();

        component.exerciseInfos[1].isCollapsed = true;
        component.exerciseInfos[2].isCollapsed = true;
        component.exerciseInfos[3].isCollapsed = true;
        component.exerciseInfos[4].isCollapsed = true;

        exportToPDFButton.nativeElement.click();

        expect(component.exerciseInfos[1].isCollapsed).toBeFalse();
        expect(component.exerciseInfos[2].isCollapsed).toBeFalse();
        expect(component.exerciseInfos[3].isCollapsed).toBeFalse();
        expect(component.exerciseInfos[4].isCollapsed).toBeFalse();

        tick();
        expect(printStub).toHaveBeenCalledOnce();
        printStub.mockRestore();
    }));

    it('should retrieve grade info correctly', () => {
        const serviceSpy = jest.spyOn(TestBed.inject(ExamParticipationService), 'loadStudentExamGradeInfoForSummary').mockReturnValue(of({ ...gradeInfo }));

        fixture.detectChanges();

        const courseId = 1;
        expect(component.studentExam).toEqual(studentExam);
        expect(serviceSpy).toHaveBeenCalledOnce();
        expect(serviceSpy).toHaveBeenCalledWith(courseId, studentExam.exam!.id, studentExam.id, studentExam.user!.id);
        expect(component.studentExamGradeInfoDTO).toEqual({ ...gradeInfo, studentExam });
    });

    it.each([
        [null, undefined],
        [undefined, undefined],
        [{}, undefined],
        [{ studentParticipations: null }, undefined],
        [{ studentParticipations: undefined }, undefined],
        [{ studentParticipations: [] }, undefined],
        [{ studentParticipations: [{ id: 1 }] }, ['/courses', undefined, 'undefined-exercises', undefined, 'participate', 1]],
    ])('should handle missing/empty fields correctly for %o in generateLink', (exercise, expectedResult) => {
        const link = component.generateLink(exercise as Exercise);
        expect(link).toEqual(expectedResult);
    });

    it.each([
        [{}, undefined],
        [{ studentParticipations: null }, undefined],
        [{ studentParticipations: undefined }, undefined],
        [{ studentParticipations: [] }, undefined],
        [{ studentParticipations: [null] }, undefined],
        [{ studentParticipations: [undefined] }, undefined],
        [{ studentParticipations: [{ id: 2 }] }, { id: 2 }],
    ])('should handle missing/empty fields correctly for %o in getParticipationForExercise', (exercise, expectedResult) => {
        const participation = component.getParticipationForExercise(exercise as Exercise);
        expect(participation).toEqual(expectedResult);
    });

    it.each([
        [null, undefined],
        [undefined, undefined],
        [{}, undefined],
        [{ studentParticipations: null }, undefined],
        [{ studentParticipations: undefined }, undefined],
        [{ studentParticipations: [] }, undefined],
        [{ studentParticipations: [null] }, undefined],
        [{ studentParticipations: [undefined] }, undefined],
        [{ studentParticipations: [{}] }, undefined],
        [{ studentParticipations: [{ submissions: null }] }, undefined],
        [{ studentParticipations: [{ submissions: undefined }] }, undefined],
        [{ studentParticipations: [{ submissions: [{ id: 3 }] }] }, { id: 3 }],
    ])('should handle missing/empty fields correctly for %o in getSubmissionForExercise', (exercise, expectedResult) => {
        const submission = component.getSubmissionForExercise(exercise as Exercise);
        expect(submission).toEqual(expectedResult);
    });

    it('should update student exam correctly', () => {
        const plagiarismService = TestBed.inject(PlagiarismCasesService);
        const plagiarismServiceSpy = jest.spyOn(plagiarismService, 'getPlagiarismCaseInfosForStudent');

        const courseId = 10;
        component.courseId = courseId;

        const studentExam2 = { id: 2 } as StudentExam;
        component.studentExam = studentExam2;
        expect(component.studentExamGradeInfoDTO).toBeUndefined();
        expect(component.studentExam.id).toBe(studentExam2.id);
        expect(plagiarismServiceSpy).not.toHaveBeenCalled();

        component.studentExam = studentExam;
        fixture.changeDetectorRef.detectChanges();

        expect(component.studentExam).toEqual(studentExam);
        expect(component.studentExamGradeInfoDTO.studentExam).toEqual(studentExam);
        expect(component.studentExam.id).toBe(studentExam.id);
        expect(plagiarismServiceSpy).toHaveBeenCalledOnce();
        expect(plagiarismServiceSpy).toHaveBeenCalledWith(courseId, [1, 2, 3, 4]);

        const studentExam3 = { id: 3 } as StudentExam;
        component.studentExam = studentExam3;
        expect(component.studentExamGradeInfoDTO.studentExam).toEqual(studentExam3);
        expect(component.studentExam.id).toBe(studentExam3.id);
        expect(plagiarismServiceSpy).toHaveBeenCalledOnce();
    });

    it('should correctly identify a TestExam', () => {
        component.studentExam = studentExamForTestExam;
        component.ngOnInit();
        expect(component.isTestExam).toBeTrue();
        expect(component.testExamConduction).toBeTrue();

        studentExamForTestExam.submitted = true;
        component.studentExam = studentExamForTestExam;
        component.ngOnInit();
        expect(component.isTestExam).toBeTrue();
        expect(component.testExamConduction).toBeFalse();
    });

    it('should correctly identify a RealExam', () => {
        component.studentExam = studentExam;
        component.ngOnInit();
        expect(component.isTestExam).toBeFalse();
        expect(component.testExamConduction).toBeFalse();
        expect(component.isTestRun).toBeFalse();
        expect(component.testRunConduction).toBeFalse();

        studentExam.submitted = true;
        component.studentExam = studentExam;
        component.ngOnInit();
        expect(component.isTestExam).toBeFalse();
        expect(component.testExamConduction).toBeFalse();
        expect(component.isTestRun).toBeFalse();
        expect(component.testRunConduction).toBeFalse();
    });

    it('should correctly determine if the results are published', () => {
        component.studentExam = studentExam;
        component.testRunConduction = true;
        expect(component.resultsArePublished).toBeFalse();

        component.testExamConduction = true;
        component.testRunConduction = false;
        expect(component.resultsArePublished).toBeFalse();

        component.isTestRun = true;
        component.testExamConduction = false;
        expect(component.resultsArePublished).toBeTrue();

        component.isTestExam = true;
        component.isTestRun = false;
        expect(component.resultsArePublished).toBeTrue();

        component.isTestExam = false;
        // const publishResultsDate is in the past
        expect(component.resultsArePublished).toBeTrue();

        component.studentExam.exam!.publishResultsDate = dayjs().add(2, 'hours');
        expect(component.resultsArePublished).toBeFalse();
    });

    it('should load exam summary when results are published', () => {
        component.studentExam = studentExam;
        const loadStudentExamGradeInfoForSummarySpy = jest.spyOn(examParticipationService, 'loadStudentExamGradeInfoForSummary');
        const isExamResultPublishedSpy = jest.spyOn(ExamUtils, 'isExamResultPublished').mockReturnValue(true);

        component.ngOnInit();

        expect(isExamResultPublishedSpy).toHaveBeenCalledOnce();
        expect(loadStudentExamGradeInfoForSummarySpy).toHaveBeenCalledOnce();
    });

    it('should correctly determine if it is after student review start', () => {
        const now = dayjs();
        const dateSpy = jest.spyOn(artemisServerDateService, 'now').mockReturnValue(now);

        component.isTestExam = true;
        component.ngOnInit();
        expect(component.isAfterStudentReviewStart).toBeTrue();

        component.isTestExam = false;
        component.isTestRun = true;
        component.ngOnInit();
        expect(component.isAfterStudentReviewStart).toBeTrue();

        component.isTestRun = false;
        component.studentExam.exam!.examStudentReviewStart = examStudentReviewStart;
        component.studentExam.exam!.examStudentReviewEnd = examStudentReviewEnd;
        component.ngOnInit();
        expect(component.isAfterStudentReviewStart).toBeTrue();

        component.studentExam.exam!.examStudentReviewStart = dayjs().add(30, 'minutes');
        component.ngOnInit();
        expect(component.isAfterStudentReviewStart).toBeFalse();

        expect(dateSpy).toHaveBeenCalled();
    });

    it('should correctly determine if it is before student review end', () => {
        const now = dayjs();
        const dateSpy = jest.spyOn(artemisServerDateService, 'now').mockReturnValue(now);

        component.isTestExam = true;
        component.ngOnInit();
        expect(component.isBeforeStudentReviewEnd).toBeTrue();

        component.isTestExam = false;
        component.isTestRun = true;
        component.ngOnInit();
        expect(component.isBeforeStudentReviewEnd).toBeTrue();

        component.isTestRun = false;
        component.studentExam.exam!.examStudentReviewEnd = examStudentReviewEnd;
        component.ngOnInit();
        expect(component.isBeforeStudentReviewEnd).toBeTrue();

        component.studentExam.exam!.examStudentReviewEnd = dayjs().subtract(30, 'minutes');
        component.ngOnInit();
        expect(component.isBeforeStudentReviewEnd).toBeFalse();

        expect(dateSpy).toHaveBeenCalled();
    });

    describe('getAchievedPercentageByExerciseId', () => {
        beforeEach(() => {
            const studentExam = {
                exam: {
                    course,
                },
            } as StudentExam;

            const studentResult = {
                exerciseGroupIdToExerciseResult: {
                    [textExercise.id!]: textExerciseResult,
                },
            } as StudentResult;

            component.studentExamGradeInfoDTO = { ...gradeInfo, studentExam, studentResult };
        });

        it('should return undefined if exercise result is undefined', () => {
            component.studentExamGradeInfoDTO.studentResult.exerciseGroupIdToExerciseResult = {};
            const scoreAsPercentage = component.getAchievedPercentageByExerciseId(textExercise.id);

            expect(scoreAsPercentage).toBeUndefined();
        });

        it('should calculate percentage based on achievedScore considering course settings', () => {
            textExerciseResult.achievedScore = 60.6666;

            const scoreAsPercentage = component.getAchievedPercentageByExerciseId(textExercise.id);

            expect(scoreAsPercentage).toBe(60.67);
        });

        it('should calculate percentage based on maxScore and achievedPoints', () => {
            textExerciseResult.achievedScore = undefined;
            textExerciseResult.maxScore = 10;
            textExerciseResult.achievedPoints = 6.066666;
            component.studentExamGradeInfoDTO.studentExam!.exam!.course!.accuracyOfScores = 3;

            const scoreAsPercentage = component.getAchievedPercentageByExerciseId(textExercise.id);

            expect(scoreAsPercentage).toBe(60.667);
        });

        it('should return undefined if not set and not calculable', () => {
            textExerciseResult.achievedScore = undefined;
            textExerciseResult.achievedPoints = undefined;

            const scoreAsPercentage = component.getAchievedPercentageByExerciseId(textExercise.id);

            expect(scoreAsPercentage).toBeUndefined();
        });
    });

    describe('scrollToOverviewOrTop', () => {
        const EXAM_SUMMARY_RESULT_OVERVIEW_ID = 'exam-summary-result-overview';
        const EXAM_RESULTS_TITLE_ID = 'exam-results-title';

        it('should scroll to exam title when overview is not displayed', () => {
            const scrollIntoViewSpy = jest.fn();

            // Call detectChanges first to render the DOM before mocking getElementById
            fixture.detectChanges();

            // To ensure there is no exam summary overview
            const getElementByIdMock = jest.spyOn(document, 'getElementById').mockImplementation((id) => {
                if (id === EXAM_SUMMARY_RESULT_OVERVIEW_ID) {
                    return null;
                }
                if (id === EXAM_RESULTS_TITLE_ID) {
                    return {
                        scrollIntoView: scrollIntoViewSpy,
                    } as unknown as HTMLElement;
                }
                return null;
            });

            // Call the component method directly to avoid querySelector issues with jsdom
            component.scrollToOverviewOrTop();

            expect(getElementByIdMock).toHaveBeenCalledWith(EXAM_SUMMARY_RESULT_OVERVIEW_ID);
            expect(getElementByIdMock).toHaveBeenCalledWith(EXAM_RESULTS_TITLE_ID);
            expect(scrollIntoViewSpy).toHaveBeenCalled();
        });

        it('should scroll to overview when it is displayed', () => {
            const scrollIntoViewSpy = jest.fn();

            component.studentExam = studentExam;
            component.studentExamGradeInfoDTO = { ...gradeInfo, studentExam };

            // Call detectChanges first to render the DOM before mocking getElementById
            fixture.detectChanges();

            const getElementByIdMock = jest.spyOn(document, 'getElementById').mockReturnValue({
                scrollIntoView: scrollIntoViewSpy,
            } as unknown as HTMLElement);

            // Call the component method directly to avoid querySelector issues with jsdom
            component.scrollToOverviewOrTop();

            expect(getElementByIdMock).toHaveBeenCalledWith(EXAM_SUMMARY_RESULT_OVERVIEW_ID);
            expect(scrollIntoViewSpy).toHaveBeenCalled();
        });
    });

    describe('toggleShowSampleSolution', () => {
        it('should be called on button click', () => {
            component.exerciseInfos = {
                1: { isCollapsed: false, displayExampleSolution: true } as ResultSummaryExerciseInfo,
            };
            exam.exampleSolutionPublicationDate = dayjs().subtract(1, 'hour');
            const toggleShowSampleSolutionSpy = jest.spyOn(component, 'toggleShowSampleSolution');

            fixture.changeDetectorRef.detectChanges();

            const button = fixture.debugElement.nativeElement.querySelector(`#show-sample-solution-button-${textExercise.id}`);
            expect(button).toBeTruthy();

            button.click();
            expect(toggleShowSampleSolutionSpy).toHaveBeenCalled();
        });
    });
});
