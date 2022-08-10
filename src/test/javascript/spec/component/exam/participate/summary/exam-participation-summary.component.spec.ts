import dayjs from 'dayjs/esm';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ExamParticipationSummaryComponent } from 'app/exam/participate/summary/exam-participation-summary.component';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { TestRunRibbonComponent } from 'app/exam/manage/test-runs/test-run-ribbon.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ExamInformationComponent } from 'app/exam/participate/information/exam-information.component';
import { ExamPointsSummaryComponent } from 'app/exam/participate/summary/points-summary/exam-points-summary.component';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { ProgrammingExamSummaryComponent } from 'app/exam/participate/summary/exercises/programming-exam-summary/programming-exam-summary.component';
import { FileUploadExamSummaryComponent } from 'app/exam/participate/summary/exercises/file-upload-exam-summary/file-upload-exam-summary.component';
import { QuizExamSummaryComponent } from 'app/exam/participate/summary/exercises/quiz-exam-summary/quiz-exam-summary.component';
import { ModelingExamSummaryComponent } from 'app/exam/participate/summary/exercises/modeling-exam-summary/modeling-exam-summary.component';
import { TextExamSummaryComponent } from 'app/exam/participate/summary/exercises/text-exam-summary/text-exam-summary.component';
import { ProgrammingExerciseInstructionComponent } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instruction.component';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { RouterTestingModule } from '@angular/router/testing';
import { HttpClientModule } from '@angular/common/http';
import { Exam } from 'app/entities/exam.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { User } from 'app/core/user/user.model';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { By } from '@angular/platform-browser';
import { TextExercise } from 'app/entities/text-exercise.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { QuizSubmission } from 'app/entities/quiz/quiz-submission.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { IncludedInScoreBadgeComponent } from 'app/exercises/shared/exercise-headers/included-in-score-badge.component';
import { ComplaintsStudentViewComponent } from 'app/complaints/complaints-for-students/complaints-student-view.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockLocalStorageService } from '../../../../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService } from 'ngx-webstorage';
import { ThemeService } from 'app/core/theme/theme.service';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { MockArtemisServerDateService } from '../../../../helpers/mocks/service/mock-server-date.service';
import { GradeType } from 'app/entities/grading-scale.model';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { StudentExamWithGradeDTO, StudentResult } from 'app/exam/exam-scores/exam-score-dtos.model';
import { MockExamParticipationService } from '../../../../helpers/mocks/service/mock-exam-participation.service';
import { SubmissionType } from 'app/entities/submission.model';

let fixture: ComponentFixture<ExamParticipationSummaryComponent>;
let component: ExamParticipationSummaryComponent;
let artemisServerDateService: ArtemisServerDateService;

const user = { id: 1, name: 'Test User' } as User;

const visibleDate = dayjs().subtract(6, 'hours');
const startDate = dayjs().subtract(5, 'hours');
const endDate = dayjs().subtract(4, 'hours');
const publishResultsDate = dayjs().subtract(3, 'hours');
const examStudentReviewStart = dayjs().subtract(2, 'hours');
const examStudentReviewEnd = dayjs().add(1, 'hours');

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
} as Exam;

const testExam = {
    id: 2,
    title: 'TestExam for Testing',
    visibleDate,
    startDate,
    endDate,
    testExam: true,
} as Exam;

const exerciseGroup = {
    exam,
} as ExerciseGroup;

const textSubmission = { id: 1, submitted: true } as TextSubmission;
const quizSubmission = { id: 1 } as QuizSubmission;
const modelingSubmission = { id: 1 } as ModelingSubmission;
const programmingSubmission = { id: 1 } as ProgrammingSubmission;

const textParticipation = { id: 1, student: user, submissions: [textSubmission] } as StudentParticipation;
const quizParticipation = { id: 2, student: user, submissions: [quizSubmission] } as StudentParticipation;
const modelingParticipation = { id: 3, student: user, submissions: [modelingSubmission] } as StudentParticipation;
const programmingParticipation = { id: 4, student: user, submissions: [programmingSubmission] } as StudentParticipation;

const textExercise = { id: 1, type: ExerciseType.TEXT, studentParticipations: [textParticipation], exerciseGroup } as TextExercise;
const quizExercise = { id: 2, type: ExerciseType.QUIZ, studentParticipations: [quizParticipation], exerciseGroup } as QuizExercise;
const modelingExercise = { id: 3, type: ExerciseType.MODELING, studentParticipations: [modelingParticipation], exerciseGroup } as ModelingExercise;
const programmingExercise = { id: 4, type: ExerciseType.PROGRAMMING, studentParticipations: [programmingParticipation], exerciseGroup } as ProgrammingExercise;
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
            imports: [RouterTestingModule.withRoutes([]), MockModule(NgbModule), HttpClientModule],
            declarations: [
                ExamParticipationSummaryComponent,
                MockComponent(TestRunRibbonComponent),
                MockComponent(ExamPointsSummaryComponent),
                MockComponent(ExamInformationComponent),
                MockComponent(ResultComponent),
                MockComponent(ProgrammingExerciseInstructionComponent),
                MockComponent(ProgrammingExamSummaryComponent),
                MockComponent(QuizExamSummaryComponent),
                MockComponent(ModelingExamSummaryComponent),
                MockComponent(TextExamSummaryComponent),
                MockComponent(FileUploadExamSummaryComponent),
                MockComponent(ComplaintsStudentViewComponent),
                MockComponent(FaIconComponent),
                MockDirective(TranslateDirective),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(HtmlForMarkdownPipe),
                MockComponent(IncludedInScoreBadgeComponent),
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
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamParticipationSummaryComponent);
                component = fixture.componentInstance;
                component.studentExam = studentExam;
                artemisServerDateService = TestBed.inject(ArtemisServerDateService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });
}

describe('ExamParticipationSummaryComponent', () => {
    sharedSetup(['', '']);

    it('should expand all exercises and call print when Export PDF is clicked', fakeAsync(() => {
        const printStub = jest.spyOn(TestBed.inject(ThemeService), 'print').mockReturnValue();
        fixture.detectChanges();
        const exportToPDFButton = fixture.debugElement.query(By.css('#exportToPDFButton'));
        const toggleCollapseExerciseButtonOne = fixture.debugElement.query(By.css('#toggleCollapseExerciseButton-0'));
        const toggleCollapseExerciseButtonTwo = fixture.debugElement.query(By.css('#toggleCollapseExerciseButton-1'));
        const toggleCollapseExerciseButtonThree = fixture.debugElement.query(By.css('#toggleCollapseExerciseButton-2'));
        const toggleCollapseExerciseButtonFour = fixture.debugElement.query(By.css('#toggleCollapseExerciseButton-3'));
        expect(exportToPDFButton).not.toBeNull();
        expect(toggleCollapseExerciseButtonOne).not.toBeNull();
        expect(toggleCollapseExerciseButtonTwo).not.toBeNull();
        expect(toggleCollapseExerciseButtonThree).not.toBeNull();
        expect(toggleCollapseExerciseButtonFour).not.toBeNull();

        toggleCollapseExerciseButtonOne.nativeElement.click();
        toggleCollapseExerciseButtonTwo.nativeElement.click();
        toggleCollapseExerciseButtonThree.nativeElement.click();
        toggleCollapseExerciseButtonFour.nativeElement.click();
        expect(component.collapsedExerciseIds).toHaveLength(4);

        exportToPDFButton.nativeElement.click();
        expect(component.collapsedExerciseIds).toBeEmpty();
        tick();
        expect(printStub).toHaveBeenCalled();
        printStub.mockRestore();
    }));

    it('should retrieve grade info correctly', () => {
        const serviceSpy = jest.spyOn(TestBed.inject(ExamParticipationService), 'loadStudentExamGradeInfoForSummary').mockReturnValue(of({ ...gradeInfo }));

        fixture.detectChanges();

        const courseId = 1;
        expect(serviceSpy).toHaveBeenCalledOnce();
        expect(serviceSpy).toHaveBeenCalledWith(courseId, studentExam.exam!.id, studentExam.user!.id);
        expect(component.studentExam).toEqual(studentExam);
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

    it.each([
        [{}, false],
        [{ studentParticipations: null }, false],
        [{ studentParticipations: undefined }, false],
        [{ studentParticipations: [] }, false],
        [{ studentParticipations: [{}] }, false],
        [{ studentParticipations: [{ submissions: null }] }, false],
        [{ studentParticipations: [{ submissions: undefined }] }, false],
        [{ studentParticipations: [{ submissions: [{ type: SubmissionType.MANUAL }] }] }, false],
        [{ studentParticipations: [{ submissions: [{ type: SubmissionType.ILLEGAL }] }] }, true],
    ])('should handle missing/empty fields correctly for %o when displaying illegal submission badge', (exercise, shouldBeNonNull) => {
        component.studentExam = { id: 1, exam, user, exercises: [exercise as Exercise] };
        fixture.detectChanges();
        const span = fixture.debugElement.query(By.css('.badge.bg-danger'));
        if (shouldBeNonNull) {
            expect(span).not.toBeNull();
        } else {
            expect(span).toBeNull();
        }
    });

    it('should update student exam correctly', () => {
        const studentExam2 = { id: 2 } as StudentExam;
        component.studentExam = studentExam2;
        expect(component.studentExamGradeInfoDTO).toBeUndefined();

        component.studentExam = studentExam;
        fixture.detectChanges();

        expect(component.studentExam).toEqual(studentExam);
        expect(component.studentExamGradeInfoDTO.studentExam).toEqual(studentExam);

        const studentExam3 = { id: 3 } as StudentExam;
        component.studentExam = studentExam3;
        expect(component.studentExamGradeInfoDTO.studentExam).toEqual(studentExam3);
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
        expect(component.resultsPublished).toBeFalse();

        component.testExamConduction = true;
        component.testRunConduction = false;
        expect(component.resultsPublished).toBeFalse();

        component.isTestRun = true;
        component.testExamConduction = false;
        expect(component.resultsPublished).toBeTrue();

        component.isTestExam = true;
        component.isTestRun = false;
        expect(component.resultsPublished).toBeTrue();

        component.isTestExam = false;
        // const publishResultsDate is in the past
        expect(component.resultsPublished).toBeTrue();

        component.studentExam.exam!.publishResultsDate = dayjs().add(2, 'hours');
        expect(component.resultsPublished).toBeFalse();
    });

    it('should correctly determine if it is after student review start', () => {
        const now = dayjs();
        const dateSpy = jest.spyOn(artemisServerDateService, 'now').mockReturnValue(now);

        component.isTestExam = true;
        expect(component.isAfterStudentReviewStart()).toBeTrue();

        component.isTestExam = false;
        component.isTestRun = true;
        expect(component.isAfterStudentReviewStart()).toBeTrue();

        component.isTestRun = false;
        component.studentExam.exam!.examStudentReviewStart = examStudentReviewStart;
        component.studentExam.exam!.examStudentReviewEnd = examStudentReviewEnd;
        expect(component.isAfterStudentReviewStart()).toBeTrue();

        component.studentExam.exam!.examStudentReviewStart = dayjs().add(30, 'minutes');
        expect(component.isAfterStudentReviewStart()).toBeFalse();

        expect(dateSpy).toHaveBeenCalledTimes(2);
    });

    it('should correctly determine if it is before student review end', () => {
        const now = dayjs();
        const dateSpy = jest.spyOn(artemisServerDateService, 'now').mockReturnValue(now);

        component.isTestExam = true;
        expect(component.isBeforeStudentReviewEnd()).toBeTrue();

        component.isTestExam = false;
        component.isTestRun = true;
        expect(component.isBeforeStudentReviewEnd()).toBeTrue();

        component.isTestRun = false;
        component.studentExam.exam!.examStudentReviewEnd = examStudentReviewEnd;
        expect(component.isBeforeStudentReviewEnd()).toBeTrue();

        component.studentExam.exam!.examStudentReviewEnd = dayjs().subtract(30, 'minutes');
        expect(component.isBeforeStudentReviewEnd()).toBeFalse();

        expect(dateSpy).toHaveBeenCalledTimes(2);
    });
});
