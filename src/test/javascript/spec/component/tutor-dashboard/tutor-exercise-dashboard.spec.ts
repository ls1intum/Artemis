import { ComponentFixture, TestBed } from '@angular/core/testing';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { SinonStub, stub } from 'sinon';
import { ArtemisTestModule } from '../../test.module';
import { MockActivatedRoute } from '../../mocks/mock-activated.route';
import { MockSyncStorage } from '../../mocks/mock-sync.storage';
import { MockComponent } from 'ng-mocks';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { MockAlertService } from '../../helpers/mock-alert.service';
import { AlertService } from 'app/core/alert/alert.service';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MockRouter } from '../../mocks/mock-router.service';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { SidePanelComponent } from 'app/components/side-panel/side-panel.component';
import { CollapsableAssessmentInstructionsComponent } from 'app/assessment-instructions/collapsable-assessment-instructions/collapsable-assessment-instructions.component';
import { AssessmentInstructionsComponent } from 'app/assessment-instructions/assessment-instructions/assessment-instructions.component';
import { TutorParticipationGraphComponent } from 'app/tutor-course-dashboard/tutor-participation-graph/tutor-participation-graph.component';
import { TutorLeaderboardComponent } from 'app/instructor-course-dashboard/tutor-leaderboard/tutor-leaderboard.component';
import { TranslateModule } from '@ngx-translate/core';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisProgrammingAssessmentModule } from 'app/programming-assessment/programming-assessment.module';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ArtemisAssessmentSharedModule } from 'app/assessment-shared/assessment-shared.module';
import { ModelingSubmission } from 'app/entities/modeling-submission/modeling-submission.model';
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/entities/programming-exercise/instructions/instructions-render/programming-exercise-instructions-render.module';
import { ModelingExercise } from 'app/entities/modeling-exercise/modeling-exercise.model';
import { HeaderExercisePageWithDetailsComponent } from 'app/exercise-headers/header-exercise-page-with-details.component';
import { TutorExerciseDashboardComponent } from 'app/tutor-exercise-dashboard/tutor-exercise-dashboard.component';
import { ExerciseType } from 'app/entities/exercise/exercise.model';
import { ModelingEditorComponent } from 'app/modeling-editor/modeling-editor.component';
import { ModelingSubmissionService } from 'app/entities/modeling-submission/modeling-submission.service';
import { TutorParticipationStatus } from 'app/entities/tutor-participation/tutor-participation.model';
import { ExerciseService } from 'app/entities/exercise/exercise.service';
import { ArtemisResultModule } from 'app/entities/result/result.module';

chai.use(sinonChai);
const expect = chai.expect;

describe('TutorExerciseDashboardComponent', () => {
    let comp: TutorExerciseDashboardComponent;
    let fixture: ComponentFixture<TutorExerciseDashboardComponent>;
    let modelingSubmissionService: ModelingSubmissionService;

    let modelingSubmissionStub: SinonStub;

    const exercise = { id: 20, type: ExerciseType.MODELING, tutorParticipations: [{ status: TutorParticipationStatus.TRAINED }] } as ModelingExercise;
    const submission = { id: 30 } as ModelingSubmission;
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
                TutorExerciseDashboardComponent,
                MockComponent(TutorLeaderboardComponent),
                MockComponent(TutorParticipationGraphComponent),
                MockComponent(HeaderExercisePageWithDetailsComponent),
                MockComponent(SidePanelComponent),
                MockComponent(ModelingEditorComponent),
                MockComponent(CollapsableAssessmentInstructionsComponent),
                MockComponent(AssessmentInstructionsComponent),
            ],
            providers: [
                JhiLanguageHelper,
                { provide: AlertService, useClass: MockAlertService },
                { provide: ActivatedRoute, useClass: MockActivatedRoute },
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
                            return of();
                        },
                    },
                },
            ],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorExerciseDashboardComponent);
                comp = fixture.componentInstance;
                modelingSubmissionService = TestBed.inject(ModelingSubmissionService);

                comp.exerciseId = exercise.id;

                modelingSubmissionStub = stub(modelingSubmissionService, 'getModelingSubmissionForExerciseWithoutAssessment');
            });
    });

    afterEach(() => {
        modelingSubmissionStub.restore();
    });

    it('should set unassessedSubmission if lock limit is not reached', () => {
        modelingSubmissionStub.returns(of(submission));

        comp.loadAll();

        expect(modelingSubmissionStub).to.have.been.calledOnceWithExactly(exercise.id);
        expect(comp.unassessedSubmission).to.equal(submission);
        expect(comp.submissionLockLimitReached).to.be.false;
    });

    it('should not set unassessedSubmission if lock limit is reached', () => {
        modelingSubmissionStub.returns(throwError(lockLimitErrorResponse));

        comp.loadAll();

        expect(modelingSubmissionStub).to.have.been.calledOnceWithExactly(exercise.id);
        expect(comp.unassessedSubmission).to.be.undefined;
        expect(comp.submissionLockLimitReached).to.be.true;
    });
});
