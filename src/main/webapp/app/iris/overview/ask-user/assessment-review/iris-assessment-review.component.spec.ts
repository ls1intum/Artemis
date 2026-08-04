import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { By } from '@angular/platform-browser';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';

import { Course } from 'app/course/shared/entities/course.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { IrisAssessmentReviewComponent } from 'app/iris/overview/ask-user/assessment-review/iris-assessment-review.component';
import { IrisAssessmentReviewHttpService } from 'app/iris/overview/ask-user/services/iris-assessment-review-http.service';
import { IrisAssessmentReviewResolvedData } from 'app/iris/overview/ask-user/services/iris-assessment-review-resolver.service';
import { IrisAssessment } from 'app/iris/shared/entities/iris-assessment.model';
import { IrisVerdict, IrisVerdictReview } from 'app/iris/shared/entities/iris-verdict.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { User } from 'app/account/user/user.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AlertService } from 'app/foundation/service/alert.service';

describe('IrisAssessmentReviewComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<IrisAssessmentReviewComponent>;
    let component: IrisAssessmentReviewComponent;
    let routeData: BehaviorSubject<Record<string, IrisAssessmentReviewResolvedData>>;
    let assessmentReviewService: {
        acceptAnswers: ReturnType<typeof vi.fn>;
        rejectAnswers: ReturnType<typeof vi.fn>;
    };
    let alertService: AlertService;

    const course = { id: 1, title: 'Course' } as Course;
    const exercise = { id: 2, title: 'Exercise', type: ExerciseType.PROGRAMMING } as ProgrammingExercise;
    const student = { login: 'student1', name: 'Student One' } as User;
    const rows = [{ id: 1, question: 'Question', answer: 'Answer', reasoning: 'Reasoning' }];

    const assessment = (verdictReview?: IrisVerdictReview): IrisAssessment =>
        ({
            id: 3,
            student,
            exercise,
            verdict: IrisVerdict.SUSPICIOUS,
            verdictReview,
        }) as IrisAssessment;

    beforeEach(async () => {
        routeData = new BehaviorSubject({ reviewData: { course, exercise, assessment: assessment(), rows } });
        assessmentReviewService = {
            acceptAnswers: vi.fn(() => of(new HttpResponse<void>())),
            rejectAnswers: vi.fn(() => of(new HttpResponse<void>())),
        };

        await TestBed.configureTestingModule({
            imports: [IrisAssessmentReviewComponent],
            providers: [
                { provide: ActivatedRoute, useValue: { data: routeData.asObservable() } },
                { provide: IrisAssessmentReviewHttpService, useValue: assessmentReviewService },
                MockProvider(AlertService),
            ],
        })
            .overrideComponent(IrisAssessmentReviewComponent, {
                remove: { imports: [TranslateDirective, ArtemisTranslatePipe] },
                add: { imports: [MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe, (key: string) => key)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(IrisAssessmentReviewComponent);
        component = fixture.componentInstance;
        alertService = TestBed.inject(AlertService);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should render the resolved assessment chat rows', () => {
        expect(fixture.nativeElement.textContent).toContain('Course - Exercise - Student One (student1)');
        expect(fixture.nativeElement.textContent).toContain('Question');
        expect(fixture.nativeElement.textContent).toContain('Answer');
        expect(fixture.nativeElement.textContent).toContain('Reasoning');
        expect((component as any).verdictTranslationSuffix()).toBe('suspicious');
    });

    it('should not render a verdict text when the assessment has no verdict', () => {
        routeData.next({ reviewData: { course, exercise, assessment: { ...assessment(), verdict: undefined }, rows } });
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.css('.iris-verdict'))).toBeNull();
    });

    it('should accept answers and update the local review state', () => {
        const acceptButton = fixture.debugElement.queryAll(By.css('button'))[0].nativeElement as HTMLButtonElement;

        acceptButton.click();
        fixture.detectChanges();

        expect(assessmentReviewService.acceptAnswers).toHaveBeenCalledExactlyOnceWith(3);
        expect((component as any).assessment().verdictReview).toBe(IrisVerdictReview.ACCEPTED);
        expect((component as any).reviewTranslationSuffix()).toBe('accepted');
    });

    it('should not send a request when the selected review state is unchanged', () => {
        routeData.next({ reviewData: { course, exercise, assessment: assessment(IrisVerdictReview.ACCEPTED), rows } });
        fixture.detectChanges();

        const acceptButton = fixture.debugElement.queryAll(By.css('button'))[0].nativeElement as HTMLButtonElement;
        acceptButton.click();

        expect(assessmentReviewService.acceptAnswers).not.toHaveBeenCalled();
    });

    it('should roll back the local review state and show an alert when rejecting fails', () => {
        assessmentReviewService.rejectAnswers.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
        const alertSpy = vi.spyOn(alertService, 'error');
        const rejectButton = fixture.debugElement.queryAll(By.css('button'))[1].nativeElement as HTMLButtonElement;

        rejectButton.click();
        fixture.detectChanges();

        expect(assessmentReviewService.rejectAnswers).toHaveBeenCalledExactlyOnceWith(3);
        expect((component as any).assessment().verdictReview).toBeUndefined();
        expect(alertSpy).toHaveBeenCalledExactlyOnceWith('error.http.400');
    });
});
