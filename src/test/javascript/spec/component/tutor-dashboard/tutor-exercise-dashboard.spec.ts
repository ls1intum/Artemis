import { ComponentFixture, TestBed } from '@angular/core/testing';
import { JhiLanguageHelper } from 'app/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ArTEMiSTestModule } from '../../test.module';
import { MockActivatedRoute } from '../../mocks';
import { ResultComponent } from 'app/entities/result';
import { MockComponent } from 'ng-mocks';
import { ArTEMiSSharedModule } from 'app/shared';
import { ExerciseService, ExerciseType } from 'app/entities/exercise';
import { TutorExerciseDashboardComponent } from 'app/tutor-exercise-dashboard';
import { ModelingExercise } from 'app/entities/modeling-exercise';
import { ModelingSubmission, ModelingSubmissionService } from 'app/entities/modeling-submission';
import { MockAlertService } from '../../helpers/mock-alert.service';
import { JhiAlertService } from 'ng-jhipster';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MockRouter } from '../../mocks/mock-router.service';
import { HeaderExercisePageWithDetailsComponent } from 'app/exercise-headers';
import { ArTEMiSTutorCourseDashboardModule } from 'app/tutor-course-dashboard';
import { ModelingEditorComponent } from 'app/modeling-editor';
import { TutorParticipationStatus } from 'app/entities/tutor-participation';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { SidePanelComponent } from 'app/components/side-panel/side-panel.component';
import { AssessmentInstructionsComponent } from 'app/assessment-instructions';

chai.use(sinonChai);
const expect = chai.expect;

describe('TutorExerciseDashboardComponent', () => {
    let comp: TutorExerciseDashboardComponent;
    let fixture: ComponentFixture<TutorExerciseDashboardComponent>;
    let modelingSubmissionService: ModelingSubmissionService;

    const exercise = { id: 20, type: ExerciseType.MODELING, tutorParticipations: [{ status: TutorParticipationStatus.TRAINED }] } as ModelingExercise;
    const submission = { id: 30 } as ModelingSubmission;
    const lockLimitErrorResponse = new HttpErrorResponse({ error: { errorKey: 'lockedSubmissionsLimitReached' } });

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [ArTEMiSTestModule, ArTEMiSSharedModule, ArTEMiSTutorCourseDashboardModule, RouterModule],
            declarations: [
                TutorExerciseDashboardComponent,
                MockComponent(HeaderExercisePageWithDetailsComponent),
                MockComponent(SidePanelComponent),
                MockComponent(ModelingEditorComponent),
                MockComponent(ResultComponent),
                MockComponent(AssessmentInstructionsComponent),
            ],
            providers: [
                JhiLanguageHelper,
                { provide: JhiAlertService, useClass: MockAlertService },
                { provide: ActivatedRoute, useClass: MockActivatedRoute },
                { provide: Router, useClass: MockRouter },
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
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorExerciseDashboardComponent);
                comp = fixture.componentInstance;
                modelingSubmissionService = TestBed.get(ModelingSubmissionService);

                comp.exerciseId = exercise.id;
            });
    });

    it('should set unassessedSubmission if lock limit is not reached', () => {
        spyOn(modelingSubmissionService, 'getModelingSubmissionForExerciseWithoutAssessment').and.returnValue(of(submission));

        comp.loadAll();

        expect(comp.unassessedSubmission).to.equal(submission);
        expect(comp.submissionLockLimitReached).to.be.false;
    });

    it('should not set unassessedSubmission if lock limit is reached', () => {
        spyOn(modelingSubmissionService, 'getModelingSubmissionForExerciseWithoutAssessment').and.returnValue(throwError(lockLimitErrorResponse));

        comp.loadAll();

        expect(comp.unassessedSubmission).to.be.undefined;
        expect(comp.submissionLockLimitReached).to.be.true;
    });
});
