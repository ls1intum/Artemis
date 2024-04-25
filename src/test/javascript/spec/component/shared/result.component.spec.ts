import { ArtemisTestModule } from '../../test.module';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ResultTemplateStatus } from 'app/exercises/shared/result/result.utils';
import { SimpleChange } from '@angular/core';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Result } from 'app/entities/result.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockPipe } from 'ng-mocks';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { Participation, ParticipationType } from 'app/entities/participation/participation.model';
import dayjs from 'dayjs/esm';
import { NgbTooltipMocksModule } from '../../helpers/mocks/directive/ngbTooltipMocks.module';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import * as utils from 'app/exercises/shared/feedback/feedback.utils';
import { FeedbackComponentPreparedParams } from 'app/exercises/shared/feedback/feedback.utils';
import { FeedbackComponent } from 'app/exercises/shared/feedback/feedback.component';
import { By } from '@angular/platform-browser';
import { MissingResultInformation } from 'app/exercises/shared/result/result.utils';
import { faTimesCircle } from '@fortawesome/free-solid-svg-icons';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';

import { of } from 'rxjs';

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
    feedbacks: [
        {
            id: 1,
            text: 'Well done!',
        },
    ],
    participation: mockParticipation,
};

const preparedFeedback: FeedbackComponentPreparedParams = {
    exercise: mockExercise,
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
    let mockLink: HTMLAnchorElement;

    beforeEach(async () => {
        participationServiceMock.downloadArtifact = jest.fn() as jest.Mock;
        global.URL.createObjectURL = jest.fn(() => 'blob:test-url');
        global.URL.revokeObjectURL = jest.fn();

        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgbTooltipMocksModule],
            declarations: [ResultComponent, TranslatePipeMock, MockPipe(ArtemisDatePipe), MockPipe(ArtemisTimeAgoPipe)],
            providers: [
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: ParticipationService, useValue: participationServiceMock },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ResultComponent);
                comp = fixture.componentInstance;
                modalService = TestBed.inject(NgbModal);

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

    it('should download build result when participation ID is provided', () => {
        // Arrange
        const fakeArtifact = {
            fileContent: new Blob(['test'], { type: 'text/plain' }),
            fileName: 'test.txt',
        };
        mockLink = document.createElement('a');
        jest.spyOn(document, 'createElement').mockReturnValue(mockLink);
        jest.spyOn(document.body, 'appendChild').mockImplementation((child) => child);
        jest.spyOn(document.body, 'removeChild').mockImplementation((child) => child);

        const urlSpy = jest.spyOn(URL, 'createObjectURL').mockReturnValue('blob:test-url');

        participationServiceMock.downloadArtifact.mockReturnValue(of(fakeArtifact));

        // Act
        comp.downloadBuildResult(123);

        // Assert
        expect(participationServiceMock.downloadArtifact).toHaveBeenCalledWith(123);
        expect(document.createElement).toHaveBeenCalledWith('a');
        expect(urlSpy).toHaveBeenCalledWith(fakeArtifact.fileContent);
        expect(mockLink.download).toBe(fakeArtifact.fileName);
        expect(mockLink.href).toBe('blob:test-url');
        expect(document.body.appendChild).toHaveBeenCalledWith(mockLink);
        // Cleanup to avoid memory leaks
        URL.revokeObjectURL(mockLink.href);
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

            fixture.detectChanges();

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

            fixture.detectChanges();

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

    it('should call showDetails only when isInSidebarCard is false', () => {
        comp.result = mockResult;
        const detailsSpy = jest.spyOn(comp, 'showDetails');

        comp.isInSidebarCard = false;
        comp.resultIconClass = faTimesCircle;
        comp.exercise = { type: ExerciseType.PROGRAMMING, numberOfAssessmentsOfCorrectionRounds: [], secondCorrectionEnabled: false, studentAssignedTeamIdComputed: false };
        comp.result = mockResult;
        comp.resultIconClass = faTimesCircle;
        comp.templateStatus = ResultTemplateStatus.HAS_RESULT;
        fixture.detectChanges();
        const resultElement = fixture.debugElement.query(By.css('#result-score'));
        resultElement.triggerEventHandler('click', null);
        expect(detailsSpy).toHaveBeenCalledWith(mockResult);

        detailsSpy.mockClear();
        comp.isInSidebarCard = true;
        fixture.detectChanges();
        resultElement.triggerEventHandler('click', null);
        expect(detailsSpy).not.toHaveBeenCalled();
    });

    it('should display building message for IS_BUILDING status', () => {
        comp.templateStatus = ResultTemplateStatus.IS_BUILDING;
        fixture.detectChanges();
        const compiled = fixture.debugElement.query(By.css('[jhiTranslate$=building]'));
        expect(compiled).toBeTruthy();
    });

    it('should display badge when showBadge is true', () => {
        comp.showBadge = true;
        comp.templateStatus = ResultTemplateStatus.HAS_RESULT;
        comp.result = mockResult;
        comp.resultIconClass = faTimesCircle;
        fixture.detectChanges();
        const badge = fixture.nativeElement.querySelector('#result-score-badge');
        expect(badge).toBeTruthy();
    });

    it('should display the correct message for FAILED_PROGRAMMING_SUBMISSION_OFFLINE_IDE and FAILED_PROGRAMMING_SUBMISSION_ONLINE_IDE', () => {
        comp.templateStatus = ResultTemplateStatus.MISSING;
        comp.missingResultInfo = MissingResultInformation.FAILED_PROGRAMMING_SUBMISSION_OFFLINE_IDE;
        fixture.detectChanges();
        const compiled = fixture.nativeElement;
        expect(compiled.textContent).toContain('artemisApp.result.missing.programmingFailedSubmission.message');
        comp.missingResultInfo = MissingResultInformation.FAILED_PROGRAMMING_SUBMISSION_OFFLINE_IDE;
        fixture.detectChanges();
        expect(compiled.textContent).toContain('artemisApp.result.missing.programmingFailedSubmission.message');
    });

    it('should display the submitted text for SUBMITTED template status', () => {
        comp.templateStatus = ResultTemplateStatus.SUBMITTED;
        fixture.detectChanges();
        const submittedSpan = fixture.nativeElement.querySelector('#test-submitted');
        expect(submittedSpan).toBeTruthy();
    });

    it('should display the submitted text for SUBMITTED_WAITING_FOR_GRADING template status', () => {
        comp.templateStatus = ResultTemplateStatus.SUBMITTED_WAITING_FOR_GRADING;
        fixture.detectChanges();
        const submittedSpan = fixture.nativeElement.querySelector('#test-submitted-waiting-grading');
        expect(submittedSpan).toBeTruthy();
    });

    it('should display the submitted text for LATE_NO_FEEDBACK template status', () => {
        comp.templateStatus = ResultTemplateStatus.LATE_NO_FEEDBACK;
        fixture.detectChanges();
        const submittedSpan = fixture.nativeElement.querySelector('#test-late-no-feedback');
        expect(submittedSpan).toBeTruthy();
    });
    it('should display the submitted text for LATE template status', () => {
        comp.resultIconClass = faTimesCircle;
        comp.templateStatus = ResultTemplateStatus.LATE;
        fixture.detectChanges();
        const submittedSpan = fixture.nativeElement.querySelector('#test-late');
        expect(submittedSpan).toBeTruthy();
    });
});
