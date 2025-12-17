import { ResultComponent } from 'app/exercise/result/result.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MissingResultInformation, ResultTemplateStatus } from 'app/exercise/result/result.utils';
import { SimpleChange } from '@angular/core';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { Participation, ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import dayjs from 'dayjs/esm';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
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
    downloadArtifact: jest.fn(),
};

describe('ResultComponent', () => {
    let comp: ResultComponent;
    let fixture: ComponentFixture<ResultComponent>;
    let modalService: NgbModal;
    let router: Router;

    beforeEach(async () => {
        participationServiceMock.downloadArtifact = jest.fn() as jest.Mock;
        global.URL.createObjectURL = jest.fn(() => 'blob:test-url');
        global.URL.revokeObjectURL = jest.fn();

        mockParticipation.submissions = [
            {
                id: 1,
                participation: mockParticipation,
                results: [mockResult],
            },
        ];

        await TestBed.configureTestingModule({
            declarations: [ResultComponent, TranslatePipeMock, MockPipe(ArtemisDatePipe), MockPipe(ArtemisTimeAgoPipe), MockDirective(TranslateDirective)],
            providers: [
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: ParticipationService, useValue: participationServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ResultComponent);
                comp = fixture.componentInstance;
                modalService = TestBed.inject(NgbModal);
                router = TestBed.inject(Router);

                participationServiceMock.downloadArtifact = jest.fn() as jest.Mock;

                comp.badge = {
                    tooltip: 'Example Tooltip',
                    class: 'Example Class',
                    text: 'Example Test',
                };
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
        global.URL.revokeObjectURL = jest.fn();
    });

    it('should set template status to BUILDING if isBuilding changes to true even though participation changes', () => {
        comp.participation = {
            results: [],
        } as any as StudentParticipation;

        comp.isBuilding = false;
        comp.ngOnInit();
        expect(comp.templateStatus).toEqual(ResultTemplateStatus.NO_RESULT);

        const newParticipation = {
            results: [],
        } as any as StudentParticipation;

        comp.isBuilding = true;
        comp.participation = newParticipation;
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
            comp.result = mockResult;
            comp.templateStatus = ResultTemplateStatus.HAS_RESULT;

            fixture.changeDetectorRef.detectChanges();

            const button = fixture.debugElement.nativeElement.querySelector(RESULT_SCORE_SELECTOR);
            expect(button).toBeTruthy();
        });

        it('should display modal onClick and initialize results modal', () => {
            const mockModalRef: NgbModalRef = { componentInstance: {} } as NgbModalRef;
            const modalComponentInstance: FeedbackComponent = mockModalRef.componentInstance;

            const showDetailsSpy = jest.spyOn(comp, 'showDetails');
            const openModalSpy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef);
            const prepareFeedbackSpy = jest.spyOn(utils, 'prepareFeedbackComponentParameters').mockReturnValue(preparedFeedback);

            comp.exercise = preparedFeedback.exercise;
            comp.result = mockResult;
            comp.resultIconClass = faTimesCircle;
            comp.templateStatus = ResultTemplateStatus.HAS_RESULT;

            fixture.changeDetectorRef.detectChanges();

            const button = fixture.debugElement.nativeElement.querySelector(RESULT_SCORE_SELECTOR);
            expect(button).toBeTruthy();

            button.dispatchEvent(new Event('click'));

            expect(showDetailsSpy).toHaveBeenCalled();
            expect(openModalSpy).toHaveBeenCalled();
            expect(prepareFeedbackSpy).toHaveBeenCalledOnce();
            expect(modalComponentInstance.exercise).toEqual(preparedFeedback.exercise);
            expect(modalComponentInstance.result).toEqual(preparedFeedback.result);
            expect(modalComponentInstance.exerciseType).toEqual(preparedFeedback.exerciseType);
            expect(modalComponentInstance.showScoreChart).toEqual(preparedFeedback.showScoreChart);
            expect(modalComponentInstance.messageKey).toEqual(preparedFeedback.messageKey);
            expect(modalComponentInstance.latestDueDate).toEqual(preparedFeedback.latestDueDate);
            expect(modalComponentInstance.showMissingAutomaticFeedbackInformation).toEqual(preparedFeedback.showMissingAutomaticFeedbackInformation);
        });
    });

    it('should navigate to text exercise details when exercise type is TEXT', () => {
        comp.exercise = { ...mockExercise, type: ExerciseType.TEXT };
        comp.participation = mockParticipation;
        const navigateSpy = jest.spyOn(router, 'navigate');
        const courseId = 42;
        comp.showDetails(mockResult);

        expect(navigateSpy).toHaveBeenCalledWith([
            '/courses',
            courseId,
            'exercises',
            'text-exercises',
            comp.exercise.id,
            'participate',
            mockParticipation.id,
            'submission',
            mockResult.submission?.id,
        ]);
    });

    it('should call showDetails only when isInSidebarCard is false', () => {
        comp.result = mockResult;
        const detailsSpy = jest.spyOn(comp, 'showDetails');

        comp.isInSidebarCard = false;
        comp.resultIconClass = faTimesCircle;
        comp.exercise = { type: ExerciseType.PROGRAMMING, numberOfAssessmentsOfCorrectionRounds: [], secondCorrectionEnabled: false, studentAssignedTeamIdComputed: false };
        comp.result = mockResult;
        comp.resultIconClass = faTimesCircle;
        comp.templateStatus = ResultTemplateStatus.HAS_RESULT;
        fixture.changeDetectorRef.detectChanges();
        const resultElement = fixture.debugElement.query(By.css('#result-score'));
        resultElement.triggerEventHandler('click', null);
        expect(detailsSpy).toHaveBeenCalledWith(mockResult);

        detailsSpy.mockClear();
        comp.isInSidebarCard = true;
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
        comp.showBadge = true;
        comp.templateStatus = ResultTemplateStatus.HAS_RESULT;
        comp.result = mockResult;
        comp.resultIconClass = faTimesCircle;
        fixture.changeDetectorRef.detectChanges();
        const badge = fixture.nativeElement.querySelector('#result-score-badge');
        expect(badge).toBeTruthy();
    });

    it('should display the correct message for FAILED_PROGRAMMING_SUBMISSION_OFFLINE_IDE and FAILED_PROGRAMMING_SUBMISSION_ONLINE_IDE', () => {
        comp.templateStatus = ResultTemplateStatus.MISSING;

        // Test for FAILED_PROGRAMMING_SUBMISSION_OFFLINE_IDE
        comp.missingResultInfo = MissingResultInformation.FAILED_PROGRAMMING_SUBMISSION_OFFLINE_IDE;
        fixture.changeDetectorRef.detectChanges();
        let compiled = fixture.nativeElement;
        let spanElement = compiled.querySelector('span[jhiTranslate="artemisApp.result.missing.programmingFailedSubmission.message"]');
        expect(spanElement).not.toBeNull();
        expect(spanElement.getAttribute('jhiTranslate')).toBe('artemisApp.result.missing.programmingFailedSubmission.message');

        // Test for FAILED_PROGRAMMING_SUBMISSION_ONLINE_IDE
        comp.missingResultInfo = MissingResultInformation.FAILED_PROGRAMMING_SUBMISSION_ONLINE_IDE;
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
            comp.participation = mockParticipation;
        });

        it('should display the first rated result if showUngradedResults is false', () => {
            comp.participation.submissions![0].results = [{ id: 2, rated: false, score: 50 } as Result, mockResult, { id: 3, rated: false, score: 70 } as Result];
            comp.showUngradedResults = false;
            comp.ngOnInit();

            expect(comp.result).toEqual(mockResult);
        });

        it('should display the first result if showUngradedResults is true', () => {
            comp.participation.submissions![0].results = [{ id: 2, rated: false, score: 50 } as Result, mockResult];
            comp.showUngradedResults = true;
            comp.ngOnInit();

            expect(comp.result).toEqual(comp.participation.submissions![0].results[0]);
        });

        it('should not have a result if there are no rated results and showUngradedResults is false', () => {
            comp.participation.submissions![0].results = [{ id: 2, rated: false, score: 50 } as Result, { id: 3, rated: false, score: 70 } as Result];
            comp.showUngradedResults = false;
            comp.ngOnInit();

            expect(comp.result).toBeUndefined();
        });
    });

    describe('ResultComponent - Feedback Generation', () => {
        beforeEach(() => {
            jest.useFakeTimers();
            comp.result = { ...mockResult, assessmentType: AssessmentType.AUTOMATIC_ATHENA, successful: undefined, completionDate: dayjs().add(1, 'minute') };
            comp.exercise = mockExercise;
            comp.participation = mockParticipation;
        });

        afterEach(() => {
            jest.clearAllTimers();
        });

        it('should call evaluate again after the specified due time', () => {
            comp.result = { ...comp.result, completionDate: dayjs().add(2, 'seconds') };
            comp.templateStatus = ResultTemplateStatus.IS_GENERATING_FEEDBACK;
            comp.evaluate();

            comp.result.completionDate = dayjs().subtract(2, 'seconds');
            jest.runOnlyPendingTimers();

            expect(comp.templateStatus).not.toEqual(ResultTemplateStatus.IS_GENERATING_FEEDBACK);
        });

        it('should clear the timeout if the component is destroyed before the feedback generation is complete', () => {
            comp.templateStatus = ResultTemplateStatus.IS_GENERATING_FEEDBACK;
            comp.evaluate();
            expect(jest.getTimerCount()).toBe(1);

            comp.ngOnDestroy();
            expect(jest.getTimerCount()).toBe(0);
        });
    });

    it('should use special handling if result is an automatic AI result', () => {
        comp.result = { ...mockResult, score: 90, assessmentType: AssessmentType.AUTOMATIC_ATHENA };

        comp.evaluate();

        expect(comp.templateStatus).toEqual(ResultTemplateStatus.HAS_RESULT);
        expect(comp.resultTooltip).toContain('artemisApp.result.resultString.automaticAIFeedbackSuccessfulTooltip');
    });

    it('should trigger Interval creation on estimatedCompletionDate change', () => {
        jest.useFakeTimers();
        comp.buildStartDate = dayjs().subtract(20, 'seconds');
        comp.estimatedCompletionDate = dayjs().add(20, 'seconds');
        comp.ngOnChanges({});

        jest.advanceTimersByTime(1200);
        expect(comp.estimatedDurationInterval).toBeDefined();
        expect(comp.estimatedRemaining).toBeGreaterThan(0);
        expect(comp.estimatedRemaining).toBeLessThan(40);
        expect(comp.estimatedDuration).toBe(40);

        jest.clearAllTimers();
        jest.useRealTimers();
    });
});
