import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { ThemeService } from 'app/core/theme/shared/theme.service';
import { User } from 'app/account/user/user.model';
import { PlagiarismCasesService } from 'app/plagiarism/shared/services/plagiarism-cases.service';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { GradeType } from 'app/assessment/shared/entities/grading-scale.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizSubmission } from 'app/quiz/shared/entities/quiz-submission.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { ExerciseResult, StudentExamWithGradeDTO, StudentResult } from 'app/exam/manage/exam-scores/exam-score-dtos.model';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { ExamResultSummaryComponent, ResultSummaryExerciseInfo } from 'app/exam/overview/summary/exam-result-summary.component';
import { ModelingExamSummaryComponent } from 'app/exam/overview/summary/exercises/modeling-exam-summary/modeling-exam-summary.component';
import { TextExamSummaryComponent } from 'app/exam/overview/summary/exercises/text-exam-summary/text-exam-summary.component';
import { ExamResultOverviewComponent } from 'app/exam/overview/summary/result-overview/exam-result-overview.component';
import { CollapsibleCardComponent } from 'app/exam/overview/summary/collapsible-card/collapsible-card.component';
import { ExamResultSummaryExerciseCardHeaderComponent } from 'app/exam/overview/summary/exercises/header/exam-result-summary-exercise-card-header.component';
import { ExamGeneralInformationComponent } from 'app/exam/overview/general-information/exam-general-information.component';
import { ProgrammingExerciseExampleSolutionRepoDownloadComponent } from 'app/programming/shared/actions/example-solution-repo-download/programming-exercise-example-solution-repo-download.component';
import { ExampleSolutionComponent } from 'app/exercise/example-solution/example-solution.component';
import { QuizExamSummaryComponent } from 'app/exam/overview/summary/exercises/quiz-exam-summary/quiz-exam-summary.component';
import { FileUploadExamSummaryComponent } from 'app/exam/overview/summary/exercises/file-upload-exam-summary/file-upload-exam-summary.component';
import { ComplaintsStudentViewComponent } from 'app/assessment/overview/complaints-for-students/complaints-student-view.component';
import { ExamExerciseHeaderComponent } from 'app/exam/overview/exercises/exam-exercise-header/exam-exercise-header.component';
import { ExamRequestAiFeedbackButtonComponent } from 'app/exam/overview/summary/exam-request-ai-feedback-button/exam-request-ai-feedback-button.component';
import { CourseSidebarToggleButtonComponent } from 'app/course/shared/course-sidebar-toggle-button/course-sidebar-toggle-button.component';
import { PlagiarismVerdict } from 'app/plagiarism/shared/entities/PlagiarismVerdict';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { TumAetUiButtonDirective, TumAetUiMessageComponent, TumAetUiTagComponent, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { RouterLink } from '@angular/router';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { of } from 'rxjs';
import { MockExamParticipationService } from 'test/helpers/mocks/service/mock-exam-participation.service';
import { MockArtemisServerDateService } from 'test/helpers/mocks/service/mock-server-date.service';
import { Course } from 'app/course/shared/entities/course.model';
import * as ExamUtils from 'app/exam/overview/exam.utils';
import { ProgrammingExamSummaryComponent } from 'app/exam/overview/summary/exercises/programming-exam-summary/programming-exam-summary.component';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

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
    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExamResultSummaryComponent],
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
            .overrideComponent(ExamResultSummaryComponent, {
                set: {
                    imports: [
                        FaIconComponent,
                        MockDirective(TranslateDirective),
                        MockPipe(ArtemisTranslatePipe),
                        RouterLink,
                        TumAetUiButtonDirective,
                        TumAetUiMessageComponent,
                        TumAetUiTagComponent,
                        MockDirective(TumAetUiTooltipDirective),
                        ExamExerciseHeaderComponent,
                        MockComponent(ExamRequestAiFeedbackButtonComponent),
                        MockComponent(CourseSidebarToggleButtonComponent),
                        MockComponent(ExamGeneralInformationComponent),
                        MockComponent(ExamResultOverviewComponent),
                        MockComponent(CollapsibleCardComponent),
                        MockComponent(ExamResultSummaryExerciseCardHeaderComponent),
                        MockComponent(ProgrammingExerciseExampleSolutionRepoDownloadComponent),
                        MockComponent(ExampleSolutionComponent),
                        MockComponent(TextExamSummaryComponent),
                        MockComponent(ModelingExamSummaryComponent),
                        MockComponent(QuizExamSummaryComponent),
                        MockComponent(FileUploadExamSummaryComponent),
                        MockComponent(ComplaintsStudentViewComponent),
                        MockComponent(ProgrammingExamSummaryComponent),
                    ],
                },
            })
            .compileComponents();
        fixture = TestBed.createComponent(ExamResultSummaryComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('studentExam', studentExam);
        artemisServerDateService = TestBed.inject(ArtemisServerDateService);
        examParticipationService = TestBed.inject(ExamParticipationService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });
}

describe('ExamResultSummaryComponent', () => {
    sharedSetup(['', '']);

    it('should expand all exercises and call print when Export PDF is clicked', async () => {
        const printStub = vi.spyOn(TestBed.inject(ThemeService), 'print').mockResolvedValue(undefined);
        fixture.detectChanges();
        const exportToPDFButton = fixture.debugElement.query(By.css('#exportToPDFButton'));

        expect(exportToPDFButton).not.toBeNull();

        component.exerciseInfos()[1].isCollapsed = true;
        component.exerciseInfos()[2].isCollapsed = true;
        component.exerciseInfos()[3].isCollapsed = true;
        component.exerciseInfos()[4].isCollapsed = true;

        exportToPDFButton.nativeElement.click();

        expect(component.exerciseInfos()[1].isCollapsed).toBe(false);
        expect(component.exerciseInfos()[2].isCollapsed).toBe(false);
        expect(component.exerciseInfos()[3].isCollapsed).toBe(false);
        expect(component.exerciseInfos()[4].isCollapsed).toBe(false);

        await fixture.whenStable();
        expect(printStub).toHaveBeenCalledOnce();
        printStub.mockRestore();
    });

    it('should retrieve grade info correctly', () => {
        const serviceSpy = vi.spyOn(TestBed.inject(ExamParticipationService), 'loadStudentExamGradeInfoForSummary').mockReturnValue(of({ ...gradeInfo }));

        fixture.detectChanges();

        const courseId = 1;
        expect(component.studentExam()).toEqual(studentExam);
        expect(serviceSpy).toHaveBeenCalledOnce();
        expect(serviceSpy).toHaveBeenCalledWith(courseId, studentExam.exam!.id, studentExam.id, studentExam.user!.id);
        expect(component.studentExamGradeInfoDTO()).toEqual({ ...gradeInfo, studentExam });
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

    it('should update student exam correctly', async () => {
        const plagiarismService = TestBed.inject(PlagiarismCasesService);
        const plagiarismServiceSpy = vi.spyOn(plagiarismService, 'getPlagiarismCaseInfosForStudent');

        // Run change detection once for the default studentExam (id 1) so the component initialises with valid state
        fixture.detectChanges();
        expect(component.studentExam().id).toBe(studentExam.id);

        const courseId = 10;
        component.courseId.set(courseId);
        plagiarismServiceSpy.mockClear();

        // After init, studentExamGradeInfoDTO should be set with the original studentExam
        component.studentExamGradeInfoDTO.set({} as StudentExamWithGradeDTO);

        // Switch to a different studentExam — the effect propagates the new value to studentExamGradeInfoDTO and reloads plagiarism cases with the new courseId
        const studentExam3 = { id: 3, exam: studentExam.exam, user, exercises } as StudentExam;
        fixture.componentRef.setInput('studentExam', studentExam3);
        fixture.detectChanges();
        await Promise.resolve();
        expect(component.studentExamGradeInfoDTO()!.studentExam).toEqual(studentExam3);
        expect(component.studentExam().id).toBe(studentExam3.id);
        expect(plagiarismServiceSpy).toHaveBeenCalledOnce();
        expect(plagiarismServiceSpy).toHaveBeenCalledWith(courseId, [1, 2, 3, 4]);
    });

    it('should correctly identify a TestExam', () => {
        fixture.componentRef.setInput('studentExam', studentExamForTestExam);
        component.ngOnInit();
        expect(component.isTestExam).toBe(true);
        expect(component.testExamConduction()).toBe(true);

        studentExamForTestExam.submitted = true;
        fixture.componentRef.setInput('studentExam', studentExamForTestExam);
        component.ngOnInit();
        expect(component.isTestExam).toBe(true);
        expect(component.testExamConduction()).toBe(false);
    });

    it('should correctly identify a RealExam', () => {
        fixture.componentRef.setInput('studentExam', studentExam);
        component.ngOnInit();
        expect(component.isTestExam).toBe(false);
        expect(component.testExamConduction()).toBe(false);
        expect(component.isTestRun()).toBe(false);
        expect(component.testRunConduction).toBe(false);

        studentExam.submitted = true;
        fixture.componentRef.setInput('studentExam', studentExam);
        component.ngOnInit();
        expect(component.isTestExam).toBe(false);
        expect(component.testExamConduction()).toBe(false);
        expect(component.isTestRun()).toBe(false);
        expect(component.testRunConduction).toBe(false);
    });

    it('should correctly determine if the results are published', () => {
        fixture.componentRef.setInput('studentExam', studentExam);
        component.testRunConduction = true;
        expect(component.resultsArePublished).toBe(false);

        component.testExamConduction.set(true);
        component.testRunConduction = false;
        expect(component.resultsArePublished).toBe(false);

        component.isTestRun.set(true);
        component.testExamConduction.set(false);
        expect(component.resultsArePublished).toBe(true);

        component.isTestExam = true;
        component.isTestRun.set(false);
        expect(component.resultsArePublished).toBe(true);

        component.isTestExam = false;
        // const publishResultsDate is in the past
        expect(component.resultsArePublished).toBe(true);

        component.studentExam().exam!.publishResultsDate = dayjs().add(2, 'hours');
        expect(component.resultsArePublished).toBe(false);
    });

    it('should load exam summary when results are published', () => {
        fixture.componentRef.setInput('studentExam', studentExam);
        const loadStudentExamGradeInfoForSummarySpy = vi.spyOn(examParticipationService, 'loadStudentExamGradeInfoForSummary');
        const isExamResultPublishedSpy = vi.spyOn(ExamUtils, 'isExamResultPublished').mockReturnValue(true);

        component.ngOnInit();

        expect(isExamResultPublishedSpy).toHaveBeenCalledOnce();
        expect(loadStudentExamGradeInfoForSummarySpy).toHaveBeenCalledOnce();
    });

    it('should correctly determine if it is after student review start', () => {
        const now = dayjs();
        const dateSpy = vi.spyOn(artemisServerDateService, 'now').mockReturnValue(now);

        component.isTestExam = true;
        component.ngOnInit();
        expect(component.isAfterStudentReviewStart()).toBe(true);

        component.isTestExam = false;
        component.isTestRun.set(true);
        component.ngOnInit();
        expect(component.isAfterStudentReviewStart()).toBe(true);

        component.isTestRun.set(false);
        component.studentExam().exam!.examStudentReviewStart = examStudentReviewStart;
        component.studentExam().exam!.examStudentReviewEnd = examStudentReviewEnd;
        component.ngOnInit();
        expect(component.isAfterStudentReviewStart()).toBe(true);

        component.studentExam().exam!.examStudentReviewStart = dayjs().add(30, 'minutes');
        component.ngOnInit();
        expect(component.isAfterStudentReviewStart()).toBe(false);

        expect(dateSpy).toHaveBeenCalled();
    });

    it('should correctly determine if it is before student review end', () => {
        const now = dayjs();
        const dateSpy = vi.spyOn(artemisServerDateService, 'now').mockReturnValue(now);

        component.isTestExam = true;
        component.ngOnInit();
        expect(component.isBeforeStudentReviewEnd()).toBe(true);

        component.isTestExam = false;
        component.isTestRun.set(true);
        component.ngOnInit();
        expect(component.isBeforeStudentReviewEnd()).toBe(true);

        component.isTestRun.set(false);
        component.studentExam().exam!.examStudentReviewEnd = examStudentReviewEnd;
        component.ngOnInit();
        expect(component.isBeforeStudentReviewEnd()).toBe(true);

        component.studentExam().exam!.examStudentReviewEnd = dayjs().subtract(30, 'minutes');
        component.ngOnInit();
        expect(component.isBeforeStudentReviewEnd()).toBe(false);

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

            component.studentExamGradeInfoDTO.set({ ...gradeInfo, studentExam, studentResult });
        });

        it('should return undefined if exercise result is undefined', () => {
            component.studentExamGradeInfoDTO()!.studentResult.exerciseGroupIdToExerciseResult = {};
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
            component.studentExamGradeInfoDTO()!.studentExam!.exam!.course!.accuracyOfScores = 3;

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

    describe('getTextColorAndIconClassByExercise', () => {
        function exerciseWithStaleResult(exerciseId: number, staleScore: number): TextExercise {
            const staleResult = { id: exerciseId, score: staleScore, rated: true, completionDate: dayjs().subtract(1, 'hour') } as Result;
            const submission = { id: exerciseId, results: [staleResult] } as TextSubmission;
            const participation = { id: exerciseId, submissions: [submission] } as StudentParticipation;
            return { id: exerciseId, type: ExerciseType.TEXT, studentParticipations: [participation], exerciseGroup } as TextExercise;
        }

        it('should color the percentage based on the authoritative exam score, not the stale participation result', () => {
            // Participation result still holds the pre-complaint (failing) score, while the exam grade info
            // reflects the accepted complaint with full points.
            const exercise = exerciseWithStaleResult(42, 20);
            const studentResult = {
                exerciseGroupIdToExerciseResult: { [exercise.id!]: { exerciseId: exercise.id, achievedScore: 100 } as ExerciseResult },
            } as StudentResult;
            component.studentExamGradeInfoDTO.set({ ...gradeInfo, studentResult });

            expect(component.getTextColorAndIconClassByExercise(exercise).textColorClass).toBe('text-state-success');
        });

        it('should fall back to the participation result color when there is no authoritative exam score', () => {
            const exercise = exerciseWithStaleResult(43, 20);
            component.studentExamGradeInfoDTO.set({ ...gradeInfo, studentResult: { exerciseGroupIdToExerciseResult: {} } as StudentResult });

            expect(component.getTextColorAndIconClassByExercise(exercise).textColorClass).toBe('text-state-danger');
        });
    });

    describe('scrollToOverviewOrTop', () => {
        const EXAM_SUMMARY_RESULT_OVERVIEW_ID = 'exam-summary-result-overview';
        const EXAM_RESULTS_TITLE_ID = 'exam-results-title';

        it('should scroll to exam title when overview is not displayed', () => {
            const scrollIntoViewSpy = vi.fn();

            // Call detectChanges first to render the DOM before mocking getElementById
            fixture.detectChanges();

            // To ensure there is no exam summary overview
            const getElementByIdMock = vi.spyOn(document, 'getElementById').mockImplementation((id) => {
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
            const scrollIntoViewSpy = vi.fn();

            fixture.componentRef.setInput('studentExam', studentExam);
            component.studentExamGradeInfoDTO.set({ ...gradeInfo, studentExam });

            // Call detectChanges first to render the DOM before mocking getElementById
            fixture.detectChanges();

            const getElementByIdMock = vi.spyOn(document, 'getElementById').mockReturnValue({
                scrollIntoView: scrollIntoViewSpy,
            } as unknown as HTMLElement);

            // Call the component method directly to avoid querySelector issues with jsdom
            component.scrollToOverviewOrTop();

            expect(getElementByIdMock).toHaveBeenCalledWith(EXAM_SUMMARY_RESULT_OVERVIEW_ID);
            expect(scrollIntoViewSpy).toHaveBeenCalled();
        });
    });

    describe('page frame', () => {
        const element = (selector: string): HTMLElement | null => fixture.nativeElement.querySelector(selector);
        const header = (): HTMLElement => element('#exam-results-title [data-testid="exam-exercise-header"]')!;
        const title = (): HTMLElement => element('#exam-results-title [data-testid="exam-exercise-title"]')!;
        const actions = (): HTMLElement => header().lastElementChild as HTMLElement;
        const submittedExam = (overrides: Partial<Exam> = {}): StudentExam => ({ ...studentExam, submitted: true, exam: { ...exam, ...overrides } as Exam }) as StudentExam;

        it('shows the page title in the shared 40px title row that holds the export button', () => {
            fixture.detectChanges();

            expect(header().classList).toContain('h-10');
            expect(title().textContent).toContain('artemisApp.exam.examSummary.examResults');
            // the id the "back to overview" scroll falls back to stays on the title row
            expect(element('#exam-results-title')!.contains(title())).toBe(true);
            expect(actions().querySelector('#exportToPDFButton')).not.toBeNull();
        });

        it('lays out the actions in the title row without floats: the AI feedback button first, the export button last, both small', () => {
            fixture.detectChanges();

            const children = [...actions().children];
            expect(children.map((child) => child.id || child.tagName.toLowerCase())).toEqual(['jhi-exam-request-ai-feedback-button', 'exportToPDFButton']);
            const exportButton = children[1];
            // the small size is 34px, so the button fits the 40px row
            expect(exportButton.classList).toContain('tumaet:text-sm');
            expect(exportButton.classList).not.toContain('tumaet:text-base');
            expect(exportButton.classList).not.toContain('float-right');
            // the host of a button that is not shown must not leave an empty flex item (and an extra gap) behind
            expect(children[0].classList).toContain('contents');
        });

        it('puts the toggle of a collapsed sidebar in front of the title inside the same row, and forwards its click', () => {
            fixture.detectChanges();
            expect(element('jhi-course-sidebar-toggle-button')).toBeNull();

            fixture.componentRef.setInput('isSidebarCollapsed', true);
            fixture.detectChanges();

            expect(title().previousElementSibling?.tagName.toLowerCase()).toBe('jhi-course-sidebar-toggle-button');
            expect(actions().querySelector('jhi-course-sidebar-toggle-button')).toBeNull();

            const toggled = vi.fn();
            component.toggleSidebar.subscribe(toggled);
            fixture.debugElement.query(By.directive(CourseSidebarToggleButtonComponent)).componentInstance.toggleSidebar.emit();
            expect(toggled).toHaveBeenCalledOnce();
        });

        it('marks a test run with a tag next to the title instead of the diagonal ribbon', () => {
            fixture.detectChanges();
            expect(element('#testRunRibbon')).toBeNull();

            component.isTestRun.set(true);
            fixture.changeDetectorRef.detectChanges();

            const tag = element('#testRunRibbon')!;
            expect(tag.tagName.toLowerCase()).toBe('tumaet-ui-tag');
            expect(title().nextElementSibling).toBe(tag);
            expect(element('jhi-test-run-ribbon')).toBeNull();
        });

        it('shows only the AI feedback button, aligned to the right edge, when an instructor looks at a test run', () => {
            fixture.componentRef.setInput('instructorView', true);
            fixture.detectChanges();
            component.isTestRun.set(true);
            fixture.changeDetectorRef.detectChanges();

            expect(element('#exam-results-title')).toBeNull();
            expect(element('#testRunRibbon')).toBeNull();
            const aiButton = element('jhi-exam-request-ai-feedback-button')!;
            expect(aiButton.parentElement!.classList).toContain('justify-end');
            // the old float and the extra right margin left the button 8px short of the edge
            expect(aiButton.classList).not.toContain('float-right');
            expect(aiButton.classList).not.toContain('mr-2!');
        });

        it('makes the back-to-overview button a small one', () => {
            fixture.detectChanges();

            const button = element('#back-to-overview-button')!;
            expect(button.classList).toContain('tumaet:text-sm');
            expect(button.classList).not.toContain('tumaet:text-base');
        });

        it('styles the exercises heading like a section heading and keeps the note about unpublished results apart from it', () => {
            fixture.componentRef.setInput('studentExam', submittedExam({ publishResultsDate: dayjs().add(2, 'hours') }));
            fixture.detectChanges();

            const heading = element('h3[jhiTranslate="artemisApp.exam.exercises"]')!;
            // the important modifiers are needed because the unlayered Bootstrap heading rules would win otherwise
            expect(heading.classList).toContain('text-base!');
            expect(heading.classList).toContain('font-semibold!');
            expect(heading.classList).toContain('mb-3!');
            expect(element('fa-icon.info-icon')!.parentElement!.classList).toContain('mb-4');
        });

        it('adds no margins around the complaint area that would open an empty line above it', () => {
            fixture.componentRef.setInput('studentExam', submittedExam({ examStudentReviewStart: dayjs().subtract(1, 'hour'), examStudentReviewEnd: dayjs().add(1, 'hour') }));
            fixture.detectChanges();

            const complaintView = element('jhi-complaint-student-view')!;
            expect(complaintView).not.toBeNull();
            expect(complaintView.classList.length).toBe(0);
        });

        it('caps the problem statement panel of every exercise card at 45% of the card, but not in the printout', () => {
            fixture.detectChanges();

            const content = element('.collapsible-content')!;
            expect(content.style.getPropertyValue('--resizeable-container-right-max-width')).toBe('45%');

            component.isPrinting.set(true);
            fixture.changeDetectorRef.detectChanges();

            expect(content.style.getPropertyValue('--resizeable-container-right-max-width')).toBe('none');
        });

        it('makes the plagiarism case link and the example solution toggle small buttons', () => {
            fixture.componentRef.setInput('studentExam', submittedExam({ exampleSolutionPublicationDate: dayjs().subtract(1, 'hour') }));
            fixture.detectChanges();
            component.plagiarismCaseInfos.set({ [textExercise.id!]: { id: 5, verdict: PlagiarismVerdict.PLAGIARISM } });
            fixture.changeDetectorRef.detectChanges();

            const link = fixture.debugElement.query(By.css('a[tumAetUiButton]')).nativeElement as HTMLElement;
            const toggle = element(`#show-sample-solution-button-${textExercise.id}`)!;
            for (const button of [link, toggle]) {
                expect(button.classList).toContain('tumaet:text-sm');
                expect(button.classList).not.toContain('tumaet:text-base');
            }
        });
    });

    describe('toggleShowSampleSolution', () => {
        it('should be called on button click', () => {
            component.exerciseInfos.set({
                1: { isCollapsed: false, displayExampleSolution: true } as ResultSummaryExerciseInfo,
            });
            exam.exampleSolutionPublicationDate = dayjs().subtract(1, 'hour');
            const toggleShowSampleSolutionSpy = vi.spyOn(component, 'toggleShowSampleSolution');

            fixture.detectChanges();

            const button = fixture.debugElement.nativeElement.querySelector(`#show-sample-solution-button-${textExercise.id}`);
            expect(button).toBeTruthy();

            button.click();
            expect(toggleShowSampleSolutionSpy).toHaveBeenCalled();
        });
    });
});
