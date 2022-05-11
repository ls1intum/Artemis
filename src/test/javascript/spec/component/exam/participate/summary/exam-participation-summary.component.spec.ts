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
import { ExerciseType } from 'app/entities/exercise.model';
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

let fixture: ComponentFixture<ExamParticipationSummaryComponent>;
let component: ExamParticipationSummaryComponent;

const user = { id: 1, name: 'Test User' } as User;

const visibleDate = dayjs().subtract(6, 'hours');
const startDate = dayjs().subtract(5, 'hours');
const endDate = dayjs().subtract(4, 'hours');
const publishResultsDate = dayjs().subtract(3, 'hours');
const reviewStartDate = dayjs().subtract(2, 'hours');
const reviewEndDate = dayjs().add(1, 'hours');

const exam = {
    id: 1,
    title: 'Test Exam',
    visibleDate,
    startDate,
    endDate,
    publishResultsDate,
    reviewStartDate,
    reviewEndDate,
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

const studentExam = { id: 1, exam, user, exercises } as StudentExam;

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
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamParticipationSummaryComponent);
                component = fixture.componentInstance;
                component.studentExam = studentExam;
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
        expect(component.collapsedExerciseIds.length).toEqual(4);

        exportToPDFButton.nativeElement.click();
        expect(component.collapsedExerciseIds).toBeEmpty();
        tick();
        expect(printStub).toHaveBeenCalled();
        printStub.mockRestore();
    }));
});
