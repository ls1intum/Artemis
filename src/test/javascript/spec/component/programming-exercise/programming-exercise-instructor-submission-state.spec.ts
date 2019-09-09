import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateModule } from '@ngx-translate/core';
import { JhiLanguageHelper } from 'app/core';
import { DebugElement, SimpleChange, SimpleChanges } from '@angular/core';
import { SinonStub, stub } from 'sinon';
import { of, Subject } from 'rxjs';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { MockParticipationWebsocketService, MockSyncStorage } from '../../mocks';
import { ParticipationWebsocketService } from 'app/entities/participation';
import { Exercise } from 'app/entities/exercise';
import { ExerciseSubmissionState, ProgrammingSubmissionService, ProgrammingSubmissionState } from 'app/programming-submission/programming-submission.service';
import { ArtemisProgrammingExerciseActionsModule } from 'app/entities/programming-exercise/actions/programming-exercise-actions.module';
import { ProgrammmingExerciseInstructorSubmissionStateComponent } from 'app/entities/programming-exercise/actions/programmming-exercise-instructor-submission-state.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('ProgrammingExerciseInstructorSubmissionState', () => {
    let comp: ProgrammmingExerciseInstructorSubmissionStateComponent;
    let fixture: ComponentFixture<ProgrammmingExerciseInstructorSubmissionStateComponent>;
    let debugElement: DebugElement;
    let submissionService: ProgrammingSubmissionService;

    let getExerciseSubmissionStateStub: SinonStub;
    let getExerciseSubmissionStateSubject: Subject<ExerciseSubmissionState>;

    let triggerAllStub: SinonStub;
    let triggerParticipationsStub: SinonStub;

    const exercise = { id: 20 } as Exercise;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisProgrammingExerciseActionsModule],
            providers: [
                JhiLanguageHelper,
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammmingExerciseInstructorSubmissionStateComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;

                submissionService = debugElement.injector.get(ProgrammingSubmissionService);

                getExerciseSubmissionStateSubject = new Subject<ExerciseSubmissionState>();
                getExerciseSubmissionStateStub = stub(submissionService, 'getSubmissionStateOfExercise').returns(getExerciseSubmissionStateSubject);

                triggerAllStub = stub(submissionService, 'triggerInstructorBuildForParticipationsOfExercise').returns(of());
                triggerParticipationsStub = stub(submissionService, 'triggerInstructorBuildForAllParticipationsOfExercise').returns(of());
            });
    });

    afterEach(() => {
        getExerciseSubmissionStateStub.restore();
        triggerAllStub.restore();
        triggerParticipationsStub.restore();
    });

    const getTriggerAllButton = () => {
        const triggerButton = debugElement.query(By.css('#trigger-all-button'));
        return triggerButton ? triggerButton.nativeElement : null;
    };

    const getTriggerFailedButton = () => {
        const triggerButton = debugElement.query(By.css('#trigger-failed-button'));
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

    it('should show & enable the trigger all button and the build state once the build summary is loaded', fakeAsync(() => {
        const noPendingSubmissionState = {
            1: { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: null, participationId: 4 },
            4: { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: null, participationId: 5 },
        } as ExerciseSubmissionState;
        const compressedSummary = { [ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION]: 2 };
        comp.exerciseId = exercise.id;
        const changes: SimpleChanges = {
            exerciseId: new SimpleChange(undefined, comp.exerciseId, true),
        };
        comp.ngOnChanges(changes);

        getExerciseSubmissionStateSubject.next(noPendingSubmissionState);

        // Wait for a second as the view is updated with a debounce.
        tick(1000);

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
            1: { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: null, participationId: 55 },
            4: { submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, submission: null, participationId: 76 },
        } as ExerciseSubmissionState;
        const compressedSummary = { [ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION]: 1, [ProgrammingSubmissionState.HAS_FAILED_SUBMISSION]: 1 };
        comp.exerciseId = exercise.id;
        const changes: SimpleChanges = {
            exerciseId: new SimpleChange(undefined, comp.exerciseId, true),
        };
        comp.ngOnChanges(changes);

        getExerciseSubmissionStateSubject.next(noPendingSubmissionState);

        // Wait for a second as the view is updated with a debounce.
        tick(1000);

        fixture.detectChanges();

        expect(getExerciseSubmissionStateStub).to.have.been.calledOnceWithExactly(exercise.id);

        expect(comp.hasFailedSubmissions).to.be.true;
        expect(comp.isBuildingFailedSubmissions).to.be.false;
        expect(comp.buildingSummary).to.deep.equal(compressedSummary);

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
        comp.exerciseId = exercise.id;
        comp.buildingSummary = { [ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION]: 1, [ProgrammingSubmissionState.HAS_FAILED_SUBMISSION]: 1 };
        comp.hasFailedSubmissions = true;

        fixture.detectChanges();

        const triggerButton = getTriggerFailedButton();
        expect(triggerButton).to.exist;
        expect(triggerButton.disabled).to.be.false;

        // Button is clicked.
        triggerButton.click();

        expect(comp.isBuildingFailedSubmissions).to.be.true;
        expect(getFailedSubmissionParticipationsForExerciseStub).to.have.been.calledOnceWithExactly(comp.exerciseId, ProgrammingSubmissionState.HAS_FAILED_SUBMISSION);
        expect(triggerAllStub).to.have.been.calledOnceWithExactly(comp.exerciseId, failedSubmissionParticipationIds);

        fixture.detectChanges();

        // Now the request returns a response.
        triggerInstructorBuildForParticipationsOfExerciseSubject.next(undefined);

        fixture.detectChanges();

        expect(comp.isBuildingFailedSubmissions).to.be.false;
    });
});
