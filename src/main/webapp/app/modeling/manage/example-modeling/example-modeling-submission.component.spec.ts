import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { ActivatedRoute, ActivatedRouteSnapshot, Router, convertToParamMap } from '@angular/router';
import { ChangeDetectorRef, Component, input } from '@angular/core';
import { MockComponent, MockProvider } from 'ng-mocks';
import { ModelingEditorComponent } from 'app/modeling/shared/modeling-editor/modeling-editor.component';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Feedback, FeedbackCorrectionError, FeedbackCorrectionErrorType, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { UMLDiagramType, UMLModel } from '@tumaet/apollon';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { AlertService } from 'app/foundation/service/alert.service';
import { ExampleModelingSubmissionComponent } from 'app/modeling/manage/example-modeling/example-modeling-submission.component';
import { ExampleSubmissionService } from 'app/assessment/shared/services/example-submission.service';
import { ExampleSubmission, ExampleSubmissionMode } from 'app/assessment/shared/entities/example-submission.model';
import { ModelingAssessmentService } from 'app/modeling/manage/assess/modeling-assessment.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateValuesDirective } from 'test/helpers/mocks/directive/mock-translate-values.directive';
import { FaLayersComponent } from '@fortawesome/angular-fontawesome';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { ModelingAssessmentComponent } from 'app/modeling/manage/assess/modeling-assessment.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { UnreferencedFeedbackComponent } from 'app/exercise/unreferenced-feedback/unreferenced-feedback.component';
import { ScoreDisplayComponent } from 'app/exercise/score-display/score-display.component';
import { FormsModule } from '@angular/forms';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { TutorParticipationService } from 'app/assessment/shared/assessment-dashboard/exercise-dashboard/tutor-participation.service';
import { TutorParticipationDTO, TutorParticipationStatus } from 'app/exercise/shared/entities/participation/tutor-participation.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DialogService } from 'primeng/dynamicdialog';

@Component({
    selector: 'jhi-modeling-editor',
    template: '',
})
class StubModelingEditorComponent {
    umlModel = input<UMLModel>();
    diagramType = input<UMLDiagramType>();
    readOnly = input<boolean>(false);
    scrollLock = input<boolean>(false);
    explanation = input<string>();
    problemStatement = input<string>();
    withExplanation = input<boolean>(false);
    tile = input<boolean>(false);

    getCurrentModel(): UMLModel {
        return {
            version: '4.0.0',
            id: 'test-id',
            title: 'Test Model',
            type: 'ClassDiagram',
            nodes: [],
            edges: [],
            assessments: {},
        } as any as UMLModel;
    }
}

@Component({
    selector: 'jhi-modeling-assessment',
    template: ` <ng-content /> `,
})
class StubModelingAssessmentComponent {
    resultFeedbacks = input<Feedback[]>([]);
    umlModel = input<UMLModel>();
    diagramType = input<UMLDiagramType>();
    readOnly = input<boolean>(false);
    highlightedElements = input<Map<string, string>>();
    explanation = input<string>();
    scrollLock = input(false);
}

describe('Example Modeling Submission Component', () => {
    let comp: ExampleModelingSubmissionComponent;
    let fixture: ComponentFixture<ExampleModelingSubmissionComponent>;
    let service: ExampleSubmissionService;
    let alertService: AlertService;
    let router: Router;
    let route: ActivatedRoute;

    const participation = new StudentParticipation();
    participation.exercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
    participation.id = 1;
    const submission = { id: 20, submitted: true, participation } as ModelingSubmission;
    const EXERCISE_ID = 22;

    const exampleSubmission: ExampleSubmission = {
        submission,
    };

    const exercise = {
        id: EXERCISE_ID,
        diagramType: UMLDiagramType.ClassDiagram,
        course: { id: 2 },
        maxPoints: 30,
        problemStatement: 'Model the specified domain.',
    } as ModelingExercise;

    const mockFeedbackWithReference: Feedback = {
        text: 'FeedbackWithReference',
        referenceId: 'relationshipId',
        reference: 'reference',
        credits: 30,
        correctionStatus: 'CORRECT',
    };
    const mockFeedbackWithoutReference: Feedback = {
        text: 'FeedbackWithoutReference',
        credits: 30,
        type: FeedbackType.MANUAL_UNREFERENCED,
    };
    const mockFeedbackInvalid: Feedback = {
        text: 'FeedbackInvalid',
        referenceId: '4',
        reference: 'reference',
        correctionStatus: FeedbackCorrectionErrorType.INCORRECT_SCORE,
    };
    const mockFeedbackCorrectionError: FeedbackCorrectionError = {
        reference: 'reference',
        type: FeedbackCorrectionErrorType.INCORRECT_SCORE,
    };

    const routeQueryParam = { readOnly: 0, toComplete: 0 };

    beforeEach(() => {
        routeQueryParam.readOnly = 0;
        routeQueryParam.toComplete = 0;
        route = {
            snapshot: {
                paramMap: convertToParamMap({ exerciseId: '22', exampleSubmissionId: '35' }),
                queryParamMap: convertToParamMap(routeQueryParam),
            },
        } as ActivatedRoute;

        TestBed.configureTestingModule({
            imports: [
                FormsModule,
                FaIconComponent,
                ExampleModelingSubmissionComponent,
                MockTranslateValuesDirective,
                MockComponent(FaLayersComponent),
                MockComponent(UnreferencedFeedbackComponent),
                MockComponent(ScoreDisplayComponent),
            ],
            providers: [
                MockProvider(ChangeDetectorRef),
                MockProvider(ArtemisTranslatePipe),
                MockProvider(DialogService),
                { provide: Router, useClass: MockRouter },
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideComponent(ExampleModelingSubmissionComponent, {
                remove: {
                    imports: [ModelingEditorComponent, ModelingAssessmentComponent],
                },
                add: {
                    imports: [StubModelingEditorComponent, StubModelingAssessmentComponent],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ExampleModelingSubmissionComponent);
        comp = fixture.componentInstance;
        service = TestBed.inject(ExampleSubmissionService);
        alertService = TestBed.inject(AlertService);
        router = TestBed.inject(Router);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('places the assessment rationale in the support pane instead of overlaying the model or submission explanation', () => {
        comp.exercise.set(exercise);
        comp.exampleSubmission.set(exampleSubmission);
        comp.assessmentMode.set(true);
        comp.selectedMode.set(ExampleSubmissionMode.READ_AND_CONFIRM);
        comp.readOnly.set(false);
        comp.toComplete.set(false);
        comp.assessmentExplanation.set('Explain the assessment reasoning');

        fixture.detectChanges();

        const assessment = fixture.nativeElement.querySelector('jhi-modeling-assessment') as HTMLElement;
        const details = fixture.nativeElement.querySelector('[assessmentworkspacedetails]') as HTMLElement;
        const rationale = details.querySelector('.example-assessment-rationale');
        expect(rationale).not.toBeNull();
        expect(assessment.querySelector('.example-assessment-rationale')).toBeNull();
        expect(rationale?.querySelector('textarea')).not.toBeNull();
    });

    it('places edit mode beside the same instructions and provides the problem statement in fullscreen', () => {
        comp.exercise.set(exercise);
        comp.exampleSubmission.set(exampleSubmission);
        comp.assessmentMode.set(false);

        fixture.detectChanges();

        const workspace = fixture.nativeElement.querySelector('jhi-assessment-workspace') as HTMLElement;
        const editor = fixture.debugElement.query((debugElement) => debugElement.componentInstance instanceof StubModelingEditorComponent)
            .componentInstance as StubModelingEditorComponent;
        expect(workspace.querySelector('[assessmentworkspacecanvas]')).not.toBeNull();
        expect(workspace.querySelector('[assessmentworkspaceinstructions] jhi-assessment-instructions')).not.toBeNull();
        expect(editor.problemStatement()).toBe(exercise.problemStatement);
    });

    it('should handle a new submission', () => {
        route.snapshot = {
            ...route.snapshot,
            paramMap: convertToParamMap({ exerciseId: '22', exampleSubmissionId: 'new' }),
        } as ActivatedRouteSnapshot;

        fixture.detectChanges();

        expect(comp.isNewSubmission()).toBe(true);
        expect(comp.exampleSubmission()).toEqual(new ExampleSubmission());
    });

    it('should upsert a new modeling submission', () => {
        const alertSpy = vi.spyOn(alertService, 'success');
        const serviceSpy = vi.spyOn(service, 'create').mockImplementation((newExampleSubmission) => of(new HttpResponse({ body: newExampleSubmission })));
        comp.isNewSubmission.set(true);
        comp.exercise.set(exercise);
        fixture.detectChanges();
        comp.upsertExampleModelingSubmission();

        expect(comp.isNewSubmission()).toBe(false);
        expect(serviceSpy).toHaveBeenCalledOnce();

        expect(alertSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.modelingEditor.saveSuccessful');
    });

    it('should upsert an existing modeling submission', async () => {
        vi.spyOn(service, 'get').mockReturnValue(of(new HttpResponse({ body: exampleSubmission })));
        const alertSpy = vi.spyOn(alertService, 'success');
        const serviceSpy = vi.spyOn(service, 'update').mockImplementation((updatedExampleSubmission) => of(new HttpResponse({ body: updatedExampleSubmission })));

        const modelingAssessmentService = TestBed.inject(ModelingAssessmentService);
        const modelingAssessmentServiceSpy = vi.spyOn(modelingAssessmentService, 'saveExampleAssessment');

        comp.isNewSubmission.set(false);
        comp.exercise.set(exercise);
        comp.exampleSubmission.set(exampleSubmission);

        fixture.detectChanges();
        comp.upsertExampleModelingSubmission();

        await fixture.whenStable();

        expect(comp.isNewSubmission()).toBe(false);
        expect(serviceSpy).toHaveBeenCalledTimes(2);
        expect(modelingAssessmentServiceSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.modelingEditor.saveSuccessful');
    });

    it('should check assessment', () => {
        const tutorParticipationService = TestBed.inject(TutorParticipationService);
        const assessExampleSubmissionSpy = vi.spyOn(tutorParticipationService, 'assessExampleSubmission');
        const exerciseId = 5;
        comp.exampleSubmission.set(exampleSubmission);
        comp.exerciseId = exerciseId;

        comp.checkAssessment();

        expect(comp.assessmentsAreValid()).toBe(true);
        expect(assessExampleSubmissionSpy).toHaveBeenCalledOnce();
        const [sentExampleSubmission, sentExerciseId] = assessExampleSubmissionSpy.mock.calls[0];
        expect(sentExerciseId).toBe(exerciseId);
        expect(sentExampleSubmission.submission!.id).toBe(exampleSubmission.submission!.id);
        expect(sentExampleSubmission.submission!.latestResult).toBeDefined();
        expect(exampleSubmission.submission!.latestResult).toBeUndefined();
    });

    it('should check invalid assessment', () => {
        const alertSpy = vi.spyOn(alertService, 'error');
        comp.exampleSubmission.set(exampleSubmission);

        comp.onReferencedFeedbackChanged([mockFeedbackInvalid]);
        comp.checkAssessment();

        expect(alertSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.modelingAssessment.invalidAssessments');
    });

    it('should read and understood', () => {
        const tutorParticipationService = TestBed.inject(TutorParticipationService);
        const dto: TutorParticipationDTO = {
            id: 1,
            exerciseId: EXERCISE_ID,
            tutorId: 3,
            status: TutorParticipationStatus.REVIEWED_INSTRUCTIONS,
        };
        vi.spyOn(tutorParticipationService, 'assessExampleSubmission').mockReturnValue(of(new HttpResponse({ body: dto })));
        const alertSpy = vi.spyOn(alertService, 'success');
        const routerSpy = vi.spyOn(router, 'navigate');
        comp.exercise.set(exercise);
        comp.exampleSubmission.set(exampleSubmission);

        fixture.detectChanges();
        comp.readAndUnderstood();

        expect(alertSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.exampleSubmission.readSuccessfully');
        expect(routerSpy).toHaveBeenCalledOnce();
    });

    it('should handle referenced feedback change', () => {
        const feedbacks = [mockFeedbackWithReference];
        comp.exercise.set(exercise);

        comp.onReferencedFeedbackChanged(feedbacks);

        expect(comp.feedbackChanged).toBe(true);
        expect(comp.assessmentsAreValid()).toBe(true);
        expect(comp.referencedFeedback()).toEqual(feedbacks);
    });

    it('should handle unreferenced feedback change', () => {
        const feedbacks = [mockFeedbackWithoutReference];
        comp.exercise.set(exercise);

        comp.onUnReferencedFeedbackChanged(feedbacks);

        expect(comp.feedbackChanged).toBe(true);
        expect(comp.assessmentsAreValid()).toBe(true);
        expect(comp.unreferencedFeedback()).toEqual(feedbacks);
    });

    it('should show submission', () => {
        const feedbacks = [mockFeedbackWithReference];
        comp.exercise.set(exercise);
        comp.exampleSubmission.set(exampleSubmission);

        comp.onReferencedFeedbackChanged(feedbacks);
        comp.showSubmission();

        expect(comp.feedbackChanged).toBe(false);
        expect(comp.assessmentMode()).toBe(false);
        expect(comp.totalScore()).toBe(mockFeedbackWithReference.credits);
    });

    it('should create error alert if assessment is invalid', () => {
        const alertSpy = vi.spyOn(alertService, 'error');
        comp.exercise.set(exercise);
        comp.exampleSubmission.set(exampleSubmission);
        comp.referencedFeedback.set([mockFeedbackInvalid]);

        comp.saveExampleAssessment();

        expect(alertSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.modelingAssessment.invalidAssessments');
    });

    it('should update assessment explanation and example assessment', () => {
        comp.exercise.set(exercise);
        comp.exampleSubmission.set({ ...exampleSubmission, assessmentExplanation: 'Explanation of the assessment' });
        comp.referencedFeedback.set([mockFeedbackWithReference]);
        comp.unreferencedFeedback.set([mockFeedbackWithoutReference]);

        const result = { id: 1 } as Result;
        const alertSpy = vi.spyOn(alertService, 'success');
        vi.spyOn(service, 'update').mockImplementation((updatedExampleSubmission) => of(new HttpResponse({ body: updatedExampleSubmission })));
        const modelingAssessmentService = TestBed.inject(ModelingAssessmentService);
        vi.spyOn(modelingAssessmentService, 'saveExampleAssessment').mockReturnValue(of(result));

        comp.saveExampleAssessment();

        expect(comp.result()).toBe(result);
        expect(alertSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.modelingAssessmentEditor.messages.saveSuccessful');
    });

    it('should update assessment explanation but create error message on example assessment update failure', () => {
        comp.exercise.set(exercise);
        comp.exampleSubmission.set({ ...exampleSubmission, assessmentExplanation: 'Explanation of the assessment' });
        comp.referencedFeedback.set([mockFeedbackWithReference, mockFeedbackWithoutReference]);

        const alertSpy = vi.spyOn(alertService, 'error');
        vi.spyOn(service, 'update').mockImplementation((updatedExampleSubmission) => of(new HttpResponse({ body: updatedExampleSubmission })));
        const modelingAssessmentService = TestBed.inject(ModelingAssessmentService);
        vi.spyOn(modelingAssessmentService, 'saveExampleAssessment').mockReturnValue(throwError(() => ({ status: 404 })));

        comp.saveExampleAssessment();

        expect(comp.result()).toBeUndefined();
        expect(alertSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.modelingAssessmentEditor.messages.saveFailed');
    });

    it('should mark all feedback correct', () => {
        comp.exercise.set(exercise);
        comp.exampleSubmission.set(exampleSubmission);
        comp.referencedFeedback.set([mockFeedbackInvalid]);
        comp.assessmentMode.set(true);

        comp.markAllFeedbackToCorrect();

        expect(comp.referencedFeedback().every((feedback) => feedback.correctionStatus === 'CORRECT')).toBe(true);
    });

    it('should mark all feedback wrong', () => {
        comp.exercise.set(exercise);
        comp.exampleSubmission.set(exampleSubmission);
        comp.referencedFeedback.set([mockFeedbackInvalid]);
        comp.assessmentMode.set(true);

        comp.markWrongFeedback([mockFeedbackCorrectionError]);

        expect(comp.referencedFeedback()[0].correctionStatus).toBe(mockFeedbackCorrectionError.type);
    });

    it('should show assessment', async () => {
        const result = { id: 1, feedbacks: [] } as Result;

        vi.spyOn(service, 'get').mockReturnValue(of(new HttpResponse({ body: exampleSubmission })));
        const modelingAssessmentService = TestBed.inject(ModelingAssessmentService);
        const assessmentSpy = vi.spyOn(modelingAssessmentService, 'getExampleAssessment').mockReturnValue(of(result));

        comp.exercise.set(exercise);
        comp.exampleSubmission.set(exampleSubmission);

        fixture.detectChanges();
        await fixture.whenStable();

        comp.showAssessment();

        expect(assessmentSpy).toHaveBeenCalledOnce();
        expect(comp.assessmentMode()).toBe(true);
        expect(result.feedbacks).toEqual(comp.assessments());
    });

    it('should call get exampleAssessment in toComplete mode', () => {
        routeQueryParam.toComplete = 1;

        const result = { id: 1 } as Result;
        const feedbackOne = { id: 1, type: FeedbackType.MANUAL_UNREFERENCED } as Feedback;
        const feedbackTwo = { id: 2, type: FeedbackType.MANUAL } as Feedback;
        result.feedbacks = [feedbackOne, feedbackTwo];

        vi.spyOn(service, 'get').mockReturnValue(of(new HttpResponse({ body: exampleSubmission })));
        const modelingAssessmentService = TestBed.inject(ModelingAssessmentService);
        const assessmentSpy = vi.spyOn(modelingAssessmentService, 'getExampleAssessment').mockReturnValue(of(result));

        fixture.detectChanges();

        expect(assessmentSpy).toHaveBeenCalledOnce();
        expect(comp.referencedExampleFeedback).toEqual([feedbackTwo]);
    });

    it('should mark only matching feedback as wrong', () => {
        const matchingFeedback = { ...mockFeedbackWithReference, reference: 'ref-1' } as Feedback;
        const otherFeedback = { ...mockFeedbackWithReference, reference: 'ref-2' } as Feedback;

        comp.referencedFeedback.set([matchingFeedback, otherFeedback]);

        const correctionError: FeedbackCorrectionError = {
            reference: 'ref-1',
            type: 'INCORRECT_SCORE',
        } as any;

        comp.markWrongFeedback([correctionError]);

        const [updated, untouched] = comp.referencedFeedback();
        expect(updated.reference).toBe('ref-1');
        expect(updated.correctionStatus).toBe('INCORRECT_SCORE');

        expect(untouched.reference).toBe('ref-2');
        expect(untouched.correctionStatus).toBe('CORRECT');
    });

    it('should mark assessments as invalid when a feedback has no credits', () => {
        comp.exercise.set(exercise);
        const feedbackWithoutCredits = {
            text: 'No credits',
            referenceId: 'id-1',
        } as Feedback;

        comp.referencedFeedback.set([feedbackWithoutCredits]);

        expect(comp.assessmentsAreValid()).toBe(false);
        expect(comp.invalidError()).toBeDefined();
        expect(comp.totalScore()).toBeUndefined();
    });

    it('should highlight missed referenced example feedback', () => {
        comp.exercise.set(exercise);

        const referencedExample1: Feedback = {
            ...mockFeedbackWithReference,
            referenceId: 'element-1',
            reference: 'ref-1',
        };
        const referencedExample2: Feedback = {
            ...mockFeedbackWithReference,
            referenceId: 'element-2',
            reference: 'ref-2',
        };

        comp.referencedExampleFeedback = [referencedExample1, referencedExample2];

        comp.referencedFeedback.set([referencedExample1]);

        (comp as any).highlightColor = 'testColor';

        comp.highlightMissedFeedback();

        const highlighted = comp.highlightedElements();
        expect(highlighted.size).toBe(1);
        expect(highlighted.get('element-2')).toBe('testColor');
        expect(highlighted.has('element-1')).toBe(false);
    });

    it('should treat empty assessments as valid with totalScore 0', () => {
        comp.exercise.set(exercise);
        expect(comp.assessments()).toHaveLength(0);
        expect(comp.totalScore()).toBe(0);
        expect(comp.assessmentsAreValid()).toBe(true);
        expect(comp.invalidError()).toBeUndefined();
    });

    it('should respect structured grading instruction usageCount when scoring', () => {
        const limitedInstruction = { id: 1, credits: 5, usageCount: 1 };
        const first = { ...mockFeedbackWithReference, credits: 5, gradingInstruction: limitedInstruction } as Feedback;
        const second = { ...mockFeedbackWithoutReference, credits: 5, gradingInstruction: limitedInstruction } as Feedback;

        comp.exercise.set({ ...exercise, maxPoints: 30 } as ModelingExercise);
        comp.referencedFeedback.set([first]);
        comp.unreferencedFeedback.set([second]);

        expect(comp.assessmentsAreValid()).toBe(true);
        expect(comp.totalScore()).toBe(5);
    });

    it('should cap the total score at the exercise maximum', () => {
        comp.exercise.set({ ...exercise, maxPoints: 10, bonusPoints: 0 } as ModelingExercise);
        comp.referencedFeedback.set([{ ...mockFeedbackWithReference, credits: 8 } as Feedback]);
        comp.unreferencedFeedback.set([{ ...mockFeedbackWithoutReference, credits: 5 } as Feedback]);

        expect(comp.totalScore()).toBe(10);
    });

    describe('practice assessment (toComplete)', () => {
        const solutionReferenced = { id: 2, type: FeedbackType.MANUAL, reference: 'ref-solution', referenceId: 'element-solution', credits: 5 } as Feedback;
        const solutionUnreferenced = { id: 1, type: FeedbackType.MANUAL_UNREFERENCED, credits: 3 } as Feedback;

        const startPracticeAssessment = async () => {
            routeQueryParam.toComplete = 1;
            const solution = { id: 1, feedbacks: [solutionUnreferenced, solutionReferenced] } as Result;
            vi.spyOn(service, 'get').mockReturnValue(of(new HttpResponse({ body: exampleSubmission })));
            vi.spyOn(TestBed.inject(ModelingAssessmentService), 'getExampleAssessment').mockReturnValue(of(solution));
            comp.exercise.set(exercise);

            fixture.detectChanges();
            await fixture.whenStable();
            fixture.detectChanges();
        };

        it('should allow submitting the assessment as soon as the page is opened', async () => {
            await startPracticeAssessment();

            expect(comp.result()).toBeUndefined();
            expect(comp.assessments()).toHaveLength(0);
            expect(comp.assessmentsAreValid()).toBe(true);

            const submitButton = fixture.nativeElement.querySelector('#submit-example-assessment') as HTMLButtonElement;
            expect(submitButton).not.toBeNull();
            expect(submitButton.disabled).toBe(false);
        });

        it('should offer the unreferenced feedback editor even though no result is loaded', async () => {
            await startPracticeAssessment();

            const details = fixture.nativeElement.querySelector('[assessmentworkspacedetails]') as HTMLElement;
            expect(details.querySelector('jhi-unreferenced-feedback')).not.toBeNull();
        });

        it('should count unreferenced feedback towards the score and the submitted assessment', async () => {
            const tutorParticipationService = TestBed.inject(TutorParticipationService);
            const assessSpy = vi.spyOn(tutorParticipationService, 'assessExampleSubmission');
            await startPracticeAssessment();

            const tutorFeedback = { text: 'Missing association', credits: 4, type: FeedbackType.MANUAL_UNREFERENCED, reference: '1' } as Feedback;
            comp.onUnReferencedFeedbackChanged([tutorFeedback]);

            expect(comp.totalScore()).toBe(4);
            expect(comp.assessmentsAreValid()).toBe(true);

            comp.checkAssessment();

            expect(assessSpy).toHaveBeenCalledOnce();
            const [submitted] = assessSpy.mock.calls[0];
            expect(submitted.submission!.results!.at(-1)!.feedbacks).toEqual([tutorFeedback]);
        });

        it('should disable submitting while a feedback has no score', async () => {
            await startPracticeAssessment();

            comp.onUnReferencedFeedbackChanged([{ text: 'No score yet', type: FeedbackType.MANUAL_UNREFERENCED } as Feedback]);
            fixture.detectChanges();

            expect(comp.assessmentsAreValid()).toBe(false);
            expect((fixture.nativeElement.querySelector('#submit-example-assessment') as HTMLButtonElement).disabled).toBe(true);
        });
    });

    describe('grading the tutor training assessment', () => {
        it('should mark unreferenced feedback wrong as well and keep the feedback instances shared with the canvas', () => {
            const referenced = { ...mockFeedbackWithReference, reference: 'ref-1', correctionStatus: undefined } as Feedback;
            const unreferenced = { text: 'Unnecessary', credits: 1, type: FeedbackType.MANUAL_UNREFERENCED, reference: '1' } as Feedback;
            comp.referencedFeedback.set([referenced]);
            comp.unreferencedFeedback.set([unreferenced]);

            comp.markAllFeedbackToCorrect();
            comp.markWrongFeedback([{ reference: '1', type: FeedbackCorrectionErrorType.UNNECESSARY_FEEDBACK } as FeedbackCorrectionError]);

            expect(comp.referencedFeedback()[0]).toBe(referenced);
            expect(referenced.correctionStatus).toBe('CORRECT');

            expect(comp.unreferencedFeedback()[0]).not.toBe(unreferenced);
            expect(comp.unreferencedFeedback()[0].correctionStatus).toBe(FeedbackCorrectionErrorType.UNNECESSARY_FEEDBACK);
        });

        it('should refresh the highlighted elements when the assessment turns out to be correct', () => {
            const missed: Feedback = { ...mockFeedbackWithReference, referenceId: 'element-1', reference: 'ref-1' };
            comp.referencedExampleFeedback = [missed];
            comp.highlightedElements.set(new Map([['element-1', 'stale']]));
            comp.referencedFeedback.set([missed]);

            comp.markAllFeedbackToCorrect();

            expect(comp.highlightedElements().size).toBe(0);
        });
    });
    it('should persist a training-mode change made while assessing', () => {
        const exampleSubmission = { id: 42, usedForTutorial: false, assessmentExplanation: 'same' } as ExampleSubmission;
        comp.exercise.set(exercise);
        comp.exampleSubmission.set(exampleSubmission);
        comp.assessmentExplanation.set('same');
        comp['exampleSubmissionId'] = 42;
        comp.selectedMode.set(ExampleSubmissionMode.ASSESS_CORRECTLY);

        const update = vi.spyOn(TestBed.inject(ExampleSubmissionService), 'update').mockReturnValue(of(new HttpResponse({ body: exampleSubmission })));
        vi.spyOn(TestBed.inject(ModelingAssessmentService), 'saveExampleAssessment').mockReturnValue(of(new Result()));

        comp.saveExampleAssessment();

        expect(update).toHaveBeenCalledOnce();
        expect(update.mock.calls[0][0].usedForTutorial).toBe(true);
    });

    it('should not round-trip the example submission when the training mode is unchanged', () => {
        const exampleSubmission = { id: 42, usedForTutorial: true, assessmentExplanation: 'same' } as ExampleSubmission;
        comp.exercise.set(exercise);
        comp.exampleSubmission.set(exampleSubmission);
        comp.assessmentExplanation.set('same');
        comp['exampleSubmissionId'] = 42;
        comp.selectedMode.set(ExampleSubmissionMode.ASSESS_CORRECTLY);

        const update = vi.spyOn(TestBed.inject(ExampleSubmissionService), 'update');
        const saveAssessment = vi.spyOn(TestBed.inject(ModelingAssessmentService), 'saveExampleAssessment').mockReturnValue(of(new Result()));

        comp.saveExampleAssessment();

        expect(update).not.toHaveBeenCalled();
        expect(saveAssessment).toHaveBeenCalledOnce();
    });
});
