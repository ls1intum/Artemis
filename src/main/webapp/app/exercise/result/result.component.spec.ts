import { expect, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ResultComponent } from 'app/exercise/result/result.component';
import { MissingResultInformation, ResultTemplateStatus } from 'app/exercise/result/result.utils';
import { ResultProgressBarComponent } from 'app/exercise/result/result-progress-bar/result-progress-bar.component';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/foundation/pipes/artemis-time-ago.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/foundation/pipes/artemis-duration-from-seconds.pipe';
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
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { Router } from '@angular/router';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission.model';

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
    showScoreChart: true,
    messageKey: 'artemisApp.result.notLatestSubmission',
    latestDueDate: dayjs().subtract(1, 'hours'),
    showMissingAutomaticFeedbackInformation: true,
};
const participationServiceMock = {
    downloadArtifact: vi.fn(),
};

describe('ResultComponent', () => {
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

        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
        (global as any).URL.revokeObjectURL = vi.fn();
    });

    it('should set the template status to IS_BUILDING when isBuilding is true, regardless of participation/result', () => {
        fixture.componentRef.setInput('participation', { type: ParticipationType.STUDENT, submissions: [] } as any as StudentParticipation);
        expect(comp.templateStatus()).toEqual(ResultTemplateStatus.NO_RESULT);

        fixture.componentRef.setInput('isBuilding', true);
        expect(comp.templateStatus()).toEqual(ResultTemplateStatus.IS_BUILDING);
    });

    describe('should display HAS_RESULT status properly', () => {
        const RESULT_SCORE_SELECTOR = '#result-score';

        it('should not display if result is not present', () => {
            fixture.detectChanges();
            const button = fixture.debugElement.nativeElement.querySelector(RESULT_SCORE_SELECTOR);
            expect(button).not.toBeTruthy();
        });

        it('should display result if present', () => {
            fixture.componentRef.setInput('exercise', mockExercise);
            fixture.componentRef.setInput('participation', mockParticipation);
            fixture.componentRef.setInput('result', mockResult);
            fixture.detectChanges();

            expect(comp.templateStatus()).toEqual(ResultTemplateStatus.HAS_RESULT);
            const button = fixture.debugElement.nativeElement.querySelector(RESULT_SCORE_SELECTOR);
            expect(button).toBeTruthy();
        });

        it('renders a failed programming build in red and keeps it clickable', () => {
            const buildFailedResult: Result = {
                id: 9,
                score: 0,
                rated: true,
                completionDate: dayjs().subtract(5, 'minutes'),
                assessmentType: AssessmentType.AUTOMATIC,
                submission: { id: 91, submissionExerciseType: SubmissionExerciseType.PROGRAMMING, buildFailed: true } as ProgrammingSubmission,
            };
            const programmingParticipation: Participation = {
                id: 5,
                type: ParticipationType.PROGRAMMING,
                exercise: mockExercise,
                submissions: [{ id: 91, submissionExerciseType: SubmissionExerciseType.PROGRAMMING, buildFailed: true } as ProgrammingSubmission],
            };

            fixture.componentRef.setInput('exercise', mockExercise);
            fixture.componentRef.setInput('participation', programmingParticipation);
            fixture.componentRef.setInput('result', buildFailedResult);
            fixture.detectChanges();

            expect(comp.templateStatus()).toEqual(ResultTemplateStatus.HAS_RESULT);
            expect(comp.textColorClass()).toBe('text-state-danger');
            expect(comp.canShowDetails()).toBe(true);
            expect(fixture.debugElement.nativeElement.querySelector(RESULT_SCORE_SELECTOR).classList).toContain('clickable-result');
        });

        describe('results that belong to no participation', () => {
            const exampleSubmissionResult: Result = {
                id: 7,
                score: 90,
                rated: true,
                completionDate: dayjs().subtract(1, 'hour'),
                submission: { id: 21 },
            };
            const modelingExercise = { id: 3, type: ExerciseType.MODELING, course: { id: 42 } } as Exercise;

            beforeEach(() => {
                fixture.componentRef.setInput('exercise', modelingExercise);
                fixture.componentRef.setInput('result', exampleSubmissionResult);
                fixture.detectChanges();
            });

            it('renders the result badge without a participation', () => {
                expect(comp.resolvedParticipation()).toBeUndefined();
                expect(comp.templateStatus()).toEqual(ResultTemplateStatus.HAS_RESULT);
                expect(fixture.debugElement.nativeElement.querySelector(RESULT_SCORE_SELECTOR)).toBeTruthy();
                expect(comp.textColorClass()).toBe('text-state-success');
                expect(comp.resultIconClass()).toBeDefined();
                expect(comp.resultString()).not.toBe('');
            });

            it('does not advertise the badge as clickable', () => {
                expect(comp.canShowDetails()).toBe(false);
                const badge = fixture.debugElement.nativeElement.querySelector(RESULT_SCORE_SELECTOR);
                expect(badge.classList).not.toContain('clickable-result');
            });

            it('does not navigate or open the feedback dialog when the badge is clicked', () => {
                const navigateSpy = vi.spyOn(router, 'navigate');
                const openModalSpy = vi.spyOn(dialogService, 'open');
                const prepareFeedbackSpy = vi.spyOn(utils, 'prepareFeedbackComponentParameters');

                fixture.debugElement.nativeElement.querySelector(RESULT_SCORE_SELECTOR).dispatchEvent(new Event('click'));

                expect(navigateSpy).not.toHaveBeenCalled();
                expect(openModalSpy).not.toHaveBeenCalled();
                expect(prepareFeedbackSpy).not.toHaveBeenCalled();
            });
        });

        it('should display modal onClick and initialize results modal', () => {
            const showDetailsSpy = vi.spyOn(comp, 'showDetails');
            const openModalSpy = vi.spyOn(dialogService, 'open');
            const prepareFeedbackSpy = vi.spyOn(utils, 'prepareFeedbackComponentParameters').mockReturnValue(preparedFeedback);

            fixture.componentRef.setInput('exercise', preparedFeedback.exercise);
            fixture.componentRef.setInput('participation', mockParticipation);
            fixture.componentRef.setInput('result', mockResult);
            fixture.detectChanges();

            const button = fixture.debugElement.nativeElement.querySelector(RESULT_SCORE_SELECTOR);
            expect(button).toBeTruthy();

            button.dispatchEvent(new Event('click'));

            expect(showDetailsSpy).toHaveBeenCalled();
            expect(openModalSpy).toHaveBeenCalledWith(
                FeedbackComponent,
                expect.objectContaining({
                    header: 'artemisApp.result.detail.feedback',
                    width: '80rem',
                    breakpoints: {
                        '1400px': '75vw',
                        '1200px': '85vw',
                        '992px': '95vw',
                    },
                    modal: true,
                    closable: true,
                    closeOnEscape: true,
                    dismissableMask: true,
                    focusOnShow: false,
                    inputValues: expect.objectContaining({
                        exercise: preparedFeedback.exercise,
                        result: preparedFeedback.result,
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
        fixture.componentRef.setInput('exercise', { ...mockExercise, type: ExerciseType.TEXT });
        fixture.componentRef.setInput('participation', mockParticipation);
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
        fixture.componentRef.setInput('exercise', { ...mockExercise, type: ExerciseType.MODELING });
        fixture.componentRef.setInput('participation', mockParticipation);
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

    it('should open the details only when isInSidebarCard is false', () => {
        const openModalSpy = vi.spyOn(dialogService, 'open');

        fixture.componentRef.setInput('exercise', {
            type: ExerciseType.PROGRAMMING,
            numberOfAssessmentsOfCorrectionRounds: [],
            secondCorrectionEnabled: false,
            studentAssignedTeamIdComputed: false,
        });
        fixture.componentRef.setInput('participation', mockParticipation);
        fixture.componentRef.setInput('result', mockResult);
        fixture.componentRef.setInput('isInSidebarCard', false);
        fixture.detectChanges();

        expect(comp.templateStatus()).toEqual(ResultTemplateStatus.HAS_RESULT);
        expect(comp.canShowDetails()).toBe(true);
        fixture.debugElement.query(By.css('#result-score')).triggerEventHandler('click', null);
        expect(openModalSpy).toHaveBeenCalledOnce();

        openModalSpy.mockClear();
        fixture.componentRef.setInput('isInSidebarCard', true);
        fixture.detectChanges();
        expect(comp.canShowDetails()).toBe(false);
        fixture.debugElement.query(By.css('#result-score')).triggerEventHandler('click', null);
        expect(openModalSpy).not.toHaveBeenCalled();
    });

    it('should display building message for IS_BUILDING status', () => {
        fixture.componentRef.setInput('isBuilding', true);
        fixture.detectChanges();
        const compiled = fixture.debugElement.query(By.css('[jhiTranslate$=building]'));
        expect(compiled).toBeTruthy();
    });

    it('should display badge when showBadge is true', () => {
        fixture.componentRef.setInput('showBadge', true);
        fixture.componentRef.setInput('exercise', mockExercise);
        fixture.componentRef.setInput('participation', mockParticipation);
        fixture.componentRef.setInput('result', mockResult);
        fixture.detectChanges();
        const badge = fixture.nativeElement.querySelector('#result-score-badge');
        expect(badge).toBeTruthy();
    });

    it('should display the correct message for FAILED_PROGRAMMING_SUBMISSION_OFFLINE_IDE and FAILED_PROGRAMMING_SUBMISSION_ONLINE_IDE', () => {
        fixture.componentRef.setInput('exercise', mockExercise);
        fixture.componentRef.setInput('participation', mockParticipation);
        fixture.componentRef.setInput('missingResultInfo', MissingResultInformation.FAILED_PROGRAMMING_SUBMISSION_OFFLINE_IDE);
        fixture.detectChanges();
        expect(comp.templateStatus()).toBe(ResultTemplateStatus.MISSING);
        let spanElement = fixture.nativeElement.querySelector('span[jhiTranslate="artemisApp.result.missing.programmingFailedSubmission.message"]');
        expect(spanElement).not.toBeNull();

        fixture.componentRef.setInput('missingResultInfo', MissingResultInformation.FAILED_PROGRAMMING_SUBMISSION_ONLINE_IDE);
        fixture.detectChanges();
        spanElement = fixture.nativeElement.querySelector('span[jhiTranslate="artemisApp.result.missing.programmingFailedSubmission.message"]');
        expect(spanElement).not.toBeNull();
    });

    it('should use special handling if result is an automatic AI result', () => {
        fixture.componentRef.setInput('exercise', mockExercise);
        fixture.componentRef.setInput('participation', mockParticipation);
        fixture.componentRef.setInput('result', { ...mockResult, score: 90, assessmentType: AssessmentType.AUTOMATIC_ATHENA, successful: true });

        expect(comp.templateStatus()).toEqual(ResultTemplateStatus.HAS_RESULT);
        expect(comp.resultTooltip()).toContain('artemisApp.result.resultString.automaticAIFeedbackSuccessfulTooltip');
    });

    describe('Athena feedback generation', () => {
        beforeEach(() => {
            vi.useFakeTimers();
        });

        afterEach(() => {
            vi.clearAllTimers();
            vi.useRealTimers();
        });

        it('should leave IS_GENERATING_FEEDBACK once the result completion time has passed', () => {
            fixture.componentRef.setInput('exercise', mockExercise);
            fixture.componentRef.setInput('participation', mockParticipation);
            fixture.componentRef.setInput('result', {
                ...mockResult,
                assessmentType: AssessmentType.AUTOMATIC_ATHENA,
                successful: undefined,
                completionDate: dayjs().add(2, 'seconds'),
            });
            fixture.detectChanges();

            expect(comp.templateStatus()).toEqual(ResultTemplateStatus.IS_GENERATING_FEEDBACK);

            // The component schedules a re-check at the completion time; advancing past it must flip the status.
            vi.advanceTimersByTime(2001);
            expect(comp.templateStatus()).toEqual(ResultTemplateStatus.FEEDBACK_GENERATION_TIMED_OUT);
        });

        it('should clear the feedback re-check timeout when the component is destroyed', () => {
            const clearTimeoutSpy = vi.spyOn(globalThis, 'clearTimeout');
            fixture.componentRef.setInput('exercise', mockExercise);
            fixture.componentRef.setInput('participation', mockParticipation);
            fixture.componentRef.setInput('result', {
                ...mockResult,
                assessmentType: AssessmentType.AUTOMATIC_ATHENA,
                successful: undefined,
                completionDate: dayjs().add(1, 'minute'),
            });
            fixture.detectChanges();
            expect(comp.templateStatus()).toEqual(ResultTemplateStatus.IS_GENERATING_FEEDBACK);

            fixture.destroy();
            expect(clearTimeoutSpy).toHaveBeenCalled();
        });
    });

    it('should update the estimated build duration once the build dates are set', () => {
        vi.useFakeTimers();
        fixture.componentRef.setInput('buildStartDate', dayjs().subtract(20, 'seconds'));
        fixture.componentRef.setInput('estimatedCompletionDate', dayjs().add(20, 'seconds'));
        fixture.detectChanges();

        vi.advanceTimersByTime(1200);
        expect(comp.estimatedRemaining()).toBeGreaterThan(0);
        expect(comp.estimatedRemaining()).toBeLessThan(40);
        expect(comp.estimatedDuration()).toBe(40);

        vi.clearAllTimers();
        vi.useRealTimers();
    });
});
