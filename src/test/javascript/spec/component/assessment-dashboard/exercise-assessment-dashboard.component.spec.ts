import * as ace from 'brace';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { SinonStub, stub } from 'sinon';
import { ArtemisTestModule } from '../../test.module';
import { MockActivatedRouteWithSubjects } from '../../helpers/mocks/activated-route/mock-activated-route-with-subjects';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockComponent } from 'ng-mocks';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { SidePanelComponent } from 'app/shared/side-panel/side-panel.component';
import { CollapsableAssessmentInstructionsComponent } from 'app/assessment/assessment-instructions/collapsable-assessment-instructions/collapsable-assessment-instructions.component';
import { AssessmentInstructionsComponent } from 'app/assessment/assessment-instructions/assessment-instructions/assessment-instructions.component';
import { TutorParticipationGraphComponent } from 'app/shared/dashboards/tutor-participation-graph/tutor-participation-graph.component';
import { TutorLeaderboardComponent } from 'app/shared/dashboards/tutor-leaderboard/tutor-leaderboard.component';
import { TranslateModule } from '@ngx-translate/core';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisProgrammingAssessmentModule } from 'app/exercises/programming/assess/programming-assessment.module';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { GuidedTourMapping } from 'app/guided-tour/guided-tour-setting.model';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { DeviceDetectorService } from 'ngx-device-detector';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instructions-render.module';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { HeaderExercisePageWithDetailsComponent } from 'app/exercises/shared/exercise-headers/header-exercise-page-with-details.component';
import { ExerciseAssessmentDashboardComponent } from 'app/exercises/shared/dashboards/tutor/exercise-assessment-dashboard.component';
import { ExerciseType } from 'app/entities/exercise.model';
import { ModelingEditorComponent } from 'app/exercises/modeling/shared/modeling-editor.component';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { TutorParticipationStatus } from 'app/entities/participation/tutor-participation.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { HeaderParticipationPageComponent } from 'app/exercises/shared/exercise-headers/header-participation-page.component';
import { StructuredGradingInstructionsAssessmentLayoutComponent } from 'app/assessment/structured-grading-instructions-assessment-layout/structured-grading-instructions-assessment-layout.component';
import { StatsForDashboard } from 'app/course/dashboards/instructor-course-dashboard/stats-for-dashboard.model';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission.service';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Complaint } from 'app/entities/complaint.model';
import { Language } from 'app/entities/tutor-group.model';
import { SubmissionExerciseType } from 'app/entities/submission.model';
import { TutorParticipationService } from 'app/exercises/shared/dashboards/tutor/tutor-participation.service';
import { Participation } from 'app/entities/participation/participation.model';
import { Result } from 'app/entities/result.model';
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { SecondCorrectionEnableButtonComponent } from 'app/exercises/shared/dashboards/tutor/second-correction-button/second-correction-enable-button.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('ExerciseAssessmentDashboardComponent', () => {
    // needed to make sure ace is defined
    ace.acequire('ace/ext/modelist.js');
    let comp: ExerciseAssessmentDashboardComponent;
    let fixture: ComponentFixture<ExerciseAssessmentDashboardComponent>;

    let modelingSubmissionService: ModelingSubmissionService;
    let modelingSubmissionStubWithAssessment: SinonStub;
    let modelingSubmissionStubWithoutAssessment: SinonStub;

    let textSubmissionService: TextSubmissionService;
    let textSubmissionStubWithAssessment: SinonStub;
    let textSubmissionStubWithoutAssessment: SinonStub;

    let fileUploadSubmissionService: FileUploadSubmissionService;
    let fileUploadSubmissionStubWithAssessment: SinonStub;
    let fileUploadSubmissionStubWithoutAssessment: SinonStub;

    let programmingSubmissionService: ProgrammingSubmissionService;
    let programmingSubmissionStubWithAssessment: SinonStub;
    let programmingSubmissionStubWithoutAssessment: SinonStub;

    let exerciseService: ExerciseService;
    let exerciseServiceGetForTutorsStub: SinonStub;
    let exerciseServiceGetStatsForTutorsStub: SinonStub;

    let tutorParticipationService: TutorParticipationService;
    let tutorParticipationServiceCreateStub: SinonStub;

    let guidedTourService: GuidedTourService;
    const result1 = { id: 11 } as Result;
    const result2 = { id: 12 } as Result;
    const exam = { id: 13, numberOfCorrectionRoundsInExam: 2 } as Exam;
    const exerciseGroup = { id: 14, exam } as ExerciseGroup;

    const exercise = {
        id: 15,
        exerciseGroup,
        tutorParticipations: [{ status: TutorParticipationStatus.TRAINED }],
        secondCorrectionEnabled: false,
    } as ProgrammingExercise;
    const programmingExercise = {
        id: 16,
        exerciseGroup,
        type: ExerciseType.PROGRAMMING,
        tutorParticipations: [{ status: TutorParticipationStatus.TRAINED }],
        secondCorrectionEnabled: false,
    } as ProgrammingExercise;
    const modelingExercise = {
        id: 17,
        exerciseGroup,
        type: ExerciseType.MODELING,
        tutorParticipations: [{ status: TutorParticipationStatus.TRAINED }],
    } as ModelingExercise;
    const textExercise = {
        id: 18,
        exerciseGroup,
        type: ExerciseType.TEXT,
        tutorParticipations: [{ status: TutorParticipationStatus.TRAINED }],
        secondCorrectionEnabled: false,
    } as TextExercise;
    const fileUploadExercise = {
        id: 19,
        exerciseGroup,
        type: ExerciseType.FILE_UPLOAD,
        tutorParticipations: [{ status: TutorParticipationStatus.TRAINED }],
        secondCorrectionEnabled: false,
    } as FileUploadExercise;

    const participation = { id: 20, submissions: [] } as Participation;

    const modelingSubmission = { id: 21 } as ModelingSubmission;
    const fileUploadSubmission = { id: 22 } as FileUploadSubmission;
    const textSubmission = { id: 23 } as TextSubmission;
    const programmingSubmission = { id: 24 } as ProgrammingSubmission;

    const modelingSubmissionAssessed = { id: 25, results: [result1, result2], participation } as ModelingSubmission;
    const fileUploadSubmissionAssessed = { id: 26, results: [result1, result2], participation } as FileUploadSubmission;
    const textSubmissionAssessed = {
        id: 27,
        submissionExerciseType: SubmissionExerciseType.TEXT,
        language: Language.GERMAN,
        results: [result1, result2],
        participation,
    } as TextSubmission;
    const programmingSubmissionAssessed = { id: 28, results: [result1, result2], participation } as ProgrammingSubmission;

    const complaint = { id: 29, result: { id: 30, submission: textSubmission } } as Complaint;

    const numberOfAssessmentsOfCorrectionRounds = [
        { inTime: 1, late: 1 },
        { inTime: 8, late: 0 },
    ];
    const stats = {
        numberOfSubmissions: { inTime: 12, late: 5 },
        totalNumberOfAssessments: { inTime: 9, late: 1 },
        numberOfAssessmentsOfCorrectionRounds,
    } as StatsForDashboard;
    const lockLimitErrorResponse = new HttpErrorResponse({ error: { errorKey: 'lockedSubmissionsLimitReached' } });
    const router = new MockRouter();
    const navigateSpy = sinon.spy(router, 'navigate');

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [
                ArtemisTestModule,
                ArtemisSharedModule,
                ArtemisSharedComponentModule,
                ArtemisProgrammingAssessmentModule,
                ArtemisProgrammingExerciseInstructionsRenderModule,
                ArtemisResultModule,
                RouterModule,
                TranslateModule.forRoot(),
                ArtemisAssessmentSharedModule,
            ],
            declarations: [
                ExerciseAssessmentDashboardComponent,
                MockComponent(TutorLeaderboardComponent),
                MockComponent(TutorParticipationGraphComponent),
                MockComponent(HeaderExercisePageWithDetailsComponent),
                MockComponent(HeaderParticipationPageComponent),
                MockComponent(SidePanelComponent),
                MockComponent(ModelingEditorComponent),
                MockComponent(SecondCorrectionEnableButtonComponent),
                MockComponent(CollapsableAssessmentInstructionsComponent),
                MockComponent(AssessmentInstructionsComponent),
                MockComponent(StructuredGradingInstructionsAssessmentLayoutComponent),
            ],
            providers: [
                JhiLanguageHelper,
                DeviceDetectorService,
                { provide: ActivatedRoute, useClass: MockActivatedRouteWithSubjects },
                { provide: Router, useValue: router },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseAssessmentDashboardComponent);
                comp = fixture.componentInstance;

                modelingSubmissionService = TestBed.inject(ModelingSubmissionService);
                textSubmissionService = TestBed.inject(TextSubmissionService);
                fileUploadSubmissionService = TestBed.inject(FileUploadSubmissionService);
                exerciseService = TestBed.inject(ExerciseService);
                programmingSubmissionService = TestBed.inject(ProgrammingSubmissionService);

                tutorParticipationService = TestBed.inject(TutorParticipationService);

                exerciseServiceGetForTutorsStub = stub(exerciseService, 'getForTutors');
                exerciseServiceGetStatsForTutorsStub = stub(exerciseService, 'getStatsForTutors');

                exerciseServiceGetForTutorsStub.returns(of(new HttpResponse({ body: modelingExercise, headers: new HttpHeaders() })));
                exerciseServiceGetStatsForTutorsStub.returns(of(new HttpResponse({ body: stats, headers: new HttpHeaders() })));

                guidedTourService = TestBed.inject(GuidedTourService);

                comp.exerciseId = modelingExercise.id!;

                modelingSubmissionStubWithoutAssessment = stub(modelingSubmissionService, 'getModelingSubmissionForExerciseForCorrectionRoundWithoutAssessment');
                modelingSubmissionStubWithAssessment = stub(modelingSubmissionService, 'getModelingSubmissionsForExerciseByCorrectionRound');

                textSubmissionStubWithoutAssessment = stub(textSubmissionService, 'getTextSubmissionForExerciseForCorrectionRoundWithoutAssessment');
                textSubmissionStubWithAssessment = stub(textSubmissionService, 'getTextSubmissionsForExerciseByCorrectionRound');

                fileUploadSubmissionStubWithAssessment = stub(fileUploadSubmissionService, 'getFileUploadSubmissionsForExerciseByCorrectionRound');
                fileUploadSubmissionStubWithoutAssessment = stub(fileUploadSubmissionService, 'getFileUploadSubmissionForExerciseForCorrectionRoundWithoutAssessment');

                programmingSubmissionStubWithoutAssessment = stub(programmingSubmissionService, 'getProgrammingSubmissionForExerciseForCorrectionRoundWithoutAssessment');
                programmingSubmissionStubWithAssessment = stub(programmingSubmissionService, 'getProgrammingSubmissionsForExerciseByCorrectionRound');

                textSubmissionStubWithoutAssessment.returns(of(textSubmission));
                textSubmissionStubWithAssessment.returns(of(textSubmissionAssessed));

                fileUploadSubmissionStubWithAssessment.returns(of(fileUploadSubmissionAssessed));
                fileUploadSubmissionStubWithoutAssessment.returns(of(fileUploadSubmission));

                programmingSubmissionStubWithAssessment.returns(of(programmingSubmissionAssessed));
                programmingSubmissionStubWithoutAssessment.returns(of(programmingSubmission));

                modelingSubmissionStubWithAssessment.returns(of(new HttpResponse({ body: [modelingSubmissionAssessed], headers: new HttpHeaders() })));
                modelingSubmissionStubWithoutAssessment.returns(of(modelingSubmission));
            });
    });

    afterEach(() => {
        modelingSubmissionStubWithoutAssessment.restore();
        modelingSubmissionStubWithAssessment.restore();

        textSubmissionStubWithoutAssessment.restore();
        textSubmissionStubWithAssessment.restore();

        fileUploadSubmissionStubWithAssessment.restore();
        fileUploadSubmissionStubWithoutAssessment.restore();

        programmingSubmissionStubWithAssessment.restore();
        programmingSubmissionStubWithoutAssessment.restore();

        exerciseServiceGetForTutorsStub.restore();
        exerciseServiceGetStatsForTutorsStub.restore();
    });

    it('should set unassessedSubmission if lock limit is not reached', () => {
        const guidedTourMapping = {} as GuidedTourMapping;
        spyOn<any>(guidedTourService, 'checkTourState').and.returnValue(true);
        guidedTourService.guidedTourMapping = guidedTourMapping;
        modelingSubmissionStubWithAssessment.returns(of(new HttpResponse({ body: [], headers: new HttpHeaders() })));

        comp.loadAll();

        expect(modelingSubmissionStubWithoutAssessment).to.have.been.calledTwice;
        sinon.assert.calledWith(modelingSubmissionStubWithoutAssessment.getCall(0), modelingExercise.id, undefined, 0);
        sinon.assert.calledWith(modelingSubmissionStubWithoutAssessment.getCall(1), modelingExercise.id, undefined, 1);

        expect(comp.unassessedSubmissionByCorrectionRound?.get(0)).to.equal(modelingSubmission);
        expect(comp.unassessedSubmissionByCorrectionRound?.get(0)?.latestResult).to.equal(undefined);
        expect(comp.submissionLockLimitReached).to.be.false;
        expect(comp.submissionsByCorrectionRound?.get(0)!.length).to.equal(0);
    });

    it('should not set unassessedSubmission if lock limit is reached', () => {
        modelingSubmissionStubWithoutAssessment.returns(throwError(lockLimitErrorResponse));
        modelingSubmissionStubWithAssessment.returns(of(new HttpResponse({ body: [], headers: new HttpHeaders() })));

        comp.loadAll();

        expect(modelingSubmissionStubWithoutAssessment).to.have.been.calledTwice;
        sinon.assert.calledWith(modelingSubmissionStubWithoutAssessment.getCall(0), modelingExercise.id, undefined, 0);
        sinon.assert.calledWith(modelingSubmissionStubWithoutAssessment.getCall(1), modelingExercise.id, undefined, 1);
        expect(comp.unassessedSubmissionByCorrectionRound?.get(1)).to.be.undefined;
        expect(comp.submissionLockLimitReached).to.be.true;
        expect(comp.submissionsByCorrectionRound?.get(1)!.length).to.equal(0);
    });

    it('should have correct percentages calculated', () => {
        modelingSubmissionStubWithAssessment.returns(of(new HttpResponse({ body: [], headers: new HttpHeaders() })));

        comp.loadAll();

        sinon.assert.calledWith(modelingSubmissionStubWithoutAssessment.getCall(0), modelingExercise.id, undefined, 0);
        sinon.assert.calledWith(modelingSubmissionStubWithoutAssessment.getCall(1), modelingExercise.id, undefined, 1);
        expect(comp.numberOfAssessmentsOfCorrectionRounds[0].inTime).to.equal(1);
        expect(comp.numberOfAssessmentsOfCorrectionRounds[1].inTime).to.equal(8);
        expect(comp.totalAssessmentPercentage.inTime).to.equal(75);
        expect(comp.totalAssessmentPercentage.late).to.equal(20);
        expect(comp.submissionsByCorrectionRound?.get(1)!.length).to.equal(0);
    });

    it('should  set assessed Submission and latest result', () => {
        comp.loadAll();

        expect(modelingSubmissionStubWithoutAssessment).to.have.been.called;
        expect(comp.submissionsByCorrectionRound?.get(1)![0]).to.equal(modelingSubmissionAssessed);
        expect(comp.submissionsByCorrectionRound?.get(1)![0]?.participation!.submissions![0]).to.equal(comp.submissionsByCorrectionRound?.get(1)![0]);
        expect(comp.submissionsByCorrectionRound?.get(1)![0]?.latestResult).to.equal(result2);
    });

    it('should set exam and stats properties', () => {
        expect(comp.exam).to.be.undefined;

        comp.loadAll();

        expect(comp.exercise).to.not.be.undefined;
        expect(comp.exam).to.equal(exam);
        expect(comp.exam?.numberOfCorrectionRoundsInExam).to.equal(numberOfAssessmentsOfCorrectionRounds.length);
        expect(comp.numberOfAssessmentsOfCorrectionRounds).to.equal(numberOfAssessmentsOfCorrectionRounds);
    });

    it('should calculateStatus DRAFT', () => {
        expect(modelingSubmission.latestResult).to.be.undefined;
        expect(comp.calculateSubmissionStatus(modelingSubmission)).to.be.equal('DRAFT');
    });

    describe('test languages', () => {
        it('should call languge unknown', () => {
            expect(comp.language(modelingSubmission)).to.be.equal('UNKNOWN');
        });

        it('should call languge correct', () => {
            expect(comp.language(textSubmission)).to.be.equal('UNKNOWN');
            expect(comp.language(textSubmissionAssessed)).to.be.equal(Language.GERMAN);
        });
    });

    it('should call hasBeenCompletedByTutor', () => {
        comp.exampleSubmissionsCompletedByTutor = [{ id: 1 }, { id: 2 }];
        expect(comp.hasBeenCompletedByTutor(1)).to.equal(true);
    });

    it('should call readInstruction', () => {
        tutorParticipationServiceCreateStub = stub(tutorParticipationService, 'create');
        const tutorParticipation = { id: 1, status: TutorParticipationStatus.REVIEWED_INSTRUCTIONS };
        tutorParticipationServiceCreateStub.returns(of(new HttpResponse({ body: tutorParticipation, headers: new HttpHeaders() })));

        expect(comp.tutorParticipation).to.equal(undefined);
        comp.readInstruction();

        expect(comp.tutorParticipation).to.equal(tutorParticipation);
        expect(comp.tutorParticipationStatus).to.equal(TutorParticipationStatus.REVIEWED_INSTRUCTIONS);
    });

    describe('test calls for all exercise types', () => {
        it('fileuploadSubmission', () => {
            modelingSubmissionStubWithoutAssessment.returns(throwError(lockLimitErrorResponse));
            modelingSubmissionStubWithAssessment.returns(of(new HttpResponse({ body: [], headers: new HttpHeaders() })));

            exerciseServiceGetForTutorsStub.returns(of(new HttpResponse({ body: fileUploadExercise, headers: new HttpHeaders() })));

            comp.loadAll();

            expect(fileUploadSubmissionStubWithAssessment).to.have.been.called;
            expect(fileUploadSubmissionStubWithoutAssessment).to.have.been.called;
        });

        it('textSubmission', () => {
            modelingSubmissionStubWithoutAssessment.returns(throwError(lockLimitErrorResponse));

            exerciseServiceGetForTutorsStub.returns(of(new HttpResponse({ body: textExercise, headers: new HttpHeaders() })));

            comp.loadAll();

            expect(textSubmissionStubWithoutAssessment).to.have.been.called;
            expect(textSubmissionStubWithAssessment).to.have.been.called;
        });

        it('programmingSubmission', () => {
            modelingSubmissionStubWithoutAssessment.returns(throwError(lockLimitErrorResponse));

            exerciseServiceGetForTutorsStub.returns(of(new HttpResponse({ body: programmingExercise, headers: new HttpHeaders() })));

            comp.loadAll();

            expect(programmingSubmissionStubWithAssessment).to.have.been.called;
            expect(programmingSubmissionStubWithoutAssessment).to.have.been.called;
        });
    });

    it('should viewComplaint', () => {
        const openAssessmentEditor = stub(comp, 'openAssessmentEditor');
        comp.viewComplaint(complaint);
        expect(openAssessmentEditor).to.have.been.called;
    });

    it('should return asProgrammingExercise', () => {
        const castedExercise = comp.asProgrammingExercise(exercise);
        expect(castedExercise).to.be.equal(exercise as ProgrammingExercise);
    });

    describe('openExampleSubmission', () => {
        const courseId = 4;

        it('should not openExampleSubmission', () => {
            navigateSpy.resetHistory();
            const submission = { id: 8 };
            comp.openExampleSubmission(submission!.id);
            expect(navigateSpy).to.have.not.been.called;
        });

        it('should openExampleSubmission', () => {
            comp.exercise = exercise;
            comp.exercise.type = ExerciseType.PROGRAMMING;
            comp.courseId = 4;
            comp.exercise = exercise;
            const submission = { id: 8 };
            comp.openExampleSubmission(submission!.id, true, true);
            expect(navigateSpy).to.have.been.calledWith([`/course-management/${courseId}/${exercise.type}-exercises/${exercise.id}/example-submissions/${submission.id}`]);
        });
    });

    describe('openAssessmentEditor', () => {
        const courseId = 4;
        it('should not openExampleSubmission', () => {
            navigateSpy.resetHistory();
            const submission = { id: 8 };
            comp.openAssessmentEditor(submission);
            expect(navigateSpy).to.have.not.been.called;
        });

        it('should openExampleSubmission with modelingExercise', () => {
            comp.exercise = exercise;
            comp.exercise.type = ExerciseType.MODELING;
            comp.courseId = 4;
            comp.exercise = exercise;
            comp.exerciseId = exercise.id!;
            const submission = { id: 8 };
            comp.openAssessmentEditor(submission);

            const expectedUrl = [
                '/course-management',
                comp.courseId.toString(),
                'modeling-exercises',
                exercise.id!.toString(),
                'submissions',
                submission.id.toString(),
                'assessment',
            ];
            expect(navigateSpy).to.have.been.calledWith(expectedUrl, { queryParams: { 'correction-round': 0 } });
        });

        it('should openExampleSubmission with programmingExercise', () => {
            comp.exercise = exercise;
            comp.exercise.type = ExerciseType.PROGRAMMING;
            comp.courseId = 4;
            comp.exerciseId = exercise.id!;
            const participationId = 3;
            const submission = { id: 8, participation: { id: participationId } };

            const expectedUrl = [
                '/course-management',
                comp.courseId.toString(),
                'programming-exercises',
                exercise.id!.toString(),
                'code-editor',
                participationId.toString(),
                'assessment',
            ];
            comp.openAssessmentEditor(submission);
            expect(navigateSpy).to.have.been.calledWith(expectedUrl);
            comp.isTestRun = true;
            comp.openAssessmentEditor(submission);
            expect(navigateSpy).to.have.been.calledWith(expectedUrl);
        });
    });
});
