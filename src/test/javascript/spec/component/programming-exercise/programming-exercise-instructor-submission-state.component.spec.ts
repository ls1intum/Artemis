import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateModule } from '@ngx-translate/core';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { DebugElement } from '@angular/core';
import { SinonStub, stub } from 'sinon';
import { of, Subject } from 'rxjs';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockParticipationWebsocketService } from '../../helpers/mocks/service/mock-participation-websocket.service';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseSubmissionState, ProgrammingSubmissionService, ProgrammingSubmissionState } from 'app/exercises/programming/participate/programming-submission.service';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { ProgrammingExerciseInstructorSubmissionStateComponent } from 'app/exercises/programming/shared/actions/programming-exercise-instructor-submission-state.component';
import { triggerChanges } from '../../helpers/utils/general.utils';
import { BuildRunState, ProgrammingBuildRunService } from 'app/exercises/programming/participate/programming-build-run.service';
import { MockProgrammingBuildRunService } from '../../helpers/mocks/service/mock-programming-build-run.service';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { MockFeatureToggleService } from '../../helpers/mocks/service/mock-feature-toggle.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('ProgrammingExerciseInstructorSubmissionStateComponent', () => {
    let comp: ProgrammingExerciseInstructorSubmissionStateComponent;
    let fixture: ComponentFixture<ProgrammingExerciseInstructorSubmissionStateComponent>;
    let debugElement: DebugElement;
    let submissionService: ProgrammingSubmissionService;
    let buildRunService: ProgrammingBuildRunService;

    let getExerciseSubmissionStateStub: SinonStub;
    let getExerciseSubmissionStateSubject: Subject<ExerciseSubmissionState>;

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    let getBuildRunStateStub: SinonStub;
    let getBuildRunStateSubject: Subject<BuildRunState>;

    let triggerAllStub: SinonStub;
    let triggerParticipationsStub: SinonStub;

    const exercise = { id: 20 } as Exercise;

    const resultEtaId = '#result-eta';

    const getResultEtaContainer = () => {
        return debugElement.query(By.css(resultEtaId));
    };

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisProgrammingExerciseActionsModule],
            providers: [
                JhiLanguageHelper,
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: ProgrammingBuildRunService, useClass: MockProgrammingBuildRunService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: FeatureToggleService, useClass: MockFeatureToggleService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseInstructorSubmissionStateComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;

                submissionService = debugElement.injector.get(ProgrammingSubmissionService);
                buildRunService = debugElement.injector.get(ProgrammingBuildRunService);

                getExerciseSubmissionStateSubject = new Subject<ExerciseSubmissionState>();
                getExerciseSubmissionStateStub = stub(submissionService, 'getSubmissionStateOfExercise').returns(getExerciseSubmissionStateSubject);

                getBuildRunStateSubject = new Subject<BuildRunState>();
                getBuildRunStateStub = stub(buildRunService, 'getBuildRunUpdates').returns(getBuildRunStateSubject);

                triggerAllStub = stub(submissionService, 'triggerInstructorBuildForParticipationsOfExercise').returns(of());
                triggerParticipationsStub = stub(submissionService, 'triggerInstructorBuildForAllParticipationsOfExercise').returns(of());
            });
    });

    afterEach(() => {
        getExerciseSubmissionStateStub.restore();
        triggerAllStub.restore();
        getBuildRunStateStub.restore();
        triggerParticipationsStub.restore();
    });

    const getTriggerAllButton = () => {
        const triggerButton = debugElement.query(By.css('#trigger-all-button button'));
        return triggerButton ? triggerButton.nativeElement : null;
    };

    const getTriggerFailedButton = () => {
        const triggerButton = debugElement.query(By.css('#trigger-failed-button button'));
        return triggerButton ? triggerButton.nativeElement : null;
    };

    const getBuildState = () => {
        const buildState = debugElement.query(By.css('#build-state'));
        return buildState ? buildState.nativeElement : null;
    };

    it('should not show the component before the build summary is retrieved', () => {
        expect(getTriggerAllButton()).to.be.null;
        expect(getTriggerFailedButton()).to.be.null;
        expect(getBuildState()).to.be.null;
    });

    it('should show the result eta if there is at least one building submission', fakeAsync(() => {
        const isBuildingSubmissionState = {
            1: { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId: 4 },
            4: { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: undefined, participationId: 5 },
        } as ExerciseSubmissionState;
        comp.exercise = exercise as ProgrammingExercise;

        triggerChanges(comp, { property: 'exercise', currentValue: comp.exercise });
        getExerciseSubmissionStateSubject.next(isBuildingSubmissionState);

        tick(500);

        fixture.detectChanges();

        const resultEta = getResultEtaContainer();
        expect(resultEta).to.exist;
    }));

    it('should not show the result eta if there is no building submission', fakeAsync(() => {
        const isNotBuildingSubmission = {
            1: { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId: 4 },
            4: { submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, submission: undefined, participationId: 5 },
        } as ExerciseSubmissionState;
        comp.exercise = exercise as ProgrammingExercise;

        triggerChanges(comp, { property: 'exercise', currentValue: comp.exercise });
        getExerciseSubmissionStateSubject.next(isNotBuildingSubmission);

        tick(500);

        fixture.detectChanges();

        const resultEta = getResultEtaContainer();
        expect(resultEta).not.to.exist;
    }));

    it('should show & enable the trigger all button and the build state once the build summary is loaded', fakeAsync(() => {
        const noPendingSubmissionState = {
            1: { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId: 4 },
            4: { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId: 5 },
        } as ExerciseSubmissionState;
        const compressedSummary = { [ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION]: 2 };
        comp.exercise = exercise as ProgrammingExercise;

        triggerChanges(comp, { property: 'exercise', currentValue: comp.exercise });
        getExerciseSubmissionStateSubject.next(noPendingSubmissionState);

        // Wait for a second as the view is updated with a debounce.
        tick(500);

        fixture.detectChanges();

        expect(getExerciseSubmissionStateStub).to.have.been.calledOnceWithExactly(exercise.id);

        expect(comp.hasFailedSubmissions).to.be.false;
        expect(comp.isBuildingFailedSubmissions).to.be.false;
        expect(comp.buildingSummary).to.deep.equal(compressedSummary);

        expect(getTriggerAllButton()).to.exist;
        expect(getTriggerAllButton().disabled).to.be.false;
        expect(getTriggerFailedButton()).to.exist;
        expect(getTriggerFailedButton().disabled).to.be.true;
        expect(getBuildState()).to.exist;
    }));

    it('should show & enable both buttons and the build state once the build summary is loaded when a failed submission exists', fakeAsync(() => {
        const noPendingSubmissionState = {
            1: { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId: 55 },
            4: { submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, submission: undefined, participationId: 76 },
            5: { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: undefined, participationId: 76 },
        } as ExerciseSubmissionState;
        const compressedSummary = {
            [ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION]: 1,
            [ProgrammingSubmissionState.HAS_FAILED_SUBMISSION]: 1,
            [ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION]: 1,
        };
        comp.exercise = exercise as ProgrammingExercise;

        triggerChanges(comp, { property: 'exercise', currentValue: comp.exercise });
        getExerciseSubmissionStateSubject.next(noPendingSubmissionState);

        // Wait for a second as the view is updated with a debounce.
        tick(500);

        fixture.detectChanges();

        expect(getExerciseSubmissionStateStub).to.have.been.calledOnceWithExactly(exercise.id);

        expect(comp.hasFailedSubmissions).to.be.true;
        expect(comp.isBuildingFailedSubmissions).to.be.false;
        expect(comp.buildingSummary).to.deep.equal(compressedSummary);

        expect(getResultEtaContainer()).to.exist;
        expect(getTriggerAllButton()).to.exist;
        expect(getTriggerAllButton().disabled).to.be.false;
        expect(getTriggerFailedButton()).to.exist;
        expect(getTriggerFailedButton().disabled).to.be.false;
        expect(getBuildState()).to.exist;
    }));

    it('should trigger the appropriate service method on trigger failed and set the isBuildingFailedSubmissionsState until the request returns a response', () => {
        const failedSubmissionParticipationIds = [333];
        const triggerInstructorBuildForParticipationsOfExerciseSubject = new Subject<void>();
        triggerAllStub.returns(triggerInstructorBuildForParticipationsOfExerciseSubject);
        const getFailedSubmissionParticipationsForExerciseStub = stub(submissionService, 'getSubmissionCountByType').returns(failedSubmissionParticipationIds);
        // Component must have at least one failed submission for the button to be enabled.
        comp.exercise = exercise as ProgrammingExercise;
        comp.buildingSummary = { [ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION]: 1, [ProgrammingSubmissionState.HAS_FAILED_SUBMISSION]: 1 };
        comp.hasFailedSubmissions = true;

        fixture.detectChanges();

        const triggerButton = getTriggerFailedButton();
        expect(triggerButton).to.exist;
        expect(triggerButton.disabled).to.be.false;

        // Button is clicked.
        triggerButton.click();

        expect(comp.isBuildingFailedSubmissions).to.be.true;
        expect(getFailedSubmissionParticipationsForExerciseStub).to.have.been.calledOnceWithExactly(comp.exercise.id, ProgrammingSubmissionState.HAS_FAILED_SUBMISSION);
        expect(triggerAllStub).to.have.been.calledOnceWithExactly(comp.exercise.id, failedSubmissionParticipationIds);

        fixture.detectChanges();

        // Now the request returns a response.
        triggerInstructorBuildForParticipationsOfExerciseSubject.next(undefined);

        fixture.detectChanges();

        expect(comp.isBuildingFailedSubmissions).to.be.false;
    });

    it('should disable the trigger all button while a build is running and re-enable it when it is complete', fakeAsync(() => {
        const isBuildingSubmissionState = {
            1: { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId: 4 },
            4: { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: undefined, participationId: 5 },
        } as ExerciseSubmissionState;
        comp.exercise = exercise as ProgrammingExercise;

        triggerChanges(comp, { property: 'exercise', currentValue: comp.exercise });
        getExerciseSubmissionStateSubject.next(isBuildingSubmissionState);

        // Wait for a second as the view is updated with a debounce.
        tick(500);

        fixture.detectChanges();

        expect(getTriggerAllButton().disabled).to.be.false;

        getBuildRunStateSubject.next(BuildRunState.RUNNING);
        fixture.detectChanges();

        expect(getTriggerAllButton().disabled).to.be.true;

        getBuildRunStateSubject.next(BuildRunState.COMPLETED);
        fixture.detectChanges();

        expect(getTriggerAllButton().disabled).to.be.false;
    }));
});
