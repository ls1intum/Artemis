import { ComponentFixture, TestBed } from '@angular/core/testing';
import { JhiLanguageHelper } from 'app/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ArTEMiSTestModule } from '../../test.module';
import { MockActivatedRoute } from '../../mocks';
import { ArTEMiSResultModule } from 'app/entities/result';
import { ArTEMiSSharedModule } from 'app/shared';
import { ExerciseService, ExerciseType } from 'app/entities/exercise';
import { TutorExerciseDashboardComponent } from 'app/tutor-exercise-dashboard';
import { ModelingExercise } from 'app/entities/modeling-exercise';
import { TutorParticipationService } from 'app/tutor-exercise-dashboard/tutor-participation.service';
import { TextSubmissionService } from 'app/entities/text-submission';
import { ModelingSubmission, ModelingSubmissionService } from 'app/entities/modeling-submission';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { ComplaintService } from 'app/entities/complaint/complaint.service';
import { MockAlertService } from '../../helpers/mock-alert.service';
import { JhiAlertService } from 'ng-jhipster';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MockRouter } from '../../mocks/mock-router.service';
import { MockTutorParticipationService } from '../../mocks/mock-tutor-participation.service';
import { MockTextSubmissionService } from '../../mocks/mock-text-submission.service';
import { MockMarkdownService } from '../../mocks/mock-markdown.service';
import { MockComplaintService } from '../../mocks/mock-complaint.service';
import { ArTEMiSHeaderExercisePageWithDetailsModule } from 'app/exercise-headers';
import { ArTEMiSTutorCourseDashboardModule } from 'app/tutor-course-dashboard';
import { ArTEMiSHomeModule } from 'app/home';
import { ArTEMiSCourseModule } from 'app/entities/course/course.module';
import { ArTEMiSSidePanelModule } from 'app/components/side-panel/side-panel.module';
import { ArTEMiSModelingEditorModule } from 'app/modeling-editor';
import { AssessmentInstructionsModule } from 'app/assessment-instructions/assessment-instructions.module';
import { TutorParticipationStatus } from 'app/entities/tutor-participation';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { ArTEMiSMainModule } from 'app/layouts/main/main.module';

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
            imports: [
                ArTEMiSTestModule,
                ArTEMiSSharedModule,
                ArTEMiSHeaderExercisePageWithDetailsModule,
                ArTEMiSTutorCourseDashboardModule,
                ArTEMiSHomeModule,
                ArTEMiSCourseModule,
                ArTEMiSMainModule,
                ArTEMiSSidePanelModule,
                ArTEMiSModelingEditorModule,
                ArTEMiSResultModule,
                AssessmentInstructionsModule,
                RouterModule,
            ],
            declarations: [TutorExerciseDashboardComponent],
            providers: [
                JhiLanguageHelper,
                { provide: JhiAlertService, useClass: MockAlertService },
                { provide: ActivatedRoute, useClass: MockActivatedRoute },
                { provide: Router, useClass: MockRouter },
                { provide: TutorParticipationService, useClass: MockTutorParticipationService },
                { provide: TextSubmissionService, useClass: MockTextSubmissionService },
                { provide: ArtemisMarkdown, useClass: MockMarkdownService },
                { provide: ComplaintService, useClass: MockComplaintService },
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
