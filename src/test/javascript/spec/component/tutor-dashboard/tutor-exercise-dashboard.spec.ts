import { ComponentFixture, TestBed } from '@angular/core/testing';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { SinonStub, stub } from 'sinon';
import { ArtemisTestModule } from '../../test.module';
import { MockActivatedRoute, MockSyncStorage } from '../../mocks';
import { ArtemisResultModule } from 'app/entities/result';
import { MockComponent } from 'ng-mocks';
import { ArtemisSharedModule } from 'app/shared';
import { ExerciseService, ExerciseType } from 'app/entities/exercise';
import { TutorExerciseDashboardComponent } from 'app/tutor-exercise-dashboard';
import { ModelingExercise } from 'app/entities/modeling-exercise';
import { ModelingSubmission, ModelingSubmissionService } from 'app/entities/modeling-submission';
import { MockAlertService } from '../../helpers/mock-alert.service';
import { JhiAlertService } from 'ng-jhipster';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MockRouter } from '../../mocks/mock-router.service';
import { HeaderExercisePageWithDetailsComponent } from 'app/exercise-headers';
import { ModelingEditorComponent } from 'app/modeling-editor';
import { TutorParticipationStatus } from 'app/entities/tutor-participation';
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
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/entities/programming-exercise/instructions/instructions-render';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ArtemisAssessmentSharedModule } from 'app/assessment-shared/assessment-shared.module';

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
                { provide: JhiAlertService, useClass: MockAlertService },
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
                modelingSubmissionService = TestBed.get(ModelingSubmissionService);

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
