import { HttpClientModule, HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ComplaintsStudentViewComponent } from 'app/complaints/complaints-for-students/complaints-student-view.component';
import { User } from 'app/core/user/user.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { SubmissionType } from 'app/entities/submission.model';
import { TestRunRibbonComponent } from 'app/exam/manage/test-runs/test-run-ribbon.component';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { ExamGeneralInformationComponent } from 'app/exam/participate/general-information/exam-general-information.component';
import { ExamResultOverviewComponent } from 'app/exam/participate/summary/result-overview/exam-result-overview.component';
import { IncludedInScoreBadgeComponent } from 'app/exercises/shared/exercise-headers/included-in-score-badge.component';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { LocalStorageService } from 'ngx-webstorage';
import { of } from 'rxjs';
import { ExamResultSummaryExerciseCardHeaderComponent } from 'app/exam/participate/summary/exercises/header/exam-result-summary-exercise-card-header.component';
import { MockLocalStorageService } from '../../../../../../helpers/mocks/service/mock-local-storage.service';
import { MockExamParticipationService } from '../../../../../../helpers/mocks/service/mock-exam-participation.service';

let fixture: ComponentFixture<ExamResultSummaryExerciseCardHeaderComponent>;
let component: ExamResultSummaryExerciseCardHeaderComponent;

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

const exerciseGroup = {
    exam,
    title: 'exercise group',
} as ExerciseGroup;

const programmingSubmission = { id: 1 } as ProgrammingSubmission;

const programmingParticipation = { id: 4, student: user, submissions: [programmingSubmission] } as StudentParticipation;

const programmingExercise = { id: 4, type: ExerciseType.PROGRAMMING, studentParticipations: [programmingParticipation], exerciseGroup } as ProgrammingExercise;
function sharedSetup(url: string[]) {
    beforeEach(() => {
        return TestBed.configureTestingModule({
            declarations: [
                ExamResultSummaryExerciseCardHeaderComponent,
                MockComponent(TestRunRibbonComponent),
                MockComponent(ExamResultOverviewComponent),
                MockComponent(ExamGeneralInformationComponent),
                MockComponent(ResultComponent),
                MockComponent(ComplaintsStudentViewComponent),
                MockComponent(FaIconComponent),
                MockDirective(TranslateDirective),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(HtmlForMarkdownPipe),
                MockComponent(IncludedInScoreBadgeComponent),
            ],
            imports: [RouterTestingModule.withRoutes([]), HttpClientModule],
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
                { provide: ExamParticipationService, useClass: MockExamParticipationService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamResultSummaryExerciseCardHeaderComponent);
                component = fixture.componentInstance;
                component.index = 3;
                component.exercise = programmingExercise;
                component.exerciseInfo = { isCollapsed: false };
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });
}

describe('ExamResultSummaryExerciseCardHeaderComponent', () => {
    sharedSetup(['', '']);

    it('should collapse and expand exercise when collapse button is clicked', fakeAsync(() => {
        fixture.detectChanges();
        const toggleCollapseExerciseButtonFour = fixture.debugElement.query(By.css('#toggleCollapseExerciseButton-4'));

        expect(toggleCollapseExerciseButtonFour).not.toBeNull();

        toggleCollapseExerciseButtonFour.nativeElement.click();

        expect(component.exerciseInfo.isCollapsed).toBeTrue();

        toggleCollapseExerciseButtonFour.nativeElement.click();

        expect(component.exerciseInfo.isCollapsed).toBeFalse();
    }));

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
        component.exercise = exercise as Exercise;

        fixture.detectChanges();
        const span = fixture.debugElement.query(By.css('.badge.bg-danger'));
        if (shouldBeNonNull) {
            expect(span).not.toBeNull();
        } else {
            expect(span).toBeNull();
        }
    });

    it('should show exercise group title', () => {
        fixture.detectChanges();

        const exerciseTitleElement: HTMLElement = fixture.nativeElement.querySelector('#exercise-group-title-' + programmingExercise.id);
        expect(exerciseTitleElement.textContent).toContain('#' + (component.index + 1));
        expect(exerciseTitleElement.textContent).toContain(programmingExercise.exerciseGroup?.title);
    });
});
