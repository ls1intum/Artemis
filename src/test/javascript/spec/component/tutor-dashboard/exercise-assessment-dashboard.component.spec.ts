import * as ace from 'brace';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { SinonStub, stub } from 'sinon';
import { ArtemisTestModule } from '../../test.module';
import { MockActivatedRouteWithSubjects } from '../../helpers/mocks/activated-route/mock-activated-route-with-subjects';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockComponent } from 'ng-mocks';
import { ArtemisSharedModule } from 'app/shared/shared.module';

import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
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

chai.use(sinonChai);
const expect = chai.expect;

describe('ExerciseAssessmentDashboardComponent', () => {
    // needed to make sure ace is defined
    ace.acequire('ace/ext/modelist.js');
    let comp: ExerciseAssessmentDashboardComponent;
    let fixture: ComponentFixture<ExerciseAssessmentDashboardComponent>;
    let modelingSubmissionService: ModelingSubmissionService;
    let modelingSubmissionStub: SinonStub;
    let guidedTourService: GuidedTourService;

    const exercise = { id: 20, type: ExerciseType.MODELING, tutorParticipations: [{ status: TutorParticipationStatus.TRAINED }] } as ModelingExercise;
    const submission = { id: 30 } as ModelingSubmission;
    const stats = {
        numberOfSubmissions: { inTime: 12, late: 5 },
        totalNumberOfAssessments: { inTime: 9, late: 1 },
    } as StatsForDashboard;
    const lockLimitErrorResponse = new HttpErrorResponse({ error: { errorKey: 'lockedSubmissionsLimitReached' } });

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
                MockComponent(CollapsableAssessmentInstructionsComponent),
                MockComponent(AssessmentInstructionsComponent),
                MockComponent(StructuredGradingInstructionsAssessmentLayoutComponent),
            ],
            providers: [
                JhiLanguageHelper,
                DeviceDetectorService,
                { provide: ActivatedRoute, useClass: MockActivatedRouteWithSubjects },
                { provide: Router, useClass: MockRouter },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                {
                    provide: ExerciseService,
                    useValue: {
                        getForTutors() {
                            return {
                                subscribe: (fn: (value: any) => void) =>
                                    fn({
                                        body: exercise,
                                    }),
                            };
                        },
                        getStatsForTutors() {
                            return {
                                subscribe: (fn: (value: any) => void) =>
                                    fn({
                                        body: stats,
                                    }),
                            };
                        },
                    },
                },
            ],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseAssessmentDashboardComponent);
                comp = fixture.componentInstance;

                modelingSubmissionService = TestBed.inject(ModelingSubmissionService);
                guidedTourService = TestBed.inject(GuidedTourService);

                comp.exerciseId = exercise.id!;

                modelingSubmissionStub = stub(modelingSubmissionService, 'getModelingSubmissionForExerciseWithoutAssessment');
            });
    });

    afterEach(() => {
        modelingSubmissionStub.restore();
    });

    it('should set unassessedSubmission if lock limit is not reached', () => {
        const guidedTourMapping = {} as GuidedTourMapping;
        spyOn<any>(guidedTourService, 'checkTourState').and.returnValue(true);
        guidedTourService.guidedTourMapping = guidedTourMapping;
        modelingSubmissionStub.returns(of(submission));

        comp.loadAll();

        expect(modelingSubmissionStub).to.have.been.calledOnceWithExactly(exercise.id);
        expect(comp.unassessedSubmissionByCorrectionRound?.get(0)).to.equal(submission);
        expect(comp.submissionLockLimitReached).to.be.false;
    });

    it('should not set unassessedSubmission if lock limit is reached', () => {
        modelingSubmissionStub.returns(throwError(lockLimitErrorResponse));

        comp.loadAll();

        expect(modelingSubmissionStub).to.have.been.calledOnceWithExactly(exercise.id);
        expect(comp.unassessedSubmissionByCorrectionRound).to.be.undefined;
        expect(comp.submissionLockLimitReached).to.be.true;
    });

    it('should have correct percentages calculated', () => {
        modelingSubmissionStub.returns(of(submission));

        comp.loadAll();

        expect(modelingSubmissionStub).to.have.been.calledOnceWithExactly(exercise.id);
        expect(comp.totalAssessmentPercentage.inTime).to.equal(75);
        expect(comp.totalAssessmentPercentage.late).to.equal(20);
    });
});
