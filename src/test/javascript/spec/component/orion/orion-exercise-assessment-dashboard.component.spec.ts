import { TestBed } from '@angular/core/testing';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { OrionExerciseAssessmentDashboardComponent } from 'app/orion/assessment/orion-exercise-assessment-dashboard.component';
import { spy, stub } from 'sinon';
import { ExerciseType } from 'app/entities/exercise.model';
import { TutorParticipationStatus } from 'app/entities/participation/tutor-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { OrionConnectorService } from 'app/orion/orion-connector.service';
import { of } from 'rxjs';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { HttpResponse } from '@angular/common/http';
import { MockAlertService } from '../../helpers/mocks/service/mock-alert.service';
import { JhiAlertService } from 'ng-jhipster';
import { ProgrammingAssessmentRepoExportService } from 'app/exercises/programming/assess/repo-export/programming-assessment-repo-export.service';
import { ArtemisTestModule } from '../../test.module';
import { ExerciseAssessmentDashboardComponent } from 'app/exercises/shared/dashboards/tutor/exercise-assessment-dashboard.component';
import { OrionModule } from 'app/orion/orion.module';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

chai.use(sinonChai);
const expect = chai.expect;

describe('OrionExerciseAssessmentDashboardComponent', () => {
    let comp: OrionExerciseAssessmentDashboardComponent;
    let programmingSubmissionService: ProgrammingSubmissionService;
    let orionConnectorService: OrionConnectorService;
    let programmingAssessmentExportService: ProgrammingAssessmentRepoExportService;

    const programmingExercise = {
        id: 16,
        type: ExerciseType.PROGRAMMING,
        tutorParticipations: [{ status: TutorParticipationStatus.TRAINED }],
        secondCorrectionEnabled: false,
    } as ProgrammingExercise;
    const programmingSubmission = { id: 11, participation: { id: 1 } } as any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                ArtemisTestModule,
                OrionModule,
            ],
            declarations: [
                OrionExerciseAssessmentDashboardComponent,
                MockComponent(ExerciseAssessmentDashboardComponent),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [
                MockProvider(ProgrammingSubmissionService),
                MockProvider(OrionConnectorService),
                MockProvider(ProgrammingAssessmentRepoExportService),
                { provide: JhiAlertService, useClass: MockAlertService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents().then(() => {
                comp = TestBed.createComponent(OrionExerciseAssessmentDashboardComponent).componentInstance;
                programmingSubmissionService = TestBed.inject(ProgrammingSubmissionService);
                orionConnectorService = TestBed.inject(OrionConnectorService);
                programmingAssessmentExportService = TestBed.inject(ProgrammingAssessmentRepoExportService);
            },
        );
    });

    it('openAssessmentInOrion should call connector', () => {
        const assessExerciseSpy = spy(orionConnectorService, 'assessExercise');

        comp.exercise = programmingExercise;
        comp.openAssessmentInOrion();

        expect(assessExerciseSpy).to.have.been.calledOnceWithExactly(programmingExercise);
    });
    it('downloadSubmissionInOrion with new should call send', () => {
        const sendSubmissionToOrion = spy(comp, <any>'sendSubmissionToOrion');
        const getSubmission = stub(programmingSubmissionService, 'getProgrammingSubmissionForExerciseForCorrectionRoundWithoutAssessment');

        comp.exerciseId = programmingExercise.id!;
        getSubmission.returns(of(programmingSubmission));

        comp.downloadSubmissionInOrion('new', 0);

        expect(getSubmission).to.have.been.calledOnceWithExactly(programmingExercise.id, true, 0);
        expect(sendSubmissionToOrion).to.have.been.calledOnceWithExactly(programmingExercise.id, programmingSubmission.id, 0);
    });
    it('downloadSubmissionInOrion with number should call send', () => {
        const sendSubmissionToOrion = stub(comp, <any>'sendSubmissionToOrion');

        comp.exerciseId = programmingExercise.id!;

        comp.downloadSubmissionInOrion(programmingSubmission, 0);

        expect(sendSubmissionToOrion).to.have.been.calledOnceWithExactly(programmingExercise.id, programmingSubmission.id, 0);
    });
    it('sendSubmissionToOrion should convert and call connector', () => {
        const downloadSubmissionSpy = spy(orionConnectorService, 'downloadSubmission');
        const isCloningSpy = spy(orionConnectorService, 'isCloning');
        const exportSubmissionStub = stub(programmingAssessmentExportService, 'exportReposByParticipations');
        const lockAndGetStub = stub(programmingSubmissionService, 'lockAndGetProgrammingSubmissionParticipation');

        // first it loads the submission
        lockAndGetStub.returns(of(programmingSubmission));
        // then the exported file
        const response = new HttpResponse({ body: new Blob(['Stuff', 'in blob']), status: 200 });
        exportSubmissionStub.returns(of(response));

        // mock FileReader
        const mockReader = {
            result: 'testBase64',
            // required, used to instantly trigger the callback
            // @ts-ignore
            readAsDataURL() {
                this.onloadend();
            },
        };
        const readerStub = stub(window, 'FileReader');
        readerStub.returns(mockReader);

        // @ts-ignore
        comp.sendSubmissionToOrion(25, 11, 0);

        expect(isCloningSpy).to.have.been.calledOnceWithExactly(true);
        expect(lockAndGetStub).to.have.been.calledOnceWith(11, 0);
        // ignore RepositoryExportOptions
        expect(exportSubmissionStub).to.have.been.calledOnceWith(25, [1]);
        expect(readerStub).to.have.been.calledOnce;
        expect(downloadSubmissionSpy).to.have.been.calledOnceWithExactly(11, 0, 'testBase64');
    });
});
