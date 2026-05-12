import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ResultComponent } from 'app/exercise/result/result.component';
import { MissingResultInformation, ResultTemplateStatus } from 'app/exercise/result/result.utils';
import { ResultProgressBarComponent } from 'app/exercise/result/result-progress-bar/result-progress-bar.component';
import { SimpleChange } from '@angular/core';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { Participation, ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import dayjs from 'dayjs/esm';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { DialogService } from 'primeng/dynamicdialog';
import * as utils from 'app/exercise/feedback/feedback.utils';
import { FeedbackComponentPreparedParams } from 'app/exercise/feedback/feedback.utils';
import { FeedbackComponent } from 'app/exercise/feedback/feedback.component';
import { By } from '@angular/platform-browser';
import { faTimesCircle } from '@fortawesome/free-solid-svg-icons';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { Router } from '@angular/router';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';

const mockExercise: Exercise = {
    id: 1,
    title: 'Sample Exercise',
    maxPoints: 100,
    dueDate: dayjs().subtract(3, 'hours'),
    assessmentType: AssessmentType.AUTOMATIC,
    type: ExerciseType.PROGRAMMING,
    numberOfAssessmentsOfCorrectionRounds: [],
    secondCorrectionEnabled: false,
    studentAssignedTeamIdComputed: false,
    course: { id: 42 },
} as Exercise;

const mockParticipation: Participation = {
    id: 1,
    type: ParticipationType.STUDENT,
    exercise: mockExercise,
};

const mockResult: Result = {
    id: 1,
    completionDate: dayjs().subtract(2, 'hours'),
    score: 85,
    rated: true,
    submission: { id: 42 },
    feedbacks: [
        {
            id: 1,
            text: 'Well done!',
        },
    ],
};

const preparedFeedback: FeedbackComponentPreparedParams = {
    exercise: mockExercise,
    participation: mockParticipation,
    result: mockResult,
    exerciseType: ExerciseType.PROGRAMMING,
    showScoreChart: true,
    messageKey: 'artemisApp.result.notLatestSubmission',
    latestDueDate: dayjs().subtract(1, 'hours'),
    showMissingAutomaticFeedbackInformation: true,
};
const participationServiceMock = {
    downloadArtifact: vi.fn(),
};

describe('ResultComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: ResultComponent;
    let fixture: ComponentFixture<ResultComponent>;
    let dialogService: DialogService;
    let router: Router;

    beforeEach(async () => {
        participationServiceMock.downloadArtifact = vi.fn();
        (global as any).URL.createObjectURL = vi.fn(() => 'blob:test-url');
        (global as any).URL.revokeObjectURL = vi.fn();

        mockParticipation.submissions = [
            {
                id: 1,
                participation: mockParticipation,
                results: [mockResult],
            },
        ];

        await TestBed.configureTestingModule({
            imports: [ResultComponent],
            providers: [
                { provide: DialogService, useClass: MockDialogService },
                { provide: ParticipationService, useValue: participationServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideComponent(ResultComponent, {
                remove: { imports: [ResultProgressBarComponent, ArtemisTranslatePipe, ArtemisTimeAgoPipe, ArtemisDatePipe, ArtemisDurationFromSecondsPipe, TranslateDirective] },
                add: {
                    imports: [
                        MockComponent(ResultProgressBarComponent),
                        TranslatePipeMock,
                        MockPipe(ArtemisDatePipe),
                        MockPipe(ArtemisTimeAgoPipe),
                        MockPipe(ArtemisDurationFromSecondsPipe),
                        MockDirective(TranslateDirective),
                    ],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ResultComponent);
        comp = fixture.componentInstance;
        dialogService = TestBed.inject(DialogService);
        router = TestBed.inject(Router);

        participationServiceMock.downloadArtifact = vi.fn();

        comp.badge = {
            tooltip: 'Example Tooltip',
            class: 'Example Class',
            text: 'Example Test',
        };
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
        (global as any).URL.revokeObjectURL = vi.fn();
    });

    it('should set template status to BUILDING if isBuilding changes to true even though participation changes', () => {
        comp.participation.set({
            results: [],
        } as any as StudentParticipation);

        fixture.componentRef.setInput('isBuilding', false);
        comp.ngOnInit();
        expect(comp.templateStatus).toEqual(ResultTemplateStatus.NO_RESULT);

        const newParticipation = {
            results: [],
        } as any as StudentParticipation;

        fixture.componentRef.setInput('isBuilding', true);
        comp.participation.set(newParticipation);
        comp.ngOnChanges({
            isBuilding: { currentValue: true, previousValue: false } as any as SimpleChange,
            participation: { currentValue: newParticipation } as any as SimpleChange,
        });

        expect(comp.templateStatus).toEqual(ResultTemplateStatus.IS_BUILDING);
    });

    describe('should display HAS_RESULT status properly', () => {
        const RESULT_SCORE_SELECTOR = '#result-score';

        it('should not display if result is not present', () => {
            comp.resultIconClass = faTimesCircle;
            const button = fixture.debugElement.nativeElement.querySelector(RESULT_SCORE_SELECTOR);
            expect(button).not.toBeTruthy();
        });

        it('should display result if present', () => {
            comp.resultIconClass = faTimesCircle;
            comp.result.set(mockResult);
            comp.templateStatus = ResultTemplateStatus.HAS_RESULT;

            fixture.changeDetectorRef.detectChanges();

            const button = fixture.debugElement.nativeElement.querySelector(RESULT_SCORE_SELECTOR);
            expect(button).toBeTruthy();
        });

        it('should display modal onClick and initialize results modal', () => {
            const showDetailsSpy = vi.spyOn(comp, 'showDetails');
            const openModalSpy = vi.spyOn(dialogService, 'open');
            const prepareFeedbackSpy = vi.spyOn(utils, 'prepareFeedbackComponentParameters').mockReturnValue(preparedFeedback);

            comp.exercise.set(preparedFeedback.exercise);
            comp.result.set(mockResult);
            comp.resultIconClass = faTimesCircle;
            comp.templateStatus = ResultTemplateStatus.HAS_RESULT;

            fixture.changeDetectorRef.detectChanges();

            const button = fixture.debugElement.nativeElement.querySelector(RESULT_SCORE_SELECTOR);
            expect(button).toBeTruthy();

            button.dispatchEvent(new Event('click'));

            expect(showDetailsSpy).toHaveBeenCalled();
            expect(openModalSpy).toHaveBeenCalledWith(
                FeedbackComponent,
                expect.objectContaining({
                    data: expect.objectContaining({
                        exercise: preparedFeedback.exercise,
                        result: preparedFeedback.result,
                        exerciseType: preparedFeedback.exerciseType,
                        showScoreChart: preparedFeedback.showScoreChart,
                        messageKey: preparedFeedback.messageKey,
                        latestDueDate: preparedFeedback.latestDueDate,
                        showMissingAutomaticFeedbackInformation: preparedFeedback.showMissingAutomaticFeedbackInformation,
                    }),
                }),
            );
            expect(prepareFeedbackSpy).toHaveBeenCalledOnce();
        });
    });

    it('should navigate to text exercise details when exercise type is TEXT', () => {
        comp.exercise.set({ ...mockExercise, type: ExerciseType.TEXT });
        comp.participation.set(mockParticipation);
        const navigateSpy = vi.spyOn(router, 'navigate');
        const courseId = 42;
        comp.showDetails(mockResult);

        expect(navigateSpy).toHaveBeenCalledWith([
            '/courses',
            courseId,
            'exercises',
            'text-exercises',
            comp.exercise()!.id,
            'participate',
            mockParticipation.id,
            'submission',
            mockResult.submission?.id,
            'result',
            mockResult.id,
        ]);
    });

    it('should navigate to modeling exercise details when exercise type is MODELING', () => {
        comp.exercise.set({ ...mockExercise, type: ExerciseType.MODELING });
        comp.participation.set(mockParticipation);
        const navigateSpy = vi.spyOn(router, 'navigate');
        const courseId = 42;
        comp.showDetails(mockResult);

        expect(navigateSpy).toHaveBeenCalledWith([
            '/courses',
            courseId,
            'exercises',
            'modeling-exercises',
            comp.exercise()!.id,
            'participate',
            mockParticipation.id,
            'submission',
            mockResult.submission?.id,
            'result',
            mockResult.id,
        ]);
    });

    it('should call showDetails only when isInSidebarCard is false', () => {
        comp.result.set(mockResult);
        const detailsSpy = vi.spyOn(comp, 'showDetails');

        fixture.componentRef.setInput('isInSidebarCard', false);
        comp.resultIconClass = faTimesCircle;
        comp.exercise.set({ type: ExerciseType.PROGRAMMING, numberOfAssessmentsOfCorrectionRounds: [], secondCorrectionEnabled: false, studentAssignedTeamIdComputed: false });
        comp.result.set(mockResult);
        comp.resultIconClass = faTimesCircle;
        comp.templateStatus = ResultTemplateStatus.HAS_RESULT;
        fixture.changeDetectorRef.detectChanges();
        const resultElement = fixture.debugElement.query(By.css('#result-score'));
        resultElement.triggerEventHandler('click', null);
        expect(detailsSpy).toHaveBeenCalledWith(mockResult);

        detailsSpy.mockClear();
        fixture.componentRef.setInput('isInSidebarCard', true);
        fixture.changeDetectorRef.detectChanges();
        resultElement.triggerEventHandler('click', null);
        expect(detailsSpy).not.toHaveBeenCalled();
    });

    it('should display building message for IS_BUILDING status', () => {
        comp.templateStatus = ResultTemplateStatus.IS_BUILDING;
        fixture.changeDetectorRef.detectChanges();
        const compiled = fixture.debugElement.query(By.css('[jhiTranslate$=building]'));
        expect(compiled).toBeTruthy();
    });

    it('should display badge when showBadge is true', () => {
        fixture.componentRef.setInput('showBadge', true);
        comp.templateStatus = ResultTemplateStatus.HAS_RESULT;
        comp.result.set(mockResult);
        comp.resultIconClass = faTimesCircle;
        fixture.changeDetectorRef.detectChanges();
        const badge = fixture.nativeElement.querySelector('#result-score-badge');
        expect(badge).toBeTruthy();
    });

    it('should display the correct message for FAILED_PROGRAMMING_SUBMISSION_OFFLINE_IDE and FAILED_PROGRAMMING_SUBMISSION_ONLINE_IDE', () => {
        // setInput triggers ngOnChanges which calls evaluate() and may reset templateStatus,
        // so we set MISSING after the input change and force re-render.
        fixture.componentRef.setInput('missingResultInfo', MissingResultInformation.FAILED_PROGRAMMING_SUBMISSION_OFFLINE_IDE);
        fixture.changeDetectorRef.detectChanges();
        comp.templateStatus = ResultTemplateStatus.MISSING;
        fixture.changeDetectorRef.detectChanges();
        let compiled = fixture.nativeElement;
        let spanElement = compiled.querySelector('span[jhiTranslate="artemisApp.result.missing.programmingFailedSubmission.message"]');
        expect(spanElement).not.toBeNull();
        expect(spanElement.getAttribute('jhiTranslate')).toBe('artemisApp.result.missing.programmingFailedSubmission.message');

        // Test for FAILED_PROGRAMMING_SUBMISSION_ONLINE_IDE
        fixture.componentRef.setInput('missingResultInfo', MissingResultInformation.FAILED_PROGRAMMING_SUBMISSION_ONLINE_IDE);
        fixture.changeDetectorRef.detectChanges();
        comp.templateStatus = ResultTemplateStatus.MISSING;
        fixture.changeDetectorRef.detectChanges();
        compiled = fixture.nativeElement;
        spanElement = compiled.querySelector('span[jhiTranslate="artemisApp.result.missing.programmingFailedSubmission.message"]');
        expect(spanElement).not.toBeNull();
        expect(spanElement.getAttribute('jhiTranslate')).toBe('artemisApp.result.missing.programmingFailedSubmission.message');
    });

    it('should display the submitted text for SUBMITTED template status', () => {
        comp.templateStatus = ResultTemplateStatus.SUBMITTED;
        fixture.changeDetectorRef.detectChanges();
        const submittedSpan = fixture.nativeElement.querySelector('#test-submitted');
        expect(submittedSpan).toBeTruthy();
    });

    it('should display the submitted text for SUBMITTED_WAITING_FOR_GRADING template status', () => {
        comp.templateStatus = ResultTemplateStatus.SUBMITTED_WAITING_FOR_GRADING;
        fixture.changeDetectorRef.detectChanges();
        const submittedSpan = fixture.nativeElement.querySelector('#test-submitted-waiting-grading');
        expect(submittedSpan).toBeTruthy();
    });

    it('should display the submitted text for LATE_NO_FEEDBACK template status', () => {
        comp.templateStatus = ResultTemplateStatus.LATE_NO_FEEDBACK;
        fixture.changeDetectorRef.detectChanges();
        const submittedSpan = fixture.nativeElement.querySelector('#test-late-no-feedback');
        expect(submittedSpan).toBeTruthy();
    });
    it('should display the submitted text for LATE template status', () => {
        comp.resultIconClass = faTimesCircle;
        comp.templateStatus = ResultTemplateStatus.LATE;
        fixture.changeDetectorRef.detectChanges();
        const submittedSpan = fixture.nativeElement.querySelector('#test-late');
        expect(submittedSpan).toBeTruthy();
    });

    describe('ResultComponent - Graded Results', () => {
        beforeEach(() => {
            comp.participation.set(mockParticipation);
        });

        it('should display the first rated result if showUngradedResults is false', () => {
            comp.participation()!.submissions![0].results = [{ id: 2, rated: false, score: 50 } as Result, mockResult, { id: 3, rated: false, score: 70 } as Result];
            fixture.componentRef.setInput('showUngradedResults', false);
            comp.ngOnInit();

            expect(comp.result()).toEqual(mockResult);
        });

        it('should display the first result if showUngradedResults is true', () => {
            comp.participation()!.submissions![0].results = [{ id: 2, rated: false, score: 50 } as Result, mockResult];
            fixture.componentRef.setInput('showUngradedResults', true);
            comp.ngOnInit();

            expect(comp.result()).toEqual(comp.participation()!.submissions![0].results![0]);
        });

        it('should not have a result if there are no rated results and showUngradedResults is false', () => {
            comp.participation()!.submissions![0].results = [{ id: 2, rated: false, score: 50 } as Result, { id: 3, rated: false, score: 70 } as Result];
            fixture.componentRef.setInput('showUngradedResults', false);
            comp.ngOnInit();

            expect(comp.result()).toBeUndefined();
        });
    });

    describe('ResultComponent - Feedback Generation', () => {
        beforeEach(() => {
            vi.useFakeTimers();
            comp.result.set({ ...mockResult, assessmentType: AssessmentType.AUTOMATIC_ATHENA, successful: undefined, completionDate: dayjs().add(1, 'minute') });
            comp.exercise.set(mockExercise);
            comp.participation.set(mockParticipation);
        });

        afterEach(() => {
            vi.clearAllTimers();
        });

        it('should call evaluate again after the specified due time', () => {
            comp.result.set({ ...comp.result()!, completionDate: dayjs().add(2, 'seconds') });
            comp.templateStatus = ResultTemplateStatus.IS_GENERATING_FEEDBACK;
            comp.evaluate();

            comp.result()!.completionDate = dayjs().subtract(2, 'seconds');
            vi.runOnlyPendingTimers();

            expect(comp.templateStatus).not.toEqual(ResultTemplateStatus.IS_GENERATING_FEEDBACK);
        });

        it('should clear the timeout if the component is destroyed before the feedback generation is complete', () => {
            comp.templateStatus = ResultTemplateStatus.IS_GENERATING_FEEDBACK;
            comp.evaluate();
            expect(vi.getTimerCount()).toBe(1);

            comp.ngOnDestroy();
            expect(vi.getTimerCount()).toBe(0);
        });
    });

    it('should use special handling if result is an automatic AI result', () => {
        comp.result.set({ ...mockResult, score: 90, assessmentType: AssessmentType.AUTOMATIC_ATHENA });

        comp.evaluate();

        expect(comp.templateStatus).toEqual(ResultTemplateStatus.HAS_RESULT);
        expect(comp.resultTooltip).toContain('artemisApp.result.resultString.automaticAIFeedbackSuccessfulTooltip');
    });

    it('should trigger Interval creation on estimatedCompletionDate change', () => {
        vi.useFakeTimers();
        fixture.componentRef.setInput('buildStartDate', dayjs().subtract(20, 'seconds'));
        fixture.componentRef.setInput('estimatedCompletionDate', dayjs().add(20, 'seconds'));
        comp.ngOnChanges({});

        vi.advanceTimersByTime(1200);
        expect(comp.estimatedDurationInterval).toBeDefined();
        expect(comp.estimatedRemaining).toBeGreaterThan(0);
        expect(comp.estimatedRemaining).toBeLessThan(40);
        expect(comp.estimatedDuration).toBe(40);

        vi.clearAllTimers();
        vi.useRealTimers();
    });
});
