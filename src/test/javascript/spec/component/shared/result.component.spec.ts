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
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';

const mockExercise: Exercise = {
    id: 1,
    title: 'Sample Exercise',
    maxPoints: 100,
    dueDate: dayjs().subtract(3, 'hours'),
    assessmentType: AssessmentType.AUTOMATIC,
    type: ExerciseType.PROGRAMMING,
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

describe('ResultComponent', () => {
    let comp: ResultComponent;
    let fixture: ComponentFixture<ResultComponent>;
    let modalService: NgbModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgbTooltipMocksModule, FontAwesomeTestingModule],
            declarations: [ResultComponent, TranslatePipeMock, MockPipe(ArtemisDatePipe), MockPipe(ArtemisTimeAgoPipe)],
            providers: [{ provide: NgbModal, useClass: MockNgbModalService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ResultComponent);
                comp = fixture.componentInstance;
                modalService = TestBed.inject(NgbModal);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
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
            const button = fixture.debugElement.nativeElement.querySelector(RESULT_SCORE_SELECTOR);
            expect(button).not.toBeTruthy();
        });

        it('should display result if present', () => {
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
});
