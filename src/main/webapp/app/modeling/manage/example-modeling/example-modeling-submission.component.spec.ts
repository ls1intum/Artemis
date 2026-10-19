import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { ActivatedRoute, ActivatedRouteSnapshot, Router, convertToParamMap } from '@angular/router';
import { ChangeDetectorRef, Component, forwardRef, input } from '@angular/core';
import { MockComponent, MockProvider } from 'ng-mocks';
import { ModelingEditorComponent } from 'app/modeling/shared/modeling-editor/modeling-editor.component';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Feedback, FeedbackCorrectionError, FeedbackCorrectionErrorType, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { UMLDiagramType, UMLModel } from '@tumaet/apollon';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
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
import { deepClone } from 'app/foundation/util/deep-clone.util';

@Component({
    selector: 'jhi-modeling-editor',
    template: '',
    providers: [{ provide: ModelingEditorComponent, useExisting: forwardRef(() => StubModelingEditorComponent) }],
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

    describe('ordered saves', () => {
        let currentModel: UMLModel;
        let updateRequests: { payload: ExampleSubmission; response: Subject<HttpResponse<ExampleSubmission>> }[];
        let assessmentRequests: { feedbacks: Feedback[]; response: Subject<Result> }[];

        beforeEach(() => {
            currentModel = TestBed.runInInjectionContext(() => new StubModelingEditorComponent().getCurrentModel());
            currentModel.nodes = [
                { id: 'relationshipId', type: 'class', position: { x: 0, y: 0 }, width: 100, height: 100, measured: { width: 100, height: 100 }, data: { name: 'Example' } },
            ];
            const storedSubmission = new ModelingSubmission();
            storedSubmission.id = 20;
            storedSubmission.model = JSON.stringify(currentModel);
            comp.modelingSubmission = storedSubmission;
            comp.exampleSubmission.set({ id: 35, submission: storedSubmission, assessmentExplanation: 'Saved rationale', usedForTutorial: false });
            comp['exampleSubmissionId'] = 35;
            comp.exerciseId = EXERCISE_ID;
            comp.exercise.set(exercise);
            comp.umlModel.set(deepClone(currentModel));
            comp.assessmentExplanation.set('Saved rationale');
            comp.selectedMode.set(ExampleSubmissionMode.READ_AND_CONFIRM);
            vi.spyOn(comp, 'ngOnInit').mockImplementation(() => {});
            vi.spyOn(comp, 'modelingEditor').mockReturnValue({ getCurrentModel: () => currentModel } as ModelingEditorComponent);
            updateRequests = [];
            assessmentRequests = [];
            vi.spyOn(service, 'update').mockImplementation((payload) => {
                const response = new Subject<HttpResponse<ExampleSubmission>>();
                updateRequests.push({ payload: deepClone(payload), response });
                return response;
            });
            vi.spyOn(TestBed.inject(ModelingAssessmentService), 'saveExampleAssessment').mockImplementation((feedbacks) => {
                const response = new Subject<Result>();
                assessmentRequests.push({ feedbacks: deepClone(feedbacks), response });
                return response;
            });
        });

        function completeUpdate(index: number) {
            const request = updateRequests[index];
            request.response.next(new HttpResponse({ body: deepClone(request.payload) }));
            request.response.complete();
        }

        function completeAssessment(index: number) {
            const request = assessmentRequests[index];
            request.response.next({ id: 1, feedbacks: deepClone(request.feedbacks) } as Result);
            request.response.complete();
        }

        it('orders metadata, assessment, and model writes and retains each clicked model', () => {
            comp.assessmentExplanation.set('Changed rationale');
            comp.saveExampleAssessment();
            currentModel.title = 'Second model';
            comp.showAssessment();
            const canvasInput = comp.umlModel();
            currentModel.title = 'Unsaved third model';

            expect(updateRequests).toHaveLength(1);
            completeUpdate(0);
            expect(assessmentRequests).toHaveLength(1);
            expect(updateRequests).toHaveLength(1);
            completeAssessment(0);
            expect(updateRequests).toHaveLength(2);
            expect(JSON.parse((updateRequests[1].payload.submission as ModelingSubmission).model!).title).toBe('Second model');
            completeUpdate(1);
            expect(currentModel.title).toBe('Unsaved third model');
            // A stale model response must not replace the editor's input with the earlier saved model.
            expect(comp.umlModel()).toBe(canvasInput);
        });

        it('builds queued metadata writes from the latest saved model', () => {
            currentModel.title = 'Second model';
            comp.showAssessment();
            comp.assessmentExplanation.set('New rationale');
            comp.saveExampleAssessment();

            expect(updateRequests).toHaveLength(1);
            completeUpdate(0);
            expect(updateRequests).toHaveLength(2);
            expect(JSON.parse((updateRequests[1].payload.submission as ModelingSubmission).model!).title).toBe('Second model');
            expect(updateRequests[1].payload.assessmentExplanation).toBe('New rationale');
            completeUpdate(1);
            completeAssessment(0);
        });

        it.each([undefined, Number.NaN])('keeps feedback with credit %s unsaved when switching back to an edited model', (credits) => {
            comp['updateAssessment']({ id: 1, feedbacks: [deepClone(mockFeedbackWithoutReference)] } as Result);
            const invalidFeedback = { ...mockFeedbackWithoutReference, credits };
            comp.onUnReferencedFeedbackChanged([invalidFeedback]);
            comp.showSubmission();
            expect(assessmentRequests).toHaveLength(0);

            currentModel.title = 'Edited model';
            comp.showAssessment();
            completeUpdate(0);
            expect(JSON.parse(comp.modelingSubmission.model!).title).toBe('Edited model');
            expect(assessmentRequests).toHaveLength(0);
            expect(comp.unreferencedFeedback()).toEqual([invalidFeedback]);
            expect(comp.feedbackChanged()).toBe(true);
        });

        it('rejects an invalid captured assessment even after its live feedback has been corrected', () => {
            comp.onUnReferencedFeedbackChanged([{ ...mockFeedbackWithoutReference, credits: undefined }]);
            comp.upsertExampleModelingSubmission();
            const corrected = { ...mockFeedbackWithoutReference, credits: 4 };
            comp.onUnReferencedFeedbackChanged([corrected]);
            completeUpdate(0);
            expect(assessmentRequests).toHaveLength(0);
            expect(comp.feedbackChanged()).toBe(true);

            comp.saveExampleAssessment();
            expect(assessmentRequests[0].feedbacks).toEqual([corrected]);
            completeAssessment(0);
            expect(comp.feedbackChanged()).toBe(false);
        });

        it('saves valid captured feedback while retaining a newer invalid edit as dirty', () => {
            const validFeedback = { ...mockFeedbackWithoutReference, credits: 4 };
            comp.onUnReferencedFeedbackChanged([validFeedback]);
            comp.upsertExampleModelingSubmission();
            const invalidFeedback = { ...validFeedback, credits: undefined };
            comp.onUnReferencedFeedbackChanged([invalidFeedback]);
            completeUpdate(0);
            expect(assessmentRequests[0].feedbacks).toEqual([validFeedback]);
            completeAssessment(0);
            expect(comp.unreferencedFeedback()).toEqual([invalidFeedback]);
            expect(comp.assessmentsAreValid()).toBe(false);
            expect(comp.feedbackChanged()).toBe(true);
        });

        it.each([false, true])('preserves the stored model and feedback without an editor (dirty: %s)', (dirty) => {
            const savedFeedback = deepClone(mockFeedbackWithReference);
            comp['updateAssessment']({ id: 1, feedbacks: [savedFeedback] } as Result);
            const currentFeedback = dirty ? { ...savedFeedback, text: 'Unsaved feedback' } : savedFeedback;
            comp.onReferencedFeedbackChanged([currentFeedback]);
            const storedModel = comp.modelingSubmission.model;
            vi.mocked(comp.modelingEditor).mockReturnValue(undefined);

            comp.upsertExampleModelingSubmission();
            expect(updateRequests).toHaveLength(0);
            expect(assessmentRequests).toHaveLength(0);
            expect(comp.modelingSubmission.model).toBe(storedModel);
            expect(comp.referencedFeedback()).toEqual([currentFeedback]);
            expect(comp.feedbackChanged()).toBe(dirty);

            vi.mocked(comp.modelingEditor).mockReturnValue({ getCurrentModel: () => currentModel } as ModelingEditorComponent);
            comp.upsertExampleModelingSubmission();
            completeUpdate(0);
            expect(assessmentRequests[0].feedbacks).toEqual([currentFeedback]);
            completeAssessment(0);
            expect(comp.modelingSubmission.model).toBe(storedModel);
            expect(comp.referencedFeedback()).toEqual([currentFeedback]);
            expect(comp.feedbackChanged()).toBe(false);
        });

        it('keeps the editor input stable when a model save returns after another edit', () => {
            fixture.detectChanges();
            const editor = fixture.debugElement.query((element) => element.componentInstance instanceof StubModelingEditorComponent)
                .componentInstance as StubModelingEditorComponent;
            const originalInput = editor.umlModel();
            currentModel.title = 'Second model';
            comp.upsertExampleModelingSubmission();
            currentModel.title = 'Unsaved third model';
            completeUpdate(0);
            fixture.detectChanges();
            expect(editor.umlModel()).toBe(originalInput);
            expect(comp['modelChanged']()).toBe(true);
            completeAssessment(0);
        });

        it.each(['success', 'failure', 'already saved'])('preserves the model when the editor is recreated around a %s response', (outcome) => {
            const renderedEditor = () =>
                fixture.debugElement.query((element) => element.componentInstance instanceof StubModelingEditorComponent)?.componentInstance as
                    StubModelingEditorComponent | undefined;
            vi.mocked(comp.modelingEditor).mockImplementation(() => renderedEditor() as ModelingEditorComponent | undefined);
            vi.spyOn(StubModelingEditorComponent.prototype, 'getCurrentModel').mockImplementation(function (this: StubModelingEditorComponent) {
                return this.umlModel()!;
            });
            fixture.detectChanges();
            const originalEditor = renderedEditor()!;
            currentModel.title = 'Edited model';
            vi.spyOn(originalEditor, 'getCurrentModel').mockImplementation(() => currentModel);
            if (outcome === 'already saved') {
                comp.upsertExampleModelingSubmission();
                completeUpdate(0);
                completeAssessment(0);
            }

            comp.showAssessment();
            fixture.detectChanges();
            expect(renderedEditor()).toBeUndefined();
            comp.showSubmission();
            fixture.detectChanges();
            const recreatedEditor = renderedEditor()!;
            expect(recreatedEditor).not.toBe(originalEditor);
            expect(recreatedEditor.umlModel()!.title).toBe('Edited model');

            if (outcome === 'success') {
                completeUpdate(0);
            } else if (outcome === 'failure') {
                updateRequests[0].response.error(new HttpErrorResponse({ status: 500 }));
            }
            fixture.detectChanges();
            expect(recreatedEditor.getCurrentModel().title).toBe('Edited model');
            expect(comp['modelChanged']()).toBe(outcome === 'failure');
            comp.showAssessment();
            expect(updateRequests).toHaveLength(outcome === 'failure' ? 2 : 1);
            if (outcome === 'failure') {
                expect(JSON.parse((updateRequests[1].payload.submission as ModelingSubmission).model!).title).toBe('Edited model');
            }
        });

        it('preserves feedback for a newer model while an older model save completes', () => {
            currentModel.title = 'First pending model';
            comp.upsertExampleModelingSubmission();
            currentModel.nodes.push({ ...deepClone(currentModel.nodes[0]), id: 'new-element' });
            comp.showAssessment();
            const newFeedback = { ...mockFeedbackWithReference, referenceId: 'new-element', reference: 'Class:new-element' };
            comp.onReferencedFeedbackChanged([newFeedback]);

            completeUpdate(0);
            expect(comp.referencedFeedback()).toEqual([newFeedback]);
            completeAssessment(0);
            completeUpdate(1);
            expect(comp.referencedFeedback()).toEqual([newFeedback]);
            expect(comp.feedbackChanged()).toBe(true);
        });

        it('keeps a rejected model out of the next queued metadata write', () => {
            currentModel.title = 'Rejected model';
            comp.showAssessment();
            comp.assessmentExplanation.set('New rationale');
            comp.saveExampleAssessment();
            expect(updateRequests).toHaveLength(1);
            updateRequests[0].response.error(new Error('Model save failed'));
            expect(updateRequests).toHaveLength(2);
            expect(JSON.parse((updateRequests[1].payload.submission as ModelingSubmission).model!).title).toBe('Test Model');
            completeUpdate(1);
            completeAssessment(0);
        });

        it.each(['success', 'failure'])('preserves newer feedback in the canvas after a delayed %s and continues saving', (outcome) => {
            const first: Feedback = { ...mockFeedbackWithReference, text: 'First assessment' };
            const second: Feedback = { ...mockFeedbackWithReference, text: 'Newer assessment' };
            const firstUnreferenced: Feedback = { ...mockFeedbackWithoutReference, text: 'First general feedback' };
            const secondUnreferenced: Feedback = { ...mockFeedbackWithoutReference, text: 'Newer general feedback' };
            comp.assessmentMode.set(true);
            comp.onReferencedFeedbackChanged([first]);
            comp.onUnReferencedFeedbackChanged([firstUnreferenced]);
            fixture.detectChanges();
            const canvas = fixture.debugElement.query((element) => element.componentInstance instanceof StubModelingAssessmentComponent)
                .componentInstance as StubModelingAssessmentComponent;
            comp.saveExampleAssessment();
            comp.onReferencedFeedbackChanged([second]);
            comp.onUnReferencedFeedbackChanged([secondUnreferenced]);
            comp.saveExampleAssessment();
            expect(assessmentRequests).toHaveLength(1);
            expect(assessmentRequests[0].feedbacks).toEqual([first, firstUnreferenced]);

            if (outcome === 'success') {
                completeAssessment(0);
            } else {
                assessmentRequests[0].response.error(new Error('Save failed'));
            }
            fixture.detectChanges();
            expect(comp.assessments()).toEqual([second, secondUnreferenced]);
            expect(canvas.resultFeedbacks()).toEqual([second]);
            expect(assessmentRequests).toHaveLength(2);
            expect(assessmentRequests[1].feedbacks).toEqual([second, secondUnreferenced]);
            completeAssessment(1);
            fixture.detectChanges();
            expect(canvas.resultFeedbacks()).toEqual([second]);
        });

        it('derives dirtiness when feedback is reverted and keeps failed saves dirty', () => {
            const original = [deepClone(mockFeedbackWithReference)];
            comp['updateAssessment']({ id: 1, feedbacks: original } as Result);
            comp.onReferencedFeedbackChanged([{ ...original[0], credits: 5 }]);
            expect(comp.feedbackChanged()).toBe(true);
            comp.onReferencedFeedbackChanged(deepClone(original));
            expect(comp.feedbackChanged()).toBe(false);
            comp.onReferencedFeedbackChanged([{ ...original[0], credits: 4 }]);
            comp.showSubmission();
            assessmentRequests[0].response.error(new Error('Save failed'));
            expect(comp.feedbackChanged()).toBe(true);
        });

        it('queues repeated create clicks and updates the created submission on the second click', () => {
            comp.isNewSubmission.set(true);
            const creation = new Subject<HttpResponse<ExampleSubmission>>();
            const create = vi.spyOn(service, 'create').mockReturnValue(creation);
            comp.upsertExampleModelingSubmission();
            currentModel.title = 'Second model';
            comp.upsertExampleModelingSubmission();
            expect(create).toHaveBeenCalledOnce();
            const created = deepClone(create.mock.calls[0][0]);
            created.id = 36;
            created.submission!.id = 21;
            creation.next(new HttpResponse({ body: created }));
            creation.complete();
            expect(updateRequests).toHaveLength(1);
            expect(updateRequests[0].payload.id).toBe(36);
            expect(JSON.parse((updateRequests[0].payload.submission as ModelingSubmission).model!).title).toBe('Second model');
        });

        it('continues after a failed metadata write and preserves newer metadata while an older save finishes', () => {
            comp.assessmentExplanation.set('First rationale');
            comp.saveExampleAssessment();
            comp.assessmentExplanation.set('Second rationale');
            comp.saveExampleAssessment();
            expect(updateRequests).toHaveLength(1);
            updateRequests[0].response.error(new Error('Metadata save failed'));
            expect(updateRequests).toHaveLength(2);
            expect(updateRequests[1].payload.assessmentExplanation).toBe('Second rationale');
            comp.assessmentExplanation.set('Unsaved rationale');
            completeUpdate(1);
            expect(comp.assessmentExplanation()).toBe('Unsaved rationale');
            expect(assessmentRequests).toHaveLength(1);
            completeAssessment(0);
        });

        it('isolates in-flight feedback and adopts server IDs without leaving an unchanged assessment dirty', () => {
            const feedback = { ...mockFeedbackWithReference, text: 'Saved text' };
            comp.onReferencedFeedbackChanged([feedback]);
            comp.saveExampleAssessment();
            feedback.text = 'Edited text';
            comp.onReferencedFeedbackChanged([feedback]);
            expect(assessmentRequests[0].feedbacks[0].text).toBe('Saved text');
            completeAssessment(0);
            expect(comp.feedbackChanged()).toBe(true);
            comp.saveExampleAssessment();
            assessmentRequests[1].response.next({ id: 1, feedbacks: [{ ...assessmentRequests[1].feedbacks[0], id: 17 }] } as Result);
            assessmentRequests[1].response.complete();
            expect(comp.assessments()[0].id).toBe(17);
            expect(comp.assessments()[0].text).toBe('Edited text');
            expect(comp.feedbackChanged()).toBe(false);
        });

        it('treats mixed server feedback ordering as a clean assessment on load and save', () => {
            const referenced = deepClone(mockFeedbackWithReference);
            const unreferenced = deepClone(mockFeedbackWithoutReference);
            comp['updateAssessment']({ id: 1, feedbacks: [unreferenced, referenced] } as Result);
            expect(comp.feedbackChanged()).toBe(false);
            comp.saveExampleAssessment();
            assessmentRequests[0].response.next({ id: 1, feedbacks: [unreferenced, referenced] } as Result);
            assessmentRequests[0].response.complete();
            expect(comp.feedbackChanged()).toBe(false);
        });

        it('does not restore deleted-element feedback from an assessment queued before the model save completed', () => {
            comp['updateAssessment']({ id: 1, feedbacks: [deepClone(mockFeedbackWithReference), deepClone(mockFeedbackWithoutReference)] } as Result);
            currentModel.nodes = [];
            comp.showAssessment();
            const newerUnreferenced = { ...mockFeedbackWithoutReference, text: 'New general feedback' };
            comp.onUnReferencedFeedbackChanged([newerUnreferenced]);
            comp.saveExampleAssessment();
            completeUpdate(0);
            expect(assessmentRequests[0].feedbacks).toEqual([mockFeedbackWithoutReference]);
            completeAssessment(0);
            expect(assessmentRequests[1].feedbacks).toEqual([newerUnreferenced]);
            completeAssessment(1);
            expect(comp.assessments()).toEqual([newerUnreferenced]);
            expect(comp.feedbackChanged()).toBe(false);
        });

        it('prunes deleted references from newer live feedback without discarding edits to surviving elements', () => {
            comp['updateAssessment']({ id: 1, feedbacks: [deepClone(mockFeedbackWithReference)] } as Result);
            currentModel.nodes = [{ ...currentModel.nodes[0], id: 'surviving-element' }];
            comp.showAssessment();
            const newer = { ...mockFeedbackWithReference, referenceId: 'surviving-element', reference: 'Class:surviving-element', text: 'Newer valid feedback' };
            comp.onReferencedFeedbackChanged([deepClone(mockFeedbackWithReference), newer]);
            completeUpdate(0);
            completeAssessment(0);
            expect(comp.referencedFeedback()).toEqual([newer]);
            expect(comp.feedbackChanged()).toBe(true);
        });
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
        expect(serviceSpy).toHaveBeenCalledOnce();
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

        expect(comp.feedbackChanged()).toBe(true);
        expect(comp.assessmentsAreValid()).toBe(true);
        expect(comp.referencedFeedback()).toEqual(feedbacks);
    });

    it('should handle unreferenced feedback change', () => {
        const feedbacks = [mockFeedbackWithoutReference];
        comp.exercise.set(exercise);

        comp.onUnReferencedFeedbackChanged(feedbacks);

        expect(comp.feedbackChanged()).toBe(true);
        expect(comp.assessmentsAreValid()).toBe(true);
        expect(comp.unreferencedFeedback()).toEqual(feedbacks);
    });

    it('should show submission and retain dirtiness until its assessment is saved', () => {
        const feedbacks = [mockFeedbackWithReference];
        comp.exercise.set(exercise);
        comp.exampleSubmission.set(exampleSubmission);

        comp.onReferencedFeedbackChanged(feedbacks);
        comp.showSubmission();

        expect(comp.feedbackChanged()).toBe(true);
        expect(comp.assessmentMode()).toBe(false);
        expect(comp.totalScore()).toBe(mockFeedbackWithReference.credits);
    });

    it('should not prune feedback when the model change is rejected', async () => {
        vi.spyOn(service, 'update').mockReturnValue(throwError(() => ({ status: 500 })));
        const saveAssessmentSpy = vi.spyOn(TestBed.inject(ModelingAssessmentService), 'saveExampleAssessment').mockReturnValue(of(new Result()));
        comp.exercise.set(exercise);
        comp.exampleSubmission.set(exampleSubmission);
        comp.modelingSubmission = new ModelingSubmission();
        // The rendered editor has an empty model, so the referenced feedback belongs to a deleted element.
        comp['updateAssessment']({ id: 1, feedbacks: [mockFeedbackWithReference] } as Result);
        vi.spyOn(comp, 'ngOnInit').mockImplementation(() => {});
        fixture.detectChanges();

        comp.showAssessment();
        await fixture.whenStable();
        expect(service.update).toHaveBeenCalledOnce();

        // the server kept the old model, so its feedback must survive and must not be queued for the assessment endpoint
        expect(comp.referencedFeedback()).toEqual([mockFeedbackWithReference]);
        expect(comp.feedbackChanged()).toBe(false);
        expect(saveAssessmentSpy).not.toHaveBeenCalled();

        // switching back must not persist a pruned assessment against the unchanged server model
        comp.showSubmission();
        await fixture.whenStable();
        expect(saveAssessmentSpy).not.toHaveBeenCalled();
    });

    it('should persist pruned feedback when switching to the assessment after a model change', async () => {
        vi.spyOn(service, 'update').mockImplementation((updatedExampleSubmission) => of(new HttpResponse({ body: updatedExampleSubmission })));
        const saveAssessmentSpy = vi.spyOn(TestBed.inject(ModelingAssessmentService), 'saveExampleAssessment').mockReturnValue(of(new Result()));
        comp.exercise.set(exercise);
        comp.exampleSubmission.set(exampleSubmission);
        comp.modelingSubmission = new ModelingSubmission();
        // The rendered editor has an empty model, so the referenced feedback belongs to a deleted element.
        comp['updateAssessment']({ id: 1, feedbacks: [mockFeedbackWithReference] } as Result);
        vi.spyOn(comp, 'ngOnInit').mockImplementation(() => {});
        fixture.detectChanges();

        comp.showAssessment();
        await fixture.whenStable();
        expect(service.update).toHaveBeenCalledOnce();

        expect(comp.referencedFeedback()).toEqual([]);
        expect(saveAssessmentSpy).toHaveBeenCalledOnce();
        expect(comp.feedbackChanged()).toBe(false);
        expect(comp.assessmentMode()).toBe(true);
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

        it('keeps additional practice feedback in the general editor and out of the diagram canvas', async () => {
            await startPracticeAssessment();
            const generalEditor = fixture.debugElement.query((element) => element.componentInstance instanceof UnreferencedFeedbackComponent)
                .componentInstance as UnreferencedFeedbackComponent;
            generalEditor.addUnreferencedFeedback();
            fixture.detectChanges();
            await fixture.whenStable();

            const feedback = comp.unreferencedFeedback()[0];
            expect(feedback.type).toBe(FeedbackType.MANUAL_UNREFERENCED);
            expect(feedback.reference).toBe('1');
            expect(feedback.referenceId).toBeUndefined();
            expect(generalEditor.feedbacks()).toEqual([feedback]);
            expect(fixture.nativeElement.querySelector('jhi-unreferenced-feedback-detail')).not.toBeNull();
            const canvas = fixture.debugElement.query((element) => element.componentInstance instanceof StubModelingAssessmentComponent)
                .componentInstance as StubModelingAssessmentComponent;
            expect(canvas.resultFeedbacks()).toEqual([]);
            expect(comp.assessments()).toEqual([feedback]);

            feedback.credits = 4;
            generalEditor.updateFeedback(feedback);
            expect(comp.totalScore()).toBe(4);
            expect(comp.assessmentsAreValid()).toBe(true);
            const assessSpy = vi.spyOn(TestBed.inject(TutorParticipationService), 'assessExampleSubmission');
            comp.checkAssessment();
            expect(assessSpy.mock.calls[0][0].submission!.results!.at(-1)!.feedbacks).toEqual([feedback]);
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
