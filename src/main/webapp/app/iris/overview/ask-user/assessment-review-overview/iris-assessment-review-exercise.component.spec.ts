import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Subject, of, throwError } from 'rxjs';
import { MockComponent, MockDirective } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DialogService } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';

import { Course } from 'app/course/shared/entities/course.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { CodeButtonComponent } from 'app/shared-ui/components/buttons/code-button/code-button.component';
import { FeatureToggleLinkDirective } from 'app/foundation/feature-toggle/feature-toggle-link.directive';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { QuizTimerBarComponent } from 'app/iris/overview/ask-user/quiz-timer-bar/quiz-timer-bar.component';
import { IrisReviewAssessmentButtonComponent } from 'app/iris/overview/ask-user/shared/iris-assessment-button/iris-review-assessment-button.component';
import { IrisAssessmentReviewHttpService } from 'app/iris/overview/ask-user/services/iris-assessment-review-http.service';
import { IrisAssessmentReviewExerciseComponent } from 'app/iris/overview/ask-user/assessment-review-overview/iris-assessment-review-exercise.component';
import { IrisInClassQuizStartWarningComponent } from 'app/iris/overview/ask-user/assessment-review-overview/iris-in-class-quiz-start-warning.component';

describe('IrisAssessmentReviewExerciseComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<IrisAssessmentReviewExerciseComponent>;
    let component: IrisAssessmentReviewExerciseComponent;
    let assessmentReviewService: {
        availableInClassQuizForExercise: ReturnType<typeof vi.fn>;
        makeInClassQuizAvailable: ReturnType<typeof vi.fn>;
        clearActiveInClassQuiz: ReturnType<typeof vi.fn>;
    };
    let dialogService: { open: ReturnType<typeof vi.fn> };
    let alertService: AlertService;

    const course = { id: 5 } as Course;
    let exercise: ProgrammingExercise;
    const participation = {
        id: 17,
        student: { name: 'Student One', login: 'student1' },
        submissionCount: 2,
        repositoryUri: 'repo-url',
        buildPlanId: 'build-plan',
    } as ProgrammingExerciseStudentParticipation;

    beforeEach(async () => {
        exercise = new ProgrammingExercise(undefined, undefined);
        exercise.id = 9;
        exercise.type = ExerciseType.PROGRAMMING;
        exercise.title = 'Programming Exercise';
        exercise.isAtLeastInstructor = true;
        exercise.dueDate = dayjs().subtract(1, 'day');

        assessmentReviewService = {
            availableInClassQuizForExercise: vi.fn(() => of(undefined)),
            makeInClassQuizAvailable: vi.fn(() => of(new HttpResponse({ body: { timerExpiresAt: dayjs().add(10, 'minutes'), timeLimit: 600 } }))),
            clearActiveInClassQuiz: vi.fn(),
        };
        dialogService = { open: vi.fn() };

        await TestBed.configureTestingModule({
            imports: [IrisAssessmentReviewExerciseComponent],
            providers: [
                provideRouter([]),
                { provide: ProfileService, useClass: MockProfileService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: IrisAssessmentReviewHttpService, useValue: assessmentReviewService },
                { provide: DialogService, useValue: dialogService },
                { provide: AlertService, useValue: { error: vi.fn(), addAlert: vi.fn() } },
            ],
        })
            .overrideComponent(IrisAssessmentReviewExerciseComponent, {
                remove: {
                    imports: [
                        TranslateDirective,
                        FaIconComponent,
                        CodeButtonComponent,
                        FeatureToggleLinkDirective,
                        IrisReviewAssessmentButtonComponent,
                        QuizTimerBarComponent,
                        HelpIconComponent,
                    ],
                },
                add: {
                    imports: [
                        MockDirective(TranslateDirective),
                        MockComponent(FaIconComponent),
                        MockComponent(CodeButtonComponent),
                        MockDirective(FeatureToggleLinkDirective),
                        MockComponent(IrisReviewAssessmentButtonComponent),
                        MockComponent(QuizTimerBarComponent),
                        MockComponent(HelpIconComponent),
                    ],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(IrisAssessmentReviewExerciseComponent);
        component = fixture.componentInstance;
        alertService = TestBed.inject(AlertService);
        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participations', [participation]);
        fixture.componentRef.setInput('showStartInClassQuizButton', true);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should build table rows with participation and repository links', () => {
        const row = (component as any).participationRows()[0];

        expect(row.participationLink).toEqual(['/course-management', '5', 'programming-exercises', '9', 'participations', '17', 'submissions']);
        expect(row.repositoryUri).toBe('repo-url');
    });

    it('should make the in-class quiz available and emit refresh after the due date', () => {
        const refreshSpy = vi.spyOn(component.refresh, 'emit');

        component.makeInClassQuizAvailable();

        expect(assessmentReviewService.makeInClassQuizAvailable).toHaveBeenCalledExactlyOnceWith(9);
        expect(refreshSpy).toHaveBeenCalledOnce();
    });

    it('should ask for confirmation before starting an in-class quiz before the due date', () => {
        const dialogClose = new Subject<boolean>();
        exercise.dueDate = dayjs().add(1, 'day');
        dialogService.open.mockReturnValue({ onClose: dialogClose.asObservable() });
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();

        component.makeInClassQuizAvailable();

        expect(dialogService.open).toHaveBeenCalledWith(
            IrisInClassQuizStartWarningComponent,
            expect.objectContaining({
                header: 'artemisApp.iris.assessmentInClassQuiz.warning.title',
                modal: true,
                closable: false,
            }),
        );
        expect(assessmentReviewService.makeInClassQuizAvailable).not.toHaveBeenCalled();

        dialogClose.next(true);

        expect(assessmentReviewService.makeInClassQuizAvailable).toHaveBeenCalledExactlyOnceWith(9);
    });

    it('should not start an in-class quiz when the warning dialog is cancelled', () => {
        const dialogClose = new Subject<boolean>();
        exercise.dueDate = dayjs().add(1, 'day');
        dialogService.open.mockReturnValue({ onClose: dialogClose.asObservable() });
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();

        component.makeInClassQuizAvailable();
        dialogClose.next(false);

        expect(assessmentReviewService.makeInClassQuizAvailable).not.toHaveBeenCalled();
    });

    it('should not start an in-class quiz when the warning dialog cannot be opened', () => {
        exercise.dueDate = dayjs().add(1, 'day');
        dialogService.open.mockReturnValue(null);
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();

        component.makeInClassQuizAvailable();

        expect(dialogService.open).toHaveBeenCalledOnce();
        expect(assessmentReviewService.makeInClassQuizAvailable).not.toHaveBeenCalled();
    });

    it('should show an alert when making the in-class quiz available fails', () => {
        assessmentReviewService.makeInClassQuizAvailable.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
        const alertSpy = vi.spyOn(alertService, 'error');

        component.makeInClassQuizAvailable();

        expect(alertSpy).toHaveBeenCalledExactlyOnceWith('error.http.400');
    });

    it('should show an alert and hide the timer when loading the available in-class quiz fails', () => {
        fixture.destroy();
        assessmentReviewService.availableInClassQuizForExercise.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
        const alertSpy = vi.spyOn(alertService, 'error');

        fixture = TestBed.createComponent(IrisAssessmentReviewExerciseComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participations', [participation]);
        fixture.componentRef.setInput('showStartInClassQuizButton', true);
        fixture.detectChanges();

        expect(alertSpy).toHaveBeenCalledExactlyOnceWith('error.http.400');
        expect(fixture.nativeElement.querySelector('jhi-quiz-timer-bar')).toBeNull();
    });

    it('should clear the active in-class quiz when its timer expires', () => {
        component.handleInClassQuizTimerExpired();

        expect(assessmentReviewService.clearActiveInClassQuiz).toHaveBeenCalledExactlyOnceWith(9);
    });
});
