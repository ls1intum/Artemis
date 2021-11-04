import * as chai from 'chai';
import { BehaviorSubject, Observable, of } from 'rxjs';
import * as sinon from 'sinon';
import { SinonStub, spy, stub } from 'sinon';
import sinonChai from 'sinon-chai';
import { OrionAssessmentService } from 'app/orion/assessment/orion-assessment.service';
import { ArtemisTestModule } from '../../test.module';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { ProgrammingAssessmentRepoExportService } from 'app/exercises/programming/assess/repo-export/programming-assessment-repo-export.service';
import { MockProvider } from 'ng-mocks';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { OrionState } from 'app/shared/orion/orion';
import { ProgrammingAssessmentManualResultService } from 'app/exercises/programming/assess/manual-result/programming-assessment-manual-result.service';
import { AlertService } from 'app/core/util/alert.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('OrionAssessmentService', () => {
    let orionAssessmentService: OrionAssessmentService;
    let programmingSubmissionService: ProgrammingSubmissionService;
    let orionConnectorService: OrionConnectorService;
    let programmingAssessmentExportService: ProgrammingAssessmentRepoExportService;
    let alertService: AlertService;
    let stateStub: SinonStub;
    let stateObservable: BehaviorSubject<any>;

    const programmingSubmission = { id: 11, participation: { id: 1 } } as any;
    const orionState = { opened: 40, building: false, cloning: true } as OrionState;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [],
            providers: [
                MockProvider(OrionConnectorService),
                MockProvider(ProgrammingSubmissionService),
                MockProvider(ProgrammingAssessmentRepoExportService),
                MockProvider(ProgrammingAssessmentManualResultService),
            ],
        })
            .compileComponents()
            .then(() => {
                orionConnectorService = TestBed.inject(OrionConnectorService);
                stateStub = stub(orionConnectorService, 'state');
                stateObservable = new BehaviorSubject(orionState);
                stateStub.returns(stateObservable);
                orionAssessmentService = TestBed.inject(OrionAssessmentService);
                programmingSubmissionService = TestBed.inject(ProgrammingSubmissionService);
                programmingAssessmentExportService = TestBed.inject(ProgrammingAssessmentRepoExportService);
                alertService = TestBed.inject(AlertService);
            });
    });

    afterEach(() => {
        sinon.restore();
    });

    it('downloadSubmissionInOrion with new should call send', () => {
        const sendSubmissionToOrion = spy(orionAssessmentService, <any>'sendSubmissionToOrion');
        const getSubmission = stub(programmingSubmissionService, 'getProgrammingSubmissionForExerciseForCorrectionRoundWithoutAssessment');

        getSubmission.returns(of(programmingSubmission));

        orionAssessmentService.downloadSubmissionInOrion(16, 'new', 0);

        expect(getSubmission).to.have.been.calledOnceWithExactly(16, true, 0);
        expect(sendSubmissionToOrion).to.have.been.calledOnceWithExactly(16, programmingSubmission.id, 0);
    });
    it('downloadSubmissionInOrion with number should call send', () => {
        const sendSubmissionToOrion = stub(orionAssessmentService, <any>'sendSubmissionToOrion');

        orionAssessmentService.downloadSubmissionInOrion(16, programmingSubmission, 0);

        expect(sendSubmissionToOrion).to.have.been.calledOnceWithExactly(16, programmingSubmission.id, 0);
    });
    it('sendSubmissionToOrion should convert and call connector', () => {
        const downloadSubmissionSpy = spy(orionConnectorService, 'downloadSubmission');

        // mock FileReader
        const mockReader = {
            result: 'testBase64',
            // required, used to instantly trigger the callback
            // @ts-ignore
            readAsDataURL() {
                this.onloadend();
            },
        };

        testConversion(mockReader as any);

        expect(downloadSubmissionSpy).to.have.been.calledOnceWithExactly(11, 0, 'testBase64');
    });
    it('sendSubmissionToOrion should convert and report error', () => {
        const alertErrorStub = stub(alertService, 'error');

        // mock FileReader
        const mockReader = {
            result: 'testBase64',
            // required, used to instantly trigger the callback
            // @ts-ignore
            readAsDataURL() {
                this.onerror();
            },
        };

        testConversion(mockReader as any);

        expect(alertErrorStub).to.have.been.calledOnceWithExactly('artemisApp.assessmentDashboard.orion.downloadFailed');
    });

    /**
     * Helper to test the conversion with the fileReader
     * @param mockReader mock reader to test
     */
    function testConversion(mockReader: FileReader) {
        const isCloningSpy = spy(orionConnectorService, 'isCloning');
        const exportSubmissionStub = stub(programmingAssessmentExportService, 'exportReposByParticipations');
        const lockAndGetStub = stub(programmingSubmissionService, 'lockAndGetProgrammingSubmissionParticipation');
        const readerStub = stub(window, 'FileReader');

        // first it loads the submission
        lockAndGetStub.returns(of(programmingSubmission));
        // then the exported file
        const response = new HttpResponse({ body: new Blob(['Stuff', 'in blob']), status: 200 });
        exportSubmissionStub.returns(of(response));

        readerStub.returns(mockReader);

        orionAssessmentService.downloadSubmissionInOrion(16, programmingSubmission);

        expect(isCloningSpy).to.have.been.calledOnceWithExactly(true);
        expect(lockAndGetStub).to.have.been.calledOnceWith(11, 0);
        // ignore RepositoryExportOptions
        expect(exportSubmissionStub).to.have.been.calledOnceWith(16, [1]);
        expect(readerStub).to.have.been.calledOnce;
    }
    it('should cancel lock correctly', fakeAsync(() => {
        const cancelStub = stub(TestBed.inject(ProgrammingAssessmentManualResultService), 'cancelAssessment');
        cancelStub.returns(new Observable());

        tick();

        expect(orionAssessmentService.orionState).to.be.deep.equals(orionState);
        orionAssessmentService.activeSubmissionId = 24;

        stateObservable.next({ ...orionState, cloning: false });
        tick();

        expect(cancelStub).to.have.been.calledOnceWithExactly(24);
    }));
});
