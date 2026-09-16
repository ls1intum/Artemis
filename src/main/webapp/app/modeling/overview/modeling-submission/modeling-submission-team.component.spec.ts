import { Component, EventEmitter, input, output } from '@angular/core';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ChangeDetectorRef } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { UMLDiagramType, UMLModel } from '@tumaet/apollon';
import { TranslateService } from '@ngx-translate/core';
import { ComplaintsStudentViewComponent } from 'app/assessment/overview/complaints-for-students/complaints-student-view.component';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { AccountService } from 'app/core/auth/account.service';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { AdditionalFeedbackComponent } from 'app/exercise/additional-feedback/additional-feedback.component';
import { RatingComponent } from 'app/exercise/rating/rating.component';
import { ExerciseMode } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { SubmissionPatch } from 'app/exercise/shared/entities/submission/submission-patch.model';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { TeamSubmissionSyncComponent } from 'app/exercise/team-submission-sync/team-submission-sync.component';
import { ModelingAssessmentComponent } from 'app/modeling/manage/assess/modeling-assessment.component';
import { routes } from 'app/modeling/overview/modeling-participation.route';
import { ModelingSubmissionComponent } from 'app/modeling/overview/modeling-submission/modeling-submission.component';
import { ModelingSubmissionService } from 'app/modeling/overview/modeling-submission/modeling-submission.service';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { ModelingEditorComponent } from 'app/modeling/shared/modeling-editor/modeling-editor.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/foundation/pipes/artemis-time-ago.pipe';
import { ResizeableContainerComponent } from 'app/shared-ui/resizeable-container/resizeable-container.component';
import { AlertService } from 'app/foundation/service/alert.service';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { BehaviorSubject, Observable, of, throwError } from 'rxjs';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockComplaintService } from 'test/helpers/mocks/service/mock-complaint.service';
import { MockParticipationWebsocketService } from 'test/helpers/mocks/service/mock-participation-websocket.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';

@Component({
    selector: 'jhi-modeling-editor',
    template: '',
})
class StubModelingEditorComponent {
    umlModel = input<UMLModel>();
    diagramType = input<UMLDiagramType>();
    readOnly = input<boolean>(false);
    tile = input(false);
    showHelpButton = input<boolean>(true);
    withExplanation = input<boolean>(false);
    problemStatement = input<string>();
    savedStatus = input<{ isChanged?: boolean; isSaving?: boolean }>();

    savedStatusOutput = output<boolean>();
    onModelChanged = output<UMLModel>();
    onModelPatch = output<unknown[]>();

    apollonEditor = { nextRender: Promise.resolve() };
    isApollonEditorMounted = false;

    getCurrentModel(): UMLModel {
        return { elements: {}, relationships: {}, version: '3.0.0' } as unknown as UMLModel;
    }

    importPatch = vi.fn();

    resynchronizeCollaborationAfterReconnect = vi.fn();
}

describe('ModelingSubmissionComponent', () => {
    let comp: ModelingSubmissionComponent;
    let fixture: ComponentFixture<ModelingSubmissionComponent>;
    let service: ModelingSubmissionService;
    let alertService: AlertService;

    const route = { params: of({ courseId: 5, exerciseId: 22, participationId: 123 }) } as any as ActivatedRoute;
    const participation = new StudentParticipation();
    participation.exercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
    participation.exercise.teamMode = true;
    participation.exercise.mode = ExerciseMode.TEAM;
    participation.id = 1;
    const submission = <ModelingSubmission>(<unknown>{ id: 20, submitted: true, participation });

    const validMockModel = JSON.stringify({
        version: '3.0.0',
        type: 'ClassDiagram',
        size: { width: 100, height: 100 },
        interactive: { elements: {}, relationships: {} },
        elements: { element1: { id: 'element1', type: 'Class', name: 'TestClass', bounds: { x: 0, y: 0, width: 100, height: 100 } } },
        relationships: {},
        assessments: {},
    });

    const originalConsoleError = console.error;

    let mockModelingEditor: Partial<ModelingEditorComponent>;

    function createComponent() {
        TestBed.overrideComponent(ModelingSubmissionComponent, {
            remove: {
                imports: [ModelingEditorComponent, RatingComponent],
            },
            add: {
                imports: [StubModelingEditorComponent, MockComponent(RatingComponent)],
            },
        });

        fixture = TestBed.createComponent(ModelingSubmissionComponent);
        comp = fixture.componentInstance;
        service = TestBed.inject(ModelingSubmissionService);
        alertService = TestBed.inject(AlertService);

        mockModelingEditor = {
            getCurrentModel: vi.fn().mockReturnValue({
                elements: { element1: { id: 'element1', type: 'Class' } },
                relationships: {},
                version: '3.0.0',
                type: 'ClassDiagram',
                size: { width: 0, height: 0 },
                interactive: { elements: {}, relationships: {} },
                assessments: {},
            } as unknown as UMLModel),
            isApollonEditorMounted: false,
            onModelPatch: new EventEmitter() as any,
            importPatch: vi.fn(),
            resynchronizeCollaborationAfterReconnect: vi.fn(),
        };

        vi.spyOn(comp, 'modelingEditor').mockReturnValue(mockModelingEditor as ModelingEditorComponent);
    }

    beforeEach(() => {
        participation.exercise ??= new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
        participation.exercise.teamMode = true;
        participation.exercise.mode = ExerciseMode.TEAM;
        submission.participation = participation;
        TestBed.configureTestingModule({
            imports: [
                RouterModule.forRoot([routes[0]]),
                ModelingSubmissionComponent,
                StubModelingEditorComponent,
                MockDirective(MarkdownDirective),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisTimeAgoPipe),
                MockComponent(ResizeableContainerComponent),
                MockComponent(TeamSubmissionSyncComponent),
                MockComponent(ModelingAssessmentComponent),
                MockComponent(AdditionalFeedbackComponent),
                MockComponent(RatingComponent),
                MockComponent(ComplaintsStudentViewComponent),
            ],
            providers: [
                MockProvider(ChangeDetectorRef),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ComplaintService, useClass: MockComplaintService },
                LocalStorageService,
                SessionStorageService,
                { provide: ActivatedRoute, useValue: route },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: WebsocketService, useClass: MockWebsocketService },
                provideHttpClient(),
                provideHttpClientTesting(),
                {
                    provide: AccountService,
                    useClass: MockAccountService,
                },
            ],
        });
        console.error = vi.fn();
    });

    afterEach(() => {
        vi.restoreAllMocks();
        console.error = originalConsoleError;

        submission.submitted = true;
        submission.model = undefined;
        submission.participation!.initializationDate = undefined;
        (<StudentParticipation>submission.participation).exercise!.dueDate = undefined;
        (<StudentParticipation>submission.participation).exercise!.exerciseGroup = undefined;

        if (comp) {
            comp.ngOnDestroy();
        }
    });

    it('should call load getDataForModelingEditor on init', () => {
        createComponent();

        const getLatestSubmissionForModelingEditorStub = vi.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));

        comp.ngOnInit();

        expect(getLatestSubmissionForModelingEditorStub).toHaveBeenCalledOnce();
        expect(comp.submission().id).toBe(20);
    });

    it('should subscribe to modeling editor patches.', async () => {
        createComponent();

        vi.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));

        comp.ngOnInit();
        fixture.detectChanges();
        await fixture.whenStable();

        const receiverMock = vi.fn();
        const submissionPatches = (comp as unknown as { submissionPatchObservable: Observable<SubmissionPatch> }).submissionPatchObservable;
        submissionPatches.subscribe(receiverMock);

        comp.onModelPatch(btoa(JSON.stringify({ test: 'value' })));

        expect(receiverMock).toHaveBeenCalled();
        expect(receiverMock.mock.lastCall![0].patch).toBe(btoa(JSON.stringify({ test: 'value' })));
    });

    it('should update the submission when a patch is received.', () => {
        createComponent();

        submission.model = JSON.stringify({
            version: '3.0.0',
            type: 'ClassDiagram',
            size: { width: 100, height: 100 },
            interactive: { elements: {}, relationships: {} },
            elements: { '1': { id: '1', type: 'Class', name: 'TestClass', bounds: { x: 0, y: 0, width: 100, height: 100 } } },
            relationships: {},
            assessments: {},
        });
        vi.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));

        comp.ngOnInit();

        const editorImportSpy = vi.spyOn(mockModelingEditor, 'importPatch');
        const patchData = btoa(JSON.stringify({ elements: { '1': { id: 1, name: 'john' } } }));
        const submissionPatch = new SubmissionPatch(patchData);
        comp.onReceiveSubmissionPatchFromTeam(submissionPatch);

        expect(editorImportSpy).toHaveBeenCalledWith(patchData);
    });

    it('should get inactive as soon as the due date passes the current date', async () => {
        createComponent();

        (<StudentParticipation>submission.participation).exercise!.dueDate = dayjs().add(1, 'days');
        vi.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));

        fixture.detectChanges();
        await fixture.whenStable();
        comp.participation().initializationDate = dayjs();

        expect(comp.isActive).toBe(true);

        comp.modelingExercise().dueDate = dayjs().subtract(1, 'days');

        fixture.changeDetectorRef.detectChanges();
        expect(comp.isActive).toBe(false);
    });

    it('should catch error on 403 error status', () => {
        createComponent();

        vi.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(throwError(() => ({ status: 403 })));
        const alertServiceSpy = vi.spyOn(alertService, 'error');
        fixture.detectChanges();

        expect(alertServiceSpy).toHaveBeenCalledOnce();
    });

    it('should set correct properties on modeling exercise update when saving', async () => {
        createComponent();

        vi.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));
        fixture.detectChanges();
        await fixture.whenStable();

        const updateStub = vi.spyOn(service, 'update').mockReturnValue(of(new HttpResponse({ body: submission })));
        comp.saveDiagram();
        expect(updateStub).toHaveBeenCalledOnce();
        expect(comp.submission()).toEqual(submission);
    });

    it('should set correct properties on modeling exercise create when saving', () => {
        createComponent();

        fixture.detectChanges();

        const createStub = vi.spyOn(service, 'create').mockReturnValue(of(new HttpResponse({ body: submission })));
        comp.modelingExercise.set(new ModelingExercise(UMLDiagramType.DeploymentDiagram, undefined, undefined));
        comp.modelingExercise().id = 1;
        comp.saveDiagram();
        expect(createStub).toHaveBeenCalledOnce();
        expect(comp.submission()).toEqual(submission);
    });

    it('should set correct properties on modeling exercise create when submitting', () => {
        createComponent();

        fixture.detectChanges();

        comp.submission.set(<ModelingSubmission>(<unknown>{ model: validMockModel, submitted: true, participation }));
        const createStub = vi.spyOn(service, 'create').mockReturnValue(of(new HttpResponse({ body: submission })));
        comp.modelingExercise.set(new ModelingExercise(UMLDiagramType.DeploymentDiagram, undefined, undefined));
        comp.modelingExercise().id = 1;
        comp.submitExercise();
        expect(createStub).toHaveBeenCalledOnce();
        expect(comp.submission()).toEqual(submission);
    });

    it('should catch error on submit', () => {
        createComponent();

        const modelSubmission = <ModelingSubmission>(<unknown>{ model: validMockModel, submitted: true, participation });
        comp.submission.set(modelSubmission);
        vi.spyOn(service, 'create').mockReturnValue(throwError(() => ({ status: 500 })));
        const alertServiceSpy = vi.spyOn(alertService, 'error');
        comp.modelingExercise.set(new ModelingExercise(UMLDiagramType.DeploymentDiagram, undefined, undefined));
        comp.modelingExercise().id = 1;
        comp.submitExercise();
        expect(alertServiceSpy).toHaveBeenCalledOnce();
        expect(comp.submission()).toBe(modelSubmission);
    });

    it('should set result when new result comes in from websocket', async () => {
        createComponent();

        const participationWebSocketService = TestBed.inject(ParticipationWebsocketService);

        const unreferencedFeedback = new Feedback();
        unreferencedFeedback.id = 1;
        unreferencedFeedback.detailText = 'General Feedback';
        unreferencedFeedback.credits = 5;
        unreferencedFeedback.type = FeedbackType.MANUAL_UNREFERENCED;
        const newResult = new Result();
        newResult.score = 50.0;
        newResult.assessmentType = AssessmentType.MANUAL;
        newResult.submission = submission;
        newResult.completionDate = dayjs();
        newResult.feedbacks = [unreferencedFeedback];
        const subscribeForLatestResultOfParticipationSubject = new BehaviorSubject<Result | undefined>(newResult);
        const subscribeForLatestResultOfParticipationStub = vi
            .spyOn(participationWebSocketService, 'subscribeForLatestResultOfParticipation')
            .mockReturnValue(subscribeForLatestResultOfParticipationSubject);

        submission.model = validMockModel;
        vi.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));

        fixture.detectChanges();
        await fixture.whenStable();

        expect(subscribeForLatestResultOfParticipationStub).toHaveBeenCalledOnce();
        expect(comp.assessmentResult()).toEqual(newResult);
    });

    it('preserves the live collaborative model when an automatic-submission snapshot arrives', async () => {
        createComponent();

        submission.submitted = false;
        submission.model = validMockModel;
        vi.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));
        const websocketService = TestBed.inject(WebsocketService) as unknown as MockWebsocketService;
        vi.spyOn(websocketService, 'subscribe');
        fixture.detectChanges();
        await fixture.whenStable();
        const liveModel = comp.umlModel();
        expect(liveModel).toBeDefined();
        expect(comp.modelingExercise().teamMode).toBe(true);
        const modelSubmission = <ModelingSubmission>(<unknown>{
            id: submission.id,
            model: JSON.stringify({ ...JSON.parse(validMockModel), title: 'Persisted snapshot' }),
            submitted: true,
            participation,
        });
        websocketService.emit(`/user/topic/modelingSubmission/${submission.id}`, modelSubmission);
        expect(comp.submission()).toEqual(modelSubmission);
        expect(comp.umlModel()).toBe(liveModel);
    });

    it('should set correct properties on modeling exercise update when submitting', () => {
        createComponent();

        comp.submission.set(<ModelingSubmission>(<unknown>{
            id: 1,
            model: validMockModel,
            submitted: true,
            participation,
        }));
        const updateStub = vi.spyOn(service, 'update').mockReturnValue(of(new HttpResponse({ body: submission })));
        comp.modelingExercise.set(new ModelingExercise(UMLDiagramType.DeploymentDiagram, undefined, undefined));
        comp.modelingExercise().id = 1;
        fixture.changeDetectorRef.detectChanges();
        comp.submitExercise();
        expect(updateStub).toHaveBeenCalledOnce();
        expect(comp.submission()).toEqual(submission);
    });

    it('should calculate number of elements from model', () => {
        createComponent();

        const elements = [{ id: 1 }, { id: 2 }, { id: 3 }];
        const relationships = [{ id: 4 }, { id: 5 }];
        submission.model = JSON.stringify({ elements, relationships });
        comp.submission.set(submission);
        fixture.changeDetectorRef.detectChanges();
        expect(comp.calculateNumberOfModelElements()).toBe(elements.length + relationships.length);
    });

    it('should update selected element IDs', () => {
        createComponent();

        const selectedIds = ['element1', 'element2', 'relationship1'];
        comp.onSelectedElementIdsChanged(selectedIds);
        expect(comp.selectedElementIds()).toEqual(selectedIds);
    });

    it('should not mark any feedback while nothing is selected on the diagram', () => {
        createComponent();

        const feedback = <Feedback>(<unknown>{ referenceType: 'Activity', referenceId: '5' });
        comp.onSelectedElementIdsChanged([]);
        fixture.changeDetectorRef.detectChanges();
        expect(comp.isFeedbackForSelection(feedback)).toBe(false);
    });

    it('should mark only the feedback belonging to the selected elements', () => {
        createComponent();

        const id = 'referenceId';
        const feedback = <Feedback>(<unknown>{ referenceType: 'Activity', referenceId: id });
        comp.onSelectedElementIdsChanged([id]);
        fixture.changeDetectorRef.detectChanges();
        expect(comp.isFeedbackForSelection(feedback)).toBe(true);

        comp.onSelectedElementIdsChanged(['otherId']);
        fixture.changeDetectorRef.detectChanges();
        expect(comp.isFeedbackForSelection(feedback)).toBe(false);
    });

    it('should update submission with current values', () => {
        createComponent();

        const model = <UMLModel>(<unknown>{
            elements: [
                { owner: 'ownerId1', id: 'elementId1' },
                { owner: 'ownerId2', id: 'elementId2' },
            ],
        });
        (mockModelingEditor.getCurrentModel as ReturnType<typeof vi.fn>).mockReturnValue(model as UMLModel);
        comp.explanation = 'Explanation Test';
        comp.updateSubmissionWithCurrentValues();
        expect(mockModelingEditor.getCurrentModel).toHaveBeenCalledTimes(2);
        expect(comp.hasElements()).toBe(true);
        expect(comp.submission()).toBeDefined();
        expect(comp.submission().model).toBe(JSON.stringify(model));
        expect(comp.submission().explanationText).toBe('Explanation Test');
    });

    it('should display the feedback text properly', () => {
        createComponent();

        const gradingInstruction = {
            id: 1,
            credits: 1,
            gradingScale: 'scale',
            instructionDescription: 'description',
            feedback: 'instruction feedback',
            usageCount: 0,
        } as GradingInstruction;
        const feedback = {
            id: 1,
            text: 'feedback1',
            credits: 1.5,
        } as Feedback;

        let textToBeDisplayed = comp.buildFeedbackTextForReview(feedback);
        expect(textToBeDisplayed).toBe(feedback.text);

        feedback.gradingInstruction = gradingInstruction;
        textToBeDisplayed = comp.buildFeedbackTextForReview(feedback);
        expect(textToBeDisplayed).toEqual(gradingInstruction.feedback + '<br>' + feedback.text);
    });

    it('should deactivate return true when there are unsaved changes', () => {
        createComponent();

        const currentModel = <UMLModel>(<unknown>{
            elements: [
                { owner: 'ownerId1', id: 'elementId1' },
                { owner: 'ownerId2', id: 'elementId2' },
            ],
            version: 'version',
        });
        const unsavedModel = <UMLModel>(<unknown>{
            elements: [{ owner: 'ownerId1', id: 'elementId1' }],
            version: 'version',
        });

        (mockModelingEditor.getCurrentModel as ReturnType<typeof vi.fn>).mockReturnValue(currentModel as UMLModel);
        (mockModelingEditor as any).isApollonEditorMounted = true;
        comp.submission.set(submission);
        comp.submission().model = JSON.stringify(unsavedModel);

        const canDeactivate = comp.canDeactivate();

        expect(mockModelingEditor.getCurrentModel).toHaveBeenCalledOnce();
        expect(canDeactivate).toBe(false);
    });

    it('should set isChanged property to false after saving', () => {
        createComponent();

        comp.submission.set(<ModelingSubmission>(<unknown>{
            id: 1,
            model: validMockModel,
            submitted: true,
            participation,
        }));
        comp.isChanged.set(true);
        vi.spyOn(service, 'update').mockReturnValue(of(new HttpResponse({ body: submission })));
        comp.modelingExercise.set(new ModelingExercise(UMLDiagramType.DeploymentDiagram, undefined, undefined));
        comp.modelingExercise().id = 1;
        fixture.changeDetectorRef.detectChanges();
        comp.saveDiagram();
        expect(comp.isChanged()).toBe(false);
    });

    it('should mark the subsequent feedback', () => {
        createComponent();

        comp.assessmentResult.set(new Result());

        const gradingInstruction = {
            id: 1,
            credits: 1,
            gradingScale: 'scale',
            instructionDescription: 'description',
            feedback: 'instruction feedback',
            usageCount: 1,
        } as GradingInstruction;

        comp.assessmentResult()!.feedbacks = [
            {
                id: 1,
                detailText: 'feedback1',
                credits: 1,
                gradingInstruction,
                type: FeedbackType.AUTOMATIC,
            } as Feedback,
            {
                id: 2,
                detailText: 'feedback2',
                credits: 1,
                gradingInstruction,
                type: FeedbackType.MANUAL,
                reference: 'asdf',
            } as Feedback,
        ];

        const unreferencedFeedback = comp.unreferencedFeedback;
        const referencedFeedback = comp.referencedFeedback;

        expect(unreferencedFeedback).toBeDefined();
        expect(unreferencedFeedback).toHaveLength(1);
        expect(unreferencedFeedback![0].isSubsequent).toBeUndefined();
        expect(referencedFeedback).toBeDefined();
        expect(referencedFeedback).toHaveLength(1);
        expect(referencedFeedback![0].isSubsequent).toBe(true);
    });

    it('should be set up with input values if present instead of loading new values from server', () => {
        createComponent();

        const getDataForFileUploadEditorSpy = vi.spyOn(service, 'getLatestSubmissionForModelingEditor');
        const modelingSubmission = submission;
        modelingSubmission.model = JSON.stringify({
            version: '3.0.0',
            type: 'ClassDiagram',
            size: { width: 100, height: 100 },
            interactive: { elements: {}, relationships: {} },
            elements: {
                element1: {
                    id: 'element1',
                    type: 'Class',
                    name: 'SomeClass',
                    bounds: { x: 0, y: 0, width: 100, height: 100 },
                },
            },
            relationships: {},
            assessments: {},
        });
        fixture.componentRef.setInput('inputExercise', participation.exercise);
        fixture.componentRef.setInput('inputSubmission', modelingSubmission);
        fixture.componentRef.setInput('inputParticipation', participation);

        fixture.changeDetectorRef.detectChanges();

        expect(comp.modelingExercise()).toEqual(participation.exercise);
        expect(comp.submission()).toEqual(modelingSubmission);
        expect(comp.participation()).toEqual(participation);
        expect(comp.umlModel()).toBeTruthy();
        expect(comp.hasElements()).toBe(true);

        expect(getDataForFileUploadEditorSpy).not.toHaveBeenCalled();
    });
});
