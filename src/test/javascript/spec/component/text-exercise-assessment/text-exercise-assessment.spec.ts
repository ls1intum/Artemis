import { ComponentFixture, TestBed } from '@angular/core/testing';
import { JhiLanguageHelper } from 'app/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { stub, SinonStub } from 'sinon';
import { ArTEMiSTestModule } from '../../test.module';
import { MockActivatedRoute } from '../../mocks';
import { UpdatingResultComponent} from 'app/entities/result';
import { MockComponent } from 'ng-mocks';
import { ArTEMiSSharedModule } from 'app/shared';
import { ExerciseService, ExerciseType } from 'app/entities/exercise';
import { MockAlertService } from '../../helpers/mock-alert.service';
import { JhiAlertService } from 'ng-jhipster';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MockRouter } from '../../mocks/mock-router.service';
import { TutorParticipationStatus } from 'app/entities/tutor-participation';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import {TextAssessmentComponent} from "app/text-assessment/text-assessment.component";
import {TextSubmission, TextSubmissionService} from "app/entities/text-submission";
import {TextExercise} from "app/entities/text-exercise";
import {TextAssessmentEditorComponent} from "app/text-assessment/text-assessment-editor/text-assessment-editor.component";
import {ResizableInstructionsComponent} from "app/text-assessment/resizable-instructions/resizable-instructions.component";
import {TextAssessmentDetailComponent} from "app/text-assessment/text-assessment-detail/text-assessment-detail.component";
import {ComplaintsForTutorComponent} from "app/complaints-for-tutor";

chai.use(sinonChai);
const expect = chai.expect;

describe('TextAssessmentComponent', () => {
    let comp: TextAssessmentComponent;
    let fixture: ComponentFixture<TextAssessmentComponent>;
    let textSubmissionService: TextSubmissionService;

    let textSubmissionStub: SinonStub;

    const exercise = { id: 20, type: ExerciseType.TEXT, tutorParticipations: [{ status: TutorParticipationStatus.TRAINED }] } as TextExercise;
    const submission = { id: 30 } as TextSubmission;
    const lockLimitErrorResponse = new HttpErrorResponse({ error: { errorKey: 'lockedSubmissionsLimitReached' } });

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [ArTEMiSTestModule, ArTEMiSSharedModule, RouterModule],
            declarations: [
                TextAssessmentComponent,
                MockComponent(UpdatingResultComponent),
                MockComponent(TextAssessmentEditorComponent),
                MockComponent(ResizableInstructionsComponent),
                MockComponent(TextAssessmentDetailComponent),
                MockComponent(ComplaintsForTutorComponent)
            ],
            providers: [
                JhiLanguageHelper,
                { provide: JhiAlertService, useClass: MockAlertService },
                { provide: ActivatedRoute, useClass: MockActivatedRoute },
                { provide: Router, useClass: MockRouter },
                { provide: Location },
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
                            return of();textSubmissionStub
                        },
                    },
                },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TextAssessmentComponent);
                comp = fixture.componentInstance;
                textSubmissionService = TestBed.get(TextSubmissionService);

                comp.exercise.id = exercise.id;

                textSubmissionStub = stub(textSubmissionService, 'getTextSubmissionForExerciseWithoutAssessment');
            });
    });

    afterEach(() => {
        textSubmissionStub.restore();
    });

    it('should set unassessedSubmission if lock limit is not reached', () => {
        textSubmissionStub.returns(of(submission));

        expect(textSubmissionStub).to.have.been.calledOnceWithExactly(exercise.id);
        expect(comp.unassessedSubmission).to.equal(submission);
    });

    it('should not set unassessedSubmission if lock limit is reached', () => {
        textSubmissionStub.returns(throwError(lockLimitErrorResponse));

        expect(textSubmissionStub).to.have.been.calledOnceWithExactly(exercise.id);
        expect(comp.unassessedSubmission).to.be.undefined;
    });
});
