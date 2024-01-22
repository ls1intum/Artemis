import { HttpClientModule, HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ComplaintsStudentViewComponent } from 'app/complaints/complaints-for-students/complaints-student-view.component';
import { ThemeService } from 'app/core/theme/theme.service';
import { User } from 'app/core/user/user.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/shared/plagiarism-cases.service';
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { GradeType } from 'app/entities/grading-scale.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { QuizSubmission } from 'app/entities/quiz/quiz-submission.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { ExerciseResult, StudentExamWithGradeDTO, StudentResult } from 'app/exam/exam-scores/exam-score-dtos.model';
import { TestRunRibbonComponent } from 'app/exam/manage/test-runs/test-run-ribbon.component';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { ExamGeneralInformationComponent } from 'app/exam/participate/general-information/exam-general-information.component';
import { ExamResultSummaryComponent, ResultSummaryExerciseInfo } from 'app/exam/participate/summary/exam-result-summary.component';
import { FileUploadExamSummaryComponent } from 'app/exam/participate/summary/exercises/file-upload-exam-summary/file-upload-exam-summary.component';
import { ModelingExamSummaryComponent } from 'app/exam/participate/summary/exercises/modeling-exam-summary/modeling-exam-summary.component';
import { ProgrammingExamSummaryComponent } from 'app/exam/participate/summary/exercises/programming-exam-summary/programming-exam-summary.component';
import { QuizExamSummaryComponent } from 'app/exam/participate/summary/exercises/quiz-exam-summary/quiz-exam-summary.component';
import { TextExamSummaryComponent } from 'app/exam/participate/summary/exercises/text-exam-summary/text-exam-summary.component';
import { ExamResultOverviewComponent } from 'app/exam/participate/summary/result-overview/exam-result-overview.component';
import { ProgrammingExerciseInstructionComponent } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instruction.component';
import { IncludedInScoreBadgeComponent } from 'app/exercises/shared/exercise-headers/included-in-score-badge.component';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { UpdatingResultComponent } from 'app/exercises/shared/result/updating-result.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { LocalStorageService } from 'ngx-webstorage';
import { of } from 'rxjs';
import { NgbCollapseMocksModule } from '../../../../helpers/mocks/directive/ngbCollapseMocks.module';
import { MockExamParticipationService } from '../../../../helpers/mocks/service/mock-exam-participation.service';
import { MockLocalStorageService } from '../../../../helpers/mocks/service/mock-local-storage.service';
import { MockArtemisServerDateService } from '../../../../helpers/mocks/service/mock-server-date.service';
import { ExamResultSummaryExerciseCardHeaderComponent } from 'app/exam/participate/summary/exercises/header/exam-result-summary-exercise-card-header.component';
import { Course } from 'app/entities/course.model';
import { AlertService } from 'app/core/util/alert.service';
import { ProgrammingExerciseExampleSolutionRepoDownloadComponent } from 'app/exercises/programming/shared/actions/programming-exercise-example-solution-repo-download.component';
import * as Utils from 'app/shared/util/utils';
import * as ExamUtils from 'app/exam/participate/exam.utils';
import { CollapsibleCardComponent } from 'app/exam/participate/summary/collapsible-card.component';

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
            imports: [RouterTestingModule.withRoutes([]), HttpClientModule, NgbCollapseMocksModule],
            declarations: [
                ExamResultSummaryComponent,
                MockComponent(TestRunRibbonComponent),
                MockComponent(ExamResultOverviewComponent),
                MockComponent(ExamGeneralInformationComponent),
                MockComponent(ResultComponent),
                MockComponent(UpdatingResultComponent),
                MockComponent(ProgrammingExerciseInstructionComponent),
                MockComponent(ProgrammingExamSummaryComponent),
                MockComponent(QuizExamSummaryComponent),
                MockComponent(ModelingExamSummaryComponent),
                MockComponent(TextExamSummaryComponent),
                MockComponent(FileUploadExamSummaryComponent),
                MockComponent(ComplaintsStudentViewComponent),
                MockComponent(FaIconComponent),
                MockComponent(ExamResultSummaryExerciseCardHeaderComponent),
                MockDirective(TranslateDirective),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(HtmlForMarkdownPipe),
                MockComponent(IncludedInScoreBadgeComponent),
                MockComponent(ProgrammingExerciseExampleSolutionRepoDownloadComponent),
                MockComponent(CollapsibleCardComponent),
            ],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: {
                            url,
                            paramMap: convertToParamMap({
                                courseId: '1',
                            }),
                        },
                    },
                },
                MockProvider(CourseManagementService, {
                    find: () => {
                        return of(new HttpResponse({ body: { accuracyOfScores: 1 } }));
                    },
                }),
                { provide: LocalStorageService, useClass: MockLocalStorageService },
                { provide: ArtemisServerDateService, useClass: MockArtemisServerDateService },
                { provide: ExamParticipationService, useClass: MockExamParticipationService },
                MockProvider(AlertService),
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
        const isTestRun = false;
        expect(component.studentExam).toEqual(studentExam);
        expect(serviceSpy).toHaveBeenCalledOnce();
        expect(serviceSpy).toHaveBeenCalledWith(courseId, studentExam.exam!.id, studentExam.user!.id, isTestRun);
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
        const plagiarismService = fixture.debugElement.injector.get(PlagiarismCasesService);
        const plagiarismServiceSpy = jest.spyOn(plagiarismService, 'getPlagiarismCaseInfosForStudent');

        const courseId = 10;
        component.courseId = courseId;

        const studentExam2 = { id: 2 } as StudentExam;
        component.studentExam = studentExam2;
        expect(component.studentExamGradeInfoDTO).toBeUndefined();
        expect(component.studentExam.id).toBe(studentExam2.id);
        expect(plagiarismServiceSpy).not.toHaveBeenCalled();

        component.studentExam = studentExam;
        fixture.detectChanges();

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
        const BACK_TO_OVERVIEW_BUTTON_ID = 'back-to-overview-button';
        const EXAM_SUMMARY_RESULT_OVERVIEW_ID = 'exam-summary-result-overview';

        it('should scroll to top when overview is not displayed', () => {
            const scrollToSpy = jest.spyOn(Utils, 'scrollToTopOfPage');

            const button = fixture.debugElement.nativeElement.querySelector('#' + BACK_TO_OVERVIEW_BUTTON_ID);
            button.click();

            expect(scrollToSpy).toHaveBeenCalledOnce();
        });

        it('should scroll to overview when it is displayed', () => {
            const scrollToSpy = jest.spyOn(Utils, 'scrollToTopOfPage');
            const scrollIntoViewSpy = jest.fn();

            const getElementByIdMock = jest.spyOn(document, 'getElementById').mockReturnValue({
                scrollIntoView: scrollIntoViewSpy,
            } as unknown as HTMLElement);

            component.studentExam = studentExam;
            component.studentExamGradeInfoDTO = { ...gradeInfo, studentExam };

            fixture.detectChanges();

            const button = fixture.debugElement.nativeElement.querySelector('#' + BACK_TO_OVERVIEW_BUTTON_ID);
            button.click();

            expect(getElementByIdMock).toHaveBeenCalledWith(EXAM_SUMMARY_RESULT_OVERVIEW_ID);
            expect(scrollIntoViewSpy).toHaveBeenCalled();
            expect(scrollToSpy).not.toHaveBeenCalled();
        });
    });

    describe('toggleShowSampleSolution', () => {
        it('should be called on button click', () => {
            component.exerciseInfos = {
                1: { isCollapsed: false, displayExampleSolution: true } as ResultSummaryExerciseInfo,
            };
            exam.exampleSolutionPublicationDate = dayjs().subtract(1, 'hour');
            const toggleShowSampleSolutionSpy = jest.spyOn(component, 'toggleShowSampleSolution');

            fixture.detectChanges();

            const button = fixture.debugElement.nativeElement.querySelector(`#show-sample-solution-button-${textExercise.id}`);
            expect(button).toBeTruthy();

            button.click();
            expect(toggleShowSampleSolutionSpy).toHaveBeenCalled();
        });
    });
});
