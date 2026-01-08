import { Component, EventEmitter, input, output } from '@angular/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ChangeDetectorRef, DebugElement } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { UMLDiagramType, UMLElement, UMLModel } from '@ls1intum/apollon';
import { TranslateService } from '@ngx-translate/core';
import { ComplaintsStudentViewComponent } from 'app/assessment/overview/complaints-for-students/complaints-student-view.component';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { AccountService } from 'app/core/auth/account.service';
import { ParticipationWebsocketService } from 'app/core/course/shared/services/participation-websocket.service';
import { AdditionalFeedbackComponent } from 'app/exercise/additional-feedback/additional-feedback.component';
import { HeaderParticipationPageComponent } from 'app/exercise/exercise-headers/participation-page/header-participation-page.component';
import { RatingComponent } from 'app/exercise/rating/rating.component';
import { ResultService } from 'app/exercise/result/result.service';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { TeamSubmissionSyncComponent } from 'app/exercise/team-submission-sync/team-submission-sync.component';
import { TeamParticipateInfoBoxComponent } from 'app/exercise/team/team-participate/team-participate-info-box.component';
import { ModelingAssessmentComponent } from 'app/modeling/manage/assess/modeling-assessment.component';
import { routes } from 'app/modeling/overview/modeling-participation.route';
import { ModelingSubmissionComponent } from 'app/modeling/overview/modeling-submission/modeling-submission.component';
import { ModelingSubmissionService } from 'app/modeling/overview/modeling-submission/modeling-submission.service';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { FullscreenComponent } from 'app/modeling/shared/fullscreen/fullscreen.component';
import { ModelingEditorComponent } from 'app/modeling/shared/modeling-editor/modeling-editor.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { ResizeableContainerComponent } from 'app/shared/resizeable-container/resizeable-container.component';
import { AlertService } from 'app/shared/service/alert.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import dayjs from 'dayjs/esm';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockComplaintService } from 'test/helpers/mocks/service/mock-complaint.service';
import { MockParticipationWebsocketService } from 'test/helpers/mocks/service/mock-participation-websocket.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';

// Stub component that provides all needed functionality for ModelingEditorComponent
@Component({
    selector: 'jhi-modeling-editor',
    template: '',
})
class StubModelingEditorComponent {
    umlModel = input<UMLModel>();
    diagramType = input<UMLDiagramType>();
    readOnly = input<boolean>(false);
    resizeOptions = input<{ verticalResize?: boolean }>({});
    showHelpButton = input<boolean>(true);
    withExplanation = input<boolean>(false);
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
}

describe('ModelingSubmissionComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: ModelingSubmissionComponent;
    let fixture: ComponentFixture<ModelingSubmissionComponent>;
    let debugElement: DebugElement;
    let service: ModelingSubmissionService;
    let alertService: AlertService;

    const route = { params: of({ courseId: 5, exerciseId: 22, participationId: 123 }) } as any as ActivatedRoute;
    const participation = new StudentParticipation();
    participation.exercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
    participation.id = 1;
    const submission = <ModelingSubmission>(<unknown>{ id: 20, submitted: true, participation });
    const result = { id: 1 } as Result;

    const originalConsoleError = console.error;

    // Create a mock modeling editor component for use in tests
    let mockModelingEditor: Partial<ModelingEditorComponent>;

    function createModelingSubmissionComponent(routeOverride?: ActivatedRoute) {
        if (routeOverride) {
            TestBed.overrideProvider(ActivatedRoute, { useValue: routeOverride });
        }

        // Override the component to use stubs/mocks instead of real components
        TestBed.overrideComponent(ModelingSubmissionComponent, {
            remove: {
                imports: [ModelingEditorComponent, HeaderParticipationPageComponent, TeamParticipateInfoBoxComponent],
            },
            add: {
                imports: [StubModelingEditorComponent, MockComponent(HeaderParticipationPageComponent), MockComponent(TeamParticipateInfoBoxComponent)],
            },
        });

        fixture = TestBed.createComponent(ModelingSubmissionComponent);
        comp = fixture.componentInstance;
        debugElement = fixture.debugElement;
        service = TestBed.inject(ModelingSubmissionService);
        alertService = TestBed.inject(AlertService);

        // Create a mock for the modeling editor viewChild signal
        // Return a model with elements so that submit validation passes
        mockModelingEditor = {
            getCurrentModel: vi.fn().mockReturnValue({
                elements: { element1: { id: 'element1', type: 'Class' } },
                relationships: {},
            } as UMLModel),
            isApollonEditorMounted: false,
            onModelPatch: new EventEmitter(),
            importPatch: vi.fn(),
        };

        // Mock the viewChild signal to return our mock editor
        vi.spyOn(comp, 'modelingEditor').mockReturnValue(mockModelingEditor as ModelingEditorComponent);
    }

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                RouterModule.forRoot([routes[0]]),
                ModelingSubmissionComponent,
                StubModelingEditorComponent,
                MockPipe(HtmlForMarkdownPipe),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisTimeAgoPipe),
                MockComponent(HeaderParticipationPageComponent),
                MockComponent(ButtonComponent),
                MockComponent(ResizeableContainerComponent),
                MockComponent(TeamParticipateInfoBoxComponent),
                MockComponent(TeamSubmissionSyncComponent),
                MockComponent(ModelingAssessmentComponent),
                MockComponent(FullscreenComponent),
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
                ResultService,
                provideHttpClientTesting(),
                provideHttpClient(),
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

        // Reset shared submission state to prevent test pollution
        submission.submitted = true;
        submission.model = undefined;
        submission.participation!.initializationDate = undefined;
        (<StudentParticipation>submission.participation).exercise!.dueDate = undefined;

        // Ensure all subscriptions are cleaned up
        if (comp) {
            try {
                comp.ngOnDestroy();
            } catch (_) {
                // Ignore errors during cleanup
            }
        }
        TestBed.resetTestingModule();
    });

    it('should initialize without submissionId (Standard Mode)', () => {
        createModelingSubmissionComponent();

        // Mock data
        const modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
        modelingExercise.teamMode = false;
        const participation = new StudentParticipation();
        participation.exercise = modelingExercise;
        participation.id = 1;
        const submission = new ModelingSubmission();
        submission.id = 20;
        submission.submitted = true;
        submission.participation = participation;

        // Mock service calls
        const getLatestSubmissionSpy = vi.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));
        const getSubmissionsWithResultsSpy = vi.spyOn(service, 'getSubmissionsWithResultsForParticipation');

        // Initialize component
        comp.ngOnInit();

        // Assertions
        expect(comp.isFeedbackView).toBe(false);
        expect(getLatestSubmissionSpy).toHaveBeenCalledOnce();
        expect(getSubmissionsWithResultsSpy).not.toHaveBeenCalled();
        expect(comp.submission).toEqual(submission);
        expect(comp.modelingExercise).toEqual(modelingExercise);
        expect(comp.participation).toEqual(participation);
    });

    it('should initialize with submissionId (Feedback View Mode)', () => {
        // Mock route parameters with submissionId
        const route = {
            params: of({ courseId: 5, exerciseId: 22, participationId: 1, submissionId: 20 }),
        } as any as ActivatedRoute;

        createModelingSubmissionComponent(route);

        // Mock data
        const modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
        modelingExercise.dueDate = dayjs().add(1, 'days');
        modelingExercise.maxPoints = 20;
        modelingExercise.teamMode = false;
        const participation = new StudentParticipation();
        participation.exercise = modelingExercise;
        participation.id = 1;
        const submission = new ModelingSubmission();
        submission.id = 20;
        submission.submitted = true;
        submission.participation = participation;
        const result = {
            id: 1,
            completionDate: dayjs(),
            assessmentType: AssessmentType.AUTOMATIC_ATHENA,
            successful: true,
            score: 10,
        } as Result;
        submission.results = [result];
        submission.latestResult = result;

        // Mock service calls
        const getSubmissionsWithResultsSpy = vi.spyOn(service, 'getSubmissionsWithResultsForParticipation').mockReturnValue(of([submission]));
        const getLatestSubmissionSpy = vi.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));

        // Initialize component
        comp.ngOnInit();

        // Assertions
        expect(comp.isFeedbackView).toBe(true);
        expect(comp.submissionId).toBe(20);
        expect(getSubmissionsWithResultsSpy).toHaveBeenCalledOnce();
        expect(getLatestSubmissionSpy).toHaveBeenCalledOnce();
        expect(comp.sortedSubmissionHistory).toEqual([submission]);
        expect(comp.sortedResultHistory).toEqual([result]);
        expect(comp.submission).toEqual(submission);
    });

    it('should allow to submit when exercise due date not set', () => {
        createModelingSubmissionComponent();

        // GIVEN
        vi.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));

        // WHEN
        comp.isLoading = false;
        fixture.changeDetectorRef.detectChanges();

        expect(debugElement.query(By.css('div'))).not.toBeNull();

        const submitButton = debugElement.query(By.css('jhi-button'));
        expect(submitButton).not.toBeNull();
        expect(submitButton.componentInstance.disabled).toBe(false);
        expect(comp.isActive).toBe(true);
    });

    it('should not allow to submit after the due date if the initialization date is before the due date', async () => {
        createModelingSubmissionComponent();

        submission.participation!.initializationDate = dayjs().subtract(2, 'days');
        (<StudentParticipation>submission.participation).exercise!.dueDate = dayjs().subtract(1, 'days');
        vi.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));

        fixture.detectChanges();
        await fixture.whenStable();

        const submitButton = debugElement.query(By.css('jhi-button'));
        expect(submitButton).not.toBeNull();
        expect(submitButton.componentInstance.disabled).toBe(true);
    });

    it('should allow to submit after the due date if the initialization date is after the due date and not submitted', async () => {
        createModelingSubmissionComponent();

        submission.participation!.initializationDate = dayjs().add(1, 'days');
        (<StudentParticipation>submission.participation).exercise!.dueDate = dayjs();
        submission.submitted = false;
        vi.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));

        fixture.detectChanges();
        await fixture.whenStable();

        expect(comp.isLate).toBe(true);
        const submitButton = debugElement.query(By.css('jhi-button'));
        expect(submitButton).not.toBeNull();
        expect(submitButton.componentInstance.disabled).toBe(false);
        submission.submitted = true;
    });

    it('should not allow to submit if there is a result and no due date', async () => {
        createModelingSubmissionComponent();

        comp.result = result;
        vi.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));

        fixture.detectChanges();
        await fixture.whenStable();

        const submitButton = debugElement.query(By.css('jhi-button'));
        expect(submitButton).not.toBeNull();
        expect(submitButton.componentInstance.disabled).toBe(true);
    });

    it('should get inactive as soon as the due date passes the current date', async () => {
        createModelingSubmissionComponent();

        (<StudentParticipation>submission.participation).exercise!.dueDate = dayjs().add(1, 'days');
        vi.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));

        fixture.detectChanges();
        await fixture.whenStable();
        comp.participation.initializationDate = dayjs();

        expect(comp.isActive).toBe(true);

        comp.modelingExercise.dueDate = dayjs().subtract(1, 'days');

        fixture.changeDetectorRef.detectChanges();
        expect(comp.isActive).toBe(false);
    });

    it('should catch error on 403 error status', () => {
        createModelingSubmissionComponent();

        vi.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(throwError(() => ({ status: 403 })));
        const alertServiceSpy = vi.spyOn(alertService, 'error');
        fixture.detectChanges();

        expect(alertServiceSpy).toHaveBeenCalledOnce();
    });

    it('should set correct properties on modeling exercise update when saving', async () => {
        createModelingSubmissionComponent();

        vi.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));
        fixture.detectChanges();
        await fixture.whenStable();

        const updateStub = vi.spyOn(service, 'update').mockReturnValue(of(new HttpResponse({ body: submission })));
        comp.saveDiagram();
        expect(updateStub).toHaveBeenCalledOnce();
        expect(comp.submission).toEqual(submission);
    });

    it('should set correct properties on modeling exercise create when saving', () => {
        createModelingSubmissionComponent();

        fixture.detectChanges();

        const createStub = vi.spyOn(service, 'create').mockReturnValue(of(new HttpResponse({ body: submission })));
        comp.modelingExercise = new ModelingExercise(UMLDiagramType.DeploymentDiagram, undefined, undefined);
        comp.modelingExercise.id = 1;
        comp.saveDiagram();
        expect(createStub).toHaveBeenCalledOnce();
        expect(comp.submission).toEqual(submission);
    });

    it('should set correct properties on modeling exercise create when submitting', () => {
        createModelingSubmissionComponent();

        fixture.detectChanges();

        comp.submission = <ModelingSubmission>(<unknown>{
            model: '{"elements": [{"id": 1}]}',
            submitted: true,
            participation,
        });
        const createStub = vi.spyOn(service, 'create').mockReturnValue(of(new HttpResponse({ body: submission })));
        comp.modelingExercise = new ModelingExercise(UMLDiagramType.DeploymentDiagram, undefined, undefined);
        comp.modelingExercise.id = 1;
        comp.submit();
        expect(createStub).toHaveBeenCalledOnce();
        expect(comp.submission).toEqual(submission);
    });

    it('should catch error on submit', () => {
        createModelingSubmissionComponent();

        const modelSubmission = <ModelingSubmission>(<unknown>{
            model: '{"elements": [{"id": 1}]}',
            submitted: true,
            participation,
        });
        comp.submission = modelSubmission;
        vi.spyOn(service, 'create').mockReturnValue(throwError(() => ({ status: 500 })));
        const alertServiceSpy = vi.spyOn(alertService, 'error');
        comp.modelingExercise = new ModelingExercise(UMLDiagramType.DeploymentDiagram, undefined, undefined);
        comp.modelingExercise.id = 1;
        comp.submit();
        expect(alertServiceSpy).toHaveBeenCalledOnce();
        expect(comp.submission).toBe(modelSubmission);
    });

    it('should handle failed Athena assessment appropriately', async () => {
        // Set up route with participationId
        const routeOverride = {
            params: of({ courseId: 5, exerciseId: 22, participationId: 123 }),
        } as any as ActivatedRoute;
        createModelingSubmissionComponent(routeOverride);

        // IMPORTANT: Set up mocks BEFORE ngOnInit is triggered
        const participationWebSocketService = TestBed.inject(ParticipationWebsocketService);
        const alertServiceSpy = vi.spyOn(alertService, 'error');

        // Create initial manual result
        const manualResult = new Result();
        manualResult.score = 50.0;
        manualResult.assessmentType = AssessmentType.MANUAL;
        manualResult.submission = submission;
        manualResult.completionDate = dayjs();
        manualResult.feedbacks = [];

        // Create a failed Athena result
        const failedAthenaResult = new Result();
        failedAthenaResult.assessmentType = AssessmentType.AUTOMATIC_ATHENA;
        failedAthenaResult.submission = submission;
        failedAthenaResult.completionDate = undefined;
        failedAthenaResult.successful = false;
        failedAthenaResult.feedbacks = [];

        const resultSubject = new BehaviorSubject<Result | undefined>(manualResult);
        const subscribeForLatestResultOfParticipationStub = vi.spyOn(participationWebSocketService, 'subscribeForLatestResultOfParticipation').mockReturnValue(resultSubject);

        // Set up the model data and mock service call
        submission.model = '{"elements": [{"id": 1}]}';
        vi.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));

        // Initialize component (ngOnInit is called here)
        fixture.detectChanges();
        await fixture.whenStable();

        expect(subscribeForLatestResultOfParticipationStub).toHaveBeenCalledOnce();

        // Clear any previous calls
        alertServiceSpy.mockClear();

        // Emit failed Athena result
        resultSubject.next(failedAthenaResult);
        fixture.changeDetectorRef.detectChanges();

        // Verify error was shown
        expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.exercise.athenaFeedbackFailed');
        expect(comp.isGeneratingFeedback).toBe(false);
    });

    it('should handle Athena assessment results separately from manual assessments', async () => {
        createModelingSubmissionComponent();

        // IMPORTANT: Set up mocks BEFORE ngOnInit is triggered
        const participationWebSocketService = TestBed.inject(ParticipationWebsocketService);
        const alertServiceInfoSpy = vi.spyOn(alertService, 'info');
        const alterServiceSuccessSpy = vi.spyOn(alertService, 'success');

        // Create an Athena result
        const athenaResult = new Result();
        athenaResult.score = 75.0;
        athenaResult.assessmentType = AssessmentType.AUTOMATIC_ATHENA;
        athenaResult.submission = submission;
        athenaResult.completionDate = dayjs();
        athenaResult.successful = true;
        athenaResult.feedbacks = [];

        // Create manual result
        const manualResult = new Result();
        manualResult.score = 50.0;
        manualResult.assessmentType = AssessmentType.MANUAL;
        manualResult.submission = submission;
        manualResult.completionDate = dayjs();
        manualResult.feedbacks = [];

        // Setup initial manual result in subject
        const resultSubject = new BehaviorSubject<Result | undefined>(manualResult);
        const subscribeForLatestResultOfParticipationStub = vi.spyOn(participationWebSocketService, 'subscribeForLatestResultOfParticipation').mockReturnValue(resultSubject);

        // Set up model and mock service call
        submission.model = '{"elements": [{"id": 1}]}';
        vi.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));

        // Initialize component and verify manual result
        fixture.detectChanges();
        await fixture.whenStable();

        expect(subscribeForLatestResultOfParticipationStub).toHaveBeenCalledOnce();
        expect(comp.assessmentResult).toEqual(manualResult);
        expect(alertServiceInfoSpy).toHaveBeenCalledWith('artemisApp.modelingEditor.newAssessment');

        // Emit Athena result
        resultSubject.next(athenaResult);
        fixture.changeDetectorRef.detectChanges();

        // Verify Athena result handling
        expect(comp.assessmentResult).toEqual(athenaResult);
        expect(alterServiceSuccessSpy).toHaveBeenCalledWith('artemisApp.exercise.athenaFeedbackSuccessful');
    });

    it('should set result when new result comes in from websocket', async () => {
        createModelingSubmissionComponent();

        // IMPORTANT: Set up mocks BEFORE ngOnInit is triggered
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

        // Set up model and mock service call
        submission.model = '{"elements": [{"id": 1}]}';
        vi.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));

        fixture.detectChanges();
        await fixture.whenStable();

        expect(subscribeForLatestResultOfParticipationStub).toHaveBeenCalledOnce();
        expect(comp.assessmentResult).toEqual(newResult);
    });

    it('should update submission when new submission comes in from websocket', () => {
        createModelingSubmissionComponent();

        submission.submitted = false;
        vi.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));
        // @ts-ignore
        const websocketService = TestBed.inject(WebsocketService) as MockWebsocketService;
        vi.spyOn(websocketService, 'subscribe');
        const modelSubmission = <ModelingSubmission>(<unknown>{
            id: submission.id,
            model: '{"elements": [{"id": 1}]}',
            submitted: true,
            participation,
        });
        fixture.detectChanges();
        websocketService.emit(`/user/topic/modelingSubmission/${submission.id}`, modelSubmission);
        expect(comp.submission).toEqual(modelSubmission);
    });

    it('should not process results without completionDate except for failed Athena results', () => {
        createModelingSubmissionComponent();

        submission.model = '{"elements": [{"id": 1}]}';
        vi.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));
        const participationWebSocketService = TestBed.inject(ParticipationWebsocketService);

        // Create an incomplete result
        const incompleteResult = new Result();
        incompleteResult.assessmentType = AssessmentType.MANUAL;
        incompleteResult.submission = submission;
        incompleteResult.completionDate = undefined;

        const resultSubject = new BehaviorSubject<Result | undefined>(incompleteResult);
        vi.spyOn(participationWebSocketService, 'subscribeForLatestResultOfParticipation').mockReturnValue(resultSubject);

        // Initialize component
        fixture.detectChanges();

        // Verify incomplete result is not processed
        expect(comp.assessmentResult).toBeUndefined();
    });

    it('should set correct properties on modeling exercise update when submitting', () => {
        createModelingSubmissionComponent();

        comp.submission = <ModelingSubmission>(<unknown>{
            id: 1,
            model: '{"elements": [{"id": 1}]}',
            submitted: true,
            participation,
        });
        const updateStub = vi.spyOn(service, 'update').mockReturnValue(of(new HttpResponse({ body: submission })));
        comp.modelingExercise = new ModelingExercise(UMLDiagramType.DeploymentDiagram, undefined, undefined);
        comp.modelingExercise.id = 1;
        fixture.detectChanges();
        comp.submit();
        expect(updateStub).toHaveBeenCalledOnce();
        expect(comp.submission).toEqual(submission);
    });

    it('should calculate number of elements from model', () => {
        createModelingSubmissionComponent();

        const elements = [{ id: 1 }, { id: 2 }, { id: 3 }];
        const relationships = [{ id: 4 }, { id: 5 }];
        submission.model = JSON.stringify({ elements, relationships });
        comp.submission = submission;
        fixture.changeDetectorRef.detectChanges();
        expect(comp.calculateNumberOfModelElements()).toBe(elements.length + relationships.length);
    });

    it('should update selected entities with given elements', () => {
        createModelingSubmissionComponent();

        const selection = {
            elements: {
                ownerId1: true,
                ownerId2: true,
            },
            relationships: {
                relationShip1: true,
                relationShip2: true,
            },
        };
        comp.umlModel = <UMLModel>(<unknown>{
            elements: {
                elementId1: <UMLElement>(<unknown>{
                    owner: 'ownerId1',
                    id: 'elementId1',
                }),
                elementId2: <UMLElement>(<unknown>{
                    owner: 'ownerId2',
                    id: 'elementId2',
                }),
            },
        });
        fixture.changeDetectorRef.detectChanges();
        comp.onSelectionChanged(selection);
        expect(comp.selectedRelationships).toEqual(['relationShip1', 'relationShip2']);
        expect(comp.selectedEntities).toEqual(['ownerId1', 'ownerId2', 'elementId1', 'elementId2']);
    });

    it('should shouldBeDisplayed return true if no selectedEntities and selectedRelationships', () => {
        createModelingSubmissionComponent();

        const feedback = <Feedback>(<unknown>{ referenceType: 'Activity', referenceId: '5' });
        comp.selectedEntities = [];
        comp.selectedRelationships = [];
        fixture.changeDetectorRef.detectChanges();
        expect(comp.shouldBeDisplayed(feedback)).toBe(true);
        comp.selectedEntities = ['3'];
        fixture.changeDetectorRef.detectChanges();
        expect(comp.shouldBeDisplayed(feedback)).toBe(false);
    });

    it('should shouldBeDisplayed return true if feedback reference is in selectedEntities or selectedRelationships', () => {
        createModelingSubmissionComponent();

        const id = 'referenceId';
        const feedback = <Feedback>(<unknown>{ referenceType: 'Activity', referenceId: id });
        comp.selectedEntities = [id];
        comp.selectedRelationships = [];
        fixture.changeDetectorRef.detectChanges();
        expect(comp.shouldBeDisplayed(feedback)).toBe(true);
        comp.selectedEntities = [];
        comp.selectedRelationships = [id];
        fixture.changeDetectorRef.detectChanges();
        expect(comp.shouldBeDisplayed(feedback)).toBe(false);
    });

    it('should update submission with current values', () => {
        createModelingSubmissionComponent();

        const model = <UMLModel>(<unknown>{
            elements: [<UMLElement>(<unknown>{
                    owner: 'ownerId1',
                    id: 'elementId1',
                }), <UMLElement>(<unknown>{ owner: 'ownerId2', id: 'elementId2' })],
        });
        (mockModelingEditor.getCurrentModel as ReturnType<typeof vi.fn>).mockReturnValue(model as UMLModel);
        comp.explanation = 'Explanation Test';
        comp.updateSubmissionWithCurrentValues();
        expect(mockModelingEditor.getCurrentModel).toHaveBeenCalledTimes(2);
        expect(comp.hasElements).toBe(true);
        expect(comp.submission).toBeDefined();
        expect(comp.submission.model).toBe(JSON.stringify(model));
        expect(comp.submission.explanationText).toBe('Explanation Test');
    });

    it('should display the feedback text properly', () => {
        createModelingSubmissionComponent();

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
        createModelingSubmissionComponent();

        const currentModel = <UMLModel>(<unknown>{
            elements: [<UMLElement>(<unknown>{
                    owner: 'ownerId1',
                    id: 'elementId1',
                }), <UMLElement>(<unknown>{ owner: 'ownerId2', id: 'elementId2' })],
            version: 'version',
        });
        const unsavedModel = <UMLModel>(<unknown>{
            elements: [<UMLElement>(<unknown>{ owner: 'ownerId1', id: 'elementId1' })],
            version: 'version',
        });

        (mockModelingEditor.getCurrentModel as ReturnType<typeof vi.fn>).mockReturnValue(currentModel as UMLModel);
        mockModelingEditor.isApollonEditorMounted = true;
        comp.submission = submission;
        comp.submission.model = JSON.stringify(unsavedModel);

        const canDeactivate = comp.canDeactivate();

        expect(mockModelingEditor.getCurrentModel).toHaveBeenCalledOnce();
        expect(canDeactivate).toBe(false);
    });

    it('should set isChanged property to false after saving', () => {
        createModelingSubmissionComponent();

        comp.submission = <ModelingSubmission>(<unknown>{
            id: 1,
            model: '{"elements": [{"id": 1}]}',
            submitted: true,
            participation,
        });
        comp.isChanged = true;
        vi.spyOn(service, 'update').mockReturnValue(of(new HttpResponse({ body: submission })));
        comp.modelingExercise = new ModelingExercise(UMLDiagramType.DeploymentDiagram, undefined, undefined);
        comp.modelingExercise.id = 1;
        fixture.detectChanges();
        comp.saveDiagram();
        expect(comp.isChanged).toBe(false);
    });

    it('should mark the subsequent feedback', () => {
        createModelingSubmissionComponent();

        comp.assessmentResult = new Result();

        const gradingInstruction = {
            id: 1,
            credits: 1,
            gradingScale: 'scale',
            instructionDescription: 'description',
            feedback: 'instruction feedback',
            usageCount: 1,
        } as GradingInstruction;

        comp.assessmentResult.feedbacks = [
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
        createModelingSubmissionComponent();

        // @ts-ignore method is private
        const setUpComponentWithInputValuesSpy = vi.spyOn(comp, 'setupComponentWithInputValues');
        const getDataForFileUploadEditorSpy = vi.spyOn(service, 'getLatestSubmissionForModelingEditor');
        const modelingSubmission = submission;
        modelingSubmission.model = JSON.stringify({
            elements: [
                {
                    content: 'some element',
                },
            ],
        });

        fixture.componentRef.setInput('inputExercise', participation.exercise);
        fixture.componentRef.setInput('inputSubmission', modelingSubmission);
        fixture.componentRef.setInput('inputParticipation', participation);

        fixture.detectChanges();

        expect(setUpComponentWithInputValuesSpy).toHaveBeenCalledOnce();
        expect(comp.modelingExercise).toEqual(participation.exercise);
        expect(comp.submission).toEqual(modelingSubmission);
        expect(comp.participation).toEqual(participation);
        expect(comp.umlModel).toBeTruthy();
        expect(comp.hasElements).toBe(true);

        // should not fetch additional information from server, reason for input values!
        expect(getDataForFileUploadEditorSpy).not.toHaveBeenCalled();
    });

    it('should fetch and sort submission history correctly', () => {
        // Create route with participationId
        const route = {
            params: of({ courseId: 5, exerciseId: 22, participationId: 123, submissionId: 20 }),
        } as any as ActivatedRoute;
        createModelingSubmissionComponent(route);

        // Helper function to create a Result
        const createResult = (id: number, dateStr: string): Result => {
            const result = new Result();
            result.id = id;
            result.completionDate = dayjs(dateStr);
            return result;
        };

        // Helper function to create a Submission
        const createSubmission = (id: number, results: Result[]): ModelingSubmission => {
            const submission = new ModelingSubmission();
            submission.id = id;
            submission.results = results;
            submission.participation = participation;
            return submission;
        };

        // Test data for dates and results
        const resultData = [
            { id: 1, date: '2024-01-01T10:00:00' }, // Monday 10 AM
            { id: 2, date: '2024-01-03T09:15:00' }, // Wednesday 9:15 AM
            { id: 3, date: '2024-01-04T16:45:00' }, // Thursday 4:45 PM
            { id: 4, date: '2024-01-02T14:30:00' }, // Tuesday 2:30 PM
            { id: 5, date: '2024-01-05T11:20:00' }, // Friday 11:20 AM
        ];

        // Create results
        const results = resultData.reduce(
            (acc, { id, date }) => {
                acc[id] = createResult(id, date);
                return acc;
            },
            {} as Record<number, Result>,
        );

        // Create submissions with their results
        const submissions = [
            createSubmission(0, [results[1], results[2]]), // Latest is date3 (Wed 9:15 AM)
            createSubmission(1, [results[3], results[4]]), // Latest is date4 (Thu 4:45 PM)
            createSubmission(2, [results[5]]), // Latest is date5 (Fri 11:20 AM)
        ];

        const expectedSortedSubmissions = [submissions[0], submissions[1], submissions[2]];
        const expectedSortedResults = [results[2], results[3], results[5]];

        // Mock the service call
        const submissionsWithResultsSpy = vi.spyOn(service, 'getSubmissionsWithResultsForParticipation').mockReturnValue(of([submissions[2], submissions[1], submissions[0]]));

        // Initialize the component
        comp.ngOnInit();

        // Verify service call
        expect(submissionsWithResultsSpy).toHaveBeenCalledWith(123);
        // Verify sorted submission and result history
        expect(comp.sortedSubmissionHistory).toEqual(expectedSortedSubmissions);
        comp.sortedResultHistory.forEach((result, index) => {
            expect(result?.id).toBe(expectedSortedResults[index].id);
            expect(result?.completionDate?.isSame(expectedSortedResults[index].completionDate)).toBe(true);
        });
    });
});
