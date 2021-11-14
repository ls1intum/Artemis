import { BehaviorSubject, Observable, of } from 'rxjs';
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

describe('OrionAssessmentService', () => {
    let orionAssessmentService: OrionAssessmentService;
    let programmingSubmissionService: ProgrammingSubmissionService;
    let orionConnectorService: OrionConnectorService;
    let programmingAssessmentExportService: ProgrammingAssessmentRepoExportService;
    let alertService: AlertService;
    let stateStub: jest.SpyInstance;
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
                stateObservable = new BehaviorSubject(orionState);
                stateStub = jest.spyOn(orionConnectorService, 'state').mockReturnValue(stateObservable);
                orionAssessmentService = TestBed.inject(OrionAssessmentService);
                programmingSubmissionService = TestBed.inject(ProgrammingSubmissionService);
                programmingAssessmentExportService = TestBed.inject(ProgrammingAssessmentRepoExportService);
                alertService = TestBed.inject(AlertService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('downloadSubmissionInOrion with new should call send', () => {
        const sendSubmissionToOrion = jest.spyOn(orionAssessmentService, <any>'sendSubmissionToOrion');
        const getSubmission = jest.spyOn(programmingSubmissionService, 'getProgrammingSubmissionForExerciseForCorrectionRoundWithoutAssessment');

        getSubmission.mockReturnValue(of(programmingSubmission));

        orionAssessmentService.downloadSubmissionInOrion(16, 'new', 0, false);

        expect(getSubmission).toHaveBeenCalledTimes(1);
        expect(getSubmission).toHaveBeenCalledWith(16, true, 0);
        expect(sendSubmissionToOrion).toHaveBeenCalledTimes(1);
        expect(sendSubmissionToOrion).toHaveBeenCalledWith(16, programmingSubmission.id, 0, false);
    });

    it('downloadSubmissionInOrion with number should call send', () => {
        const sendSubmissionToOrion = jest.spyOn(orionAssessmentService, <any>'sendSubmissionToOrion').mockImplementation();

        orionAssessmentService.downloadSubmissionInOrion(16, programmingSubmission, 0, false);

        expect(sendSubmissionToOrion).toHaveBeenCalledTimes(1);
        expect(sendSubmissionToOrion).toHaveBeenCalledWith(16, programmingSubmission.id, 0, false);
    });

    it('sendSubmissionToOrion should convert and call connector', () => {
        const downloadSubmissionSpy = jest.spyOn(orionConnectorService, 'downloadSubmission');

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

        expect(downloadSubmissionSpy).toHaveBeenCalledTimes(1);
        expect(downloadSubmissionSpy).toHaveBeenCalledWith(11, 0, false, 'testBase64');
    });

    it('sendSubmissionToOrion should convert and report error', () => {
        const alertErrorStub = jest.spyOn(alertService, 'error');

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

        expect(alertErrorStub).toHaveBeenCalledTimes(1);
        expect(alertErrorStub).toHaveBeenCalledWith('artemisApp.assessmentDashboard.orion.downloadFailed');
    });

    /**
     * Helper to test the conversion with the fileReader
     * @param mockReader mock reader to test
     */
    function testConversion(mockReader: FileReader) {
        const isCloningSpy = jest.spyOn(orionConnectorService, 'isCloning');
        const exportSubmissionStub = jest.spyOn(programmingAssessmentExportService, 'exportReposByParticipations');
        const lockAndGetStub = jest.spyOn(programmingSubmissionService, 'lockAndGetProgrammingSubmissionParticipation');
        const readerStub = jest.spyOn(window, 'FileReader');

        // first it loads the submission
        lockAndGetStub.mockReturnValue(of(programmingSubmission));
        // then the exported file
        const response = new HttpResponse({ body: new Blob(['Stuff', 'in blob']), status: 200 });
        exportSubmissionStub.mockReturnValue(of(response));

        readerStub.mockReturnValue(mockReader);

        orionAssessmentService.downloadSubmissionInOrion(16, programmingSubmission, 0, false);

        expect(isCloningSpy).toHaveBeenCalledTimes(1);
        expect(isCloningSpy).toHaveBeenCalledWith(true);
        expect(lockAndGetStub).toHaveBeenCalledTimes(1);
        expect(lockAndGetStub).toHaveBeenCalledWith(11, 0);
        expect(exportSubmissionStub).toHaveBeenCalledTimes(1);
        // ignore RepositoryExportOptions
        expect(exportSubmissionStub).toHaveBeenCalledWith(16, [1], expect.anything());
        expect(readerStub).toHaveBeenCalledTimes(1);
    }

    it('should cancel lock correctly', fakeAsync(() => {
        const cancelStub = jest.spyOn(TestBed.inject(ProgrammingAssessmentManualResultService), 'cancelAssessment');
        cancelStub.mockReturnValue(new Observable());

        tick();

        expect(orionAssessmentService.orionState).toEqual(orionState);
        orionAssessmentService.activeSubmissionId = 24;

        stateObservable.next({ ...orionState, cloning: false });
        tick();

        expect(cancelStub).toHaveBeenCalledTimes(1);
        expect(cancelStub).toHaveBeenCalledWith(24);
    }));
});
