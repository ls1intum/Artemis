import { Component, EventEmitter, input, output } from '@angular/core';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ChangeDetectorRef } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Params, RouterModule } from '@angular/router';
import { type CollaborationUser, UMLDiagramType, UMLModel } from '@tumaet/apollon';
import { TranslateService } from '@ngx-translate/core';
import { ComplaintsStudentViewComponent } from 'app/assessment/overview/complaints-for-students/complaints-student-view.component';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { AccountService } from 'app/core/auth/account.service';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { RatingComponent } from 'app/exercise/rating/rating.component';
import { ResultService } from 'app/exercise/result/result.service';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { TeamSubmissionSyncComponent } from 'app/exercise/team-submission-sync/team-submission-sync.component';
import { ModelingAssessmentComponent } from 'app/modeling/manage/assess/modeling-assessment.component';
import { routes } from 'app/modeling/overview/modeling-participation.route';
import { ModelingSubmissionComponent } from 'app/modeling/overview/modeling-submission/modeling-submission.component';
import { ModelingSubmissionService } from 'app/modeling/overview/modeling-submission/modeling-submission.service';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { ModelingEditorComponent } from 'app/modeling/shared/modeling-editor/modeling-editor.component';
import { UnifiedFeedbackComponent } from 'app/shared/components/unified-feedback/unified-feedback.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/foundation/pipes/artemis-time-ago.pipe';
import { ResizeableContainerComponent } from 'app/shared-ui/resizeable-container/resizeable-container.component';
import { AlertService } from 'app/foundation/service/alert.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { faCheck, faTriangleExclamation, faXmark } from '@fortawesome/free-solid-svg-icons';
import { captureException } from '@sentry/angular';
import { User } from 'app/account/user/user.model';
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
    collaborationEnabled = input(false);
    collaborationUser = input<CollaborationUser | undefined>(undefined);
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

vi.mock('@sentry/angular', async (importOriginal) => ({
    ...(await importOriginal<typeof import('@sentry/angular')>()),
    captureException: vi.fn(),
}));

describe('ModelingSubmissionComponent', () => {
    let comp: ModelingSubmissionComponent;
    let fixture: ComponentFixture<ModelingSubmissionComponent>;
    let service: ModelingSubmissionService;
    let alertService: AlertService;

    const route = { params: of({ courseId: 5, exerciseId: 22, participationId: 123 }) } as any as ActivatedRoute;
    const participation = new StudentParticipation();
    participation.exercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
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

    function createModelingSubmissionComponent(routeOverride?: ActivatedRoute) {
        if (routeOverride) {
            TestBed.overrideProvider(ActivatedRoute, { useValue: routeOverride });
        }

        TestBed.overrideComponent(ModelingSubmissionComponent, {
            remove: {
                imports: [ModelingEditorComponent, RatingComponent, ComplaintsStudentViewComponent],
            },
            add: {
                imports: [StubModelingEditorComponent, MockComponent(RatingComponent), MockComponent(ComplaintsStudentViewComponent)],
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
        };

        vi.spyOn(comp, 'modelingEditor').mockReturnValue(mockModelingEditor as ModelingEditorComponent);
    }

    beforeEach(() => {
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
                MockComponent(UnifiedFeedbackComponent),
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
        TestBed.resetTestingModule();
    });

    it('should initialize without submissionId (Standard Mode)', () => {
        createModelingSubmissionComponent();

        const modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
        modelingExercise.teamMode = false;
        const participation = new StudentParticipation();
        participation.exercise = modelingExercise;
        participation.id = 1;
        const submission = new ModelingSubmission();
        submission.id = 20;
        submission.submitted = true;
        submission.participation = participation;

        const getLatestSubmissionSpy = vi.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));
        const getSubmissionsWithResultsSpy = vi.spyOn(service, 'getSubmissionsWithResultsForParticipation');

        comp.ngOnInit();

        expect(comp.isFeedbackView()).toBe(false);
        expect(getLatestSubmissionSpy).toHaveBeenCalledOnce();
        expect(getSubmissionsWithResultsSpy).not.toHaveBeenCalled();
        expect(comp.submission()).toEqual(submission);
        expect(comp.modelingExercise()).toEqual(modelingExercise);
        expect(comp.participation()).toEqual(participation);
    });

    it('should initialize with submissionId (Feedback View Mode)', () => {
        const route = {
            params: of({ courseId: 5, exerciseId: 22, participationId: 1, submissionId: 20 }),
        } as any as ActivatedRoute;

        createModelingSubmissionComponent(route);

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

        const getSubmissionsWithResultsSpy = vi.spyOn(service, 'getSubmissionsWithResultsForParticipation').mockReturnValue(of([submission]));
        const getLatestSubmissionSpy = vi.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));

        comp.ngOnInit();

        expect(comp.isFeedbackView()).toBe(true);
        expect(comp.submissionId).toBe(20);
        expect(getSubmissionsWithResultsSpy).toHaveBeenCalledOnce();
        expect(getLatestSubmissionSpy).toHaveBeenCalledOnce();
        expect(comp.sortedSubmissionHistory()).toEqual([submission]);
        expect(comp.sortedResultHistory()).toEqual([result]);
        expect(comp.submission()).toEqual(submission);
    });

    it('should initialize with submissionId and resultId (Feedback View with specific result)', () => {
        const route = {
            params: of({ courseId: 5, exerciseId: 22, participationId: 1, submissionId: 20, resultId: 99 }),
        } as any as ActivatedRoute;

        createModelingSubmissionComponent(route);

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

        const manualResult = {
            id: 99,
            completionDate: dayjs().subtract(1, 'hour'),
            assessmentType: AssessmentType.MANUAL,
            successful: true,
            score: 85,
        } as Result;

        const athenaResult = {
            id: 100,
            completionDate: dayjs(),
            assessmentType: AssessmentType.AUTOMATIC_ATHENA,
            successful: true,
            score: 75,
        } as Result;

        submission.results = [manualResult, athenaResult];
        submission.latestResult = athenaResult;

        vi.spyOn(service, 'getSubmissionsWithResultsForParticipation').mockReturnValue(of([submission]));
        vi.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));

        comp.ngOnInit();

        expect(comp.isFeedbackView()).toBe(true);
        expect(comp.submissionId).toBe(20);
        expect(comp.resultId).toBe(99);
        expect(comp.submission()?.results).toHaveLength(2);
        expect(comp.result()?.id).toBe(99);
        expect(comp.result()?.assessmentType).toBe(AssessmentType.MANUAL);
    });

    it('should show an older result by id after the real service conversion of a newest-first response', () => {
        const route = {
            params: of({ courseId: 5, exerciseId: 22, participationId: 1, submissionId: 20, resultId: 24 }),
        } as any as ActivatedRoute;

        createModelingSubmissionComponent(route);

        const modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
        modelingExercise.dueDate = dayjs().subtract(2, 'days');
        modelingExercise.assessmentDueDate = dayjs().subtract(1, 'days');
        modelingExercise.maxPoints = 20;
        modelingExercise.teamMode = false;
        const participation = new StudentParticipation();
        participation.exercise = modelingExercise;
        participation.id = 1;

        // The history endpoint lists results newest first, so the older result is the last element.
        const olderFeedback = { id: 2, text: 'older', credits: 5, type: FeedbackType.MANUAL } as Feedback;
        const newerResult = { id: 25, completionDate: dayjs().subtract(1, 'hour'), assessmentType: AssessmentType.MANUAL, score: 90, feedbacks: [] } as Result;
        const olderResult = { id: 24, completionDate: dayjs().subtract(2, 'hours'), assessmentType: AssessmentType.MANUAL, score: 50, feedbacks: [olderFeedback] } as Result;
        const serverSubmission = { id: 20, submitted: true, participation, results: [newerResult, olderResult] } as ModelingSubmission;

        // Answer the HTTP requests, not the service, so the real ModelingSubmissionService conversion runs on the server payload.
        const httpMock = TestBed.inject(HttpTestingController);
        comp.ngOnInit();
        httpMock.expectOne({ method: 'GET', url: 'api/modeling/participations/1/submissions-with-results' }).flush([serverSubmission]);
        httpMock.expectOne({ method: 'GET', url: 'api/modeling/participations/1/latest-modeling-submission' }).flush(serverSubmission);

        expect(comp.submission()?.results?.map((result) => result.id)).toEqual([25, 24]);
        expect(comp.result()?.id).toBe(24);
        expect(comp.assessmentResult()?.feedbacks).toEqual([olderFeedback]);
    });

    it('should get inactive as soon as the due date passes the current date', async () => {
        createModelingSubmissionComponent();

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
        expect(comp.submission()).toEqual(submission);
    });

    it('should set correct properties on modeling exercise create when saving', () => {
        createModelingSubmissionComponent();

        fixture.detectChanges();

        const createStub = vi.spyOn(service, 'create').mockReturnValue(of(new HttpResponse({ body: submission })));
        comp.modelingExercise.set(new ModelingExercise(UMLDiagramType.DeploymentDiagram, undefined, undefined));
        comp.modelingExercise().id = 1;
        comp.saveDiagram();
        expect(createStub).toHaveBeenCalledOnce();
        expect(comp.submission()).toEqual(submission);
    });

    it('should set correct properties on modeling exercise create when submitting', () => {
        createModelingSubmissionComponent();

        fixture.detectChanges();

        comp.submission.set(<ModelingSubmission>(<unknown>{
            model: validMockModel,
            submitted: true,
            participation,
        }));
        const createStub = vi.spyOn(service, 'create').mockReturnValue(of(new HttpResponse({ body: submission })));
        comp.modelingExercise.set(new ModelingExercise(UMLDiagramType.DeploymentDiagram, undefined, undefined));
        comp.modelingExercise().id = 1;
        comp.submitExercise();
        expect(createStub).toHaveBeenCalledOnce();
        expect(comp.submission()).toEqual(submission);
    });

    it('should catch error on submit', () => {
        createModelingSubmissionComponent();

        const modelSubmission = <ModelingSubmission>(<unknown>{
            model: validMockModel,
            submitted: true,
            participation,
        });
        comp.submission.set(modelSubmission);
        vi.spyOn(service, 'create').mockReturnValue(throwError(() => ({ status: 500 })));
        const alertServiceSpy = vi.spyOn(alertService, 'error');
        comp.modelingExercise.set(new ModelingExercise(UMLDiagramType.DeploymentDiagram, undefined, undefined));
        comp.modelingExercise().id = 1;
        comp.submitExercise();
        expect(alertServiceSpy).toHaveBeenCalledOnce();
        expect(comp.submission()).toBe(modelSubmission);
    });

    it('should handle failed Athena assessment appropriately', async () => {
        const routeOverride = {
            params: of({ courseId: 5, exerciseId: 22, participationId: 123 }),
        } as any as ActivatedRoute;
        createModelingSubmissionComponent(routeOverride);

        const participationWebSocketService = TestBed.inject(ParticipationWebsocketService);
        const alertServiceSpy = vi.spyOn(alertService, 'error');

        const manualResult = new Result();
        manualResult.score = 50.0;
        manualResult.assessmentType = AssessmentType.MANUAL;
        manualResult.submission = submission;
        manualResult.completionDate = dayjs();
        manualResult.feedbacks = [];

        const failedAthenaResult = new Result();
        failedAthenaResult.assessmentType = AssessmentType.AUTOMATIC_ATHENA;
        failedAthenaResult.submission = submission;
        failedAthenaResult.completionDate = undefined;
        failedAthenaResult.successful = false;
        failedAthenaResult.feedbacks = [];

        const resultSubject = new BehaviorSubject<Result | undefined>(manualResult);
        const subscribeForLatestResultOfParticipationStub = vi.spyOn(participationWebSocketService, 'subscribeForLatestResultOfParticipation').mockReturnValue(resultSubject);

        submission.model = validMockModel;
        vi.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));

        fixture.detectChanges();
        await fixture.whenStable();

        expect(subscribeForLatestResultOfParticipationStub).toHaveBeenCalledOnce();

        alertServiceSpy.mockClear();

        resultSubject.next(failedAthenaResult);
        fixture.changeDetectorRef.detectChanges();

        expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.exercise.athenaFeedbackFailed');
    });

    it('should handle Athena assessment results separately from manual assessments', async () => {
        createModelingSubmissionComponent();

        const participationWebSocketService = TestBed.inject(ParticipationWebsocketService);
        const alertServiceInfoSpy = vi.spyOn(alertService, 'info');
        const alterServiceSuccessSpy = vi.spyOn(alertService, 'success');

        const athenaResult = new Result();
        athenaResult.score = 75.0;
        athenaResult.assessmentType = AssessmentType.AUTOMATIC_ATHENA;
        athenaResult.submission = submission;
        athenaResult.completionDate = dayjs();
        athenaResult.successful = true;
        athenaResult.feedbacks = [];

        const manualResult = new Result();
        manualResult.score = 50.0;
        manualResult.assessmentType = AssessmentType.MANUAL;
        manualResult.submission = submission;
        manualResult.completionDate = dayjs();
        manualResult.feedbacks = [];

        const resultSubject = new BehaviorSubject<Result | undefined>(manualResult);
        const subscribeForLatestResultOfParticipationStub = vi.spyOn(participationWebSocketService, 'subscribeForLatestResultOfParticipation').mockReturnValue(resultSubject);

        submission.model = validMockModel;
        vi.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));

        fixture.detectChanges();
        await fixture.whenStable();

        expect(subscribeForLatestResultOfParticipationStub).toHaveBeenCalledOnce();
        expect(comp.assessmentResult()).toEqual(manualResult);
        expect(alertServiceInfoSpy).toHaveBeenCalledWith('artemisApp.modelingEditor.newAssessment');

        resultSubject.next(athenaResult);
        fixture.changeDetectorRef.detectChanges();

        expect(comp.assessmentResult()).toEqual(athenaResult);
        expect(alterServiceSuccessSpy).toHaveBeenCalledWith('artemisApp.exercise.athenaFeedbackSuccessful', { title: comp.modelingExercise()?.title ?? '' });
    });

    it('should set result when new result comes in from websocket', async () => {
        createModelingSubmissionComponent();

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

    it('refreshes a non-collaborative editor from an automatic-submission snapshot', () => {
        createModelingSubmissionComponent();

        submission.submitted = false;
        vi.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));
        const websocketService = TestBed.inject(WebsocketService) as unknown as MockWebsocketService;
        vi.spyOn(websocketService, 'subscribe');
        const persistedModel = JSON.stringify({ ...JSON.parse(validMockModel), title: 'Persisted snapshot' });
        const modelSubmission = <ModelingSubmission>(<unknown>{
            id: submission.id,
            model: persistedModel,
            submitted: true,
            participation,
        });
        fixture.detectChanges();
        websocketService.emit(`/user/topic/modelingSubmission/${submission.id}`, modelSubmission);
        expect(comp.submission()).toEqual(modelSubmission);
        expect(comp.umlModel().title).toBe('Persisted snapshot');
    });

    it('should not process results without completionDate except for failed Athena results', () => {
        createModelingSubmissionComponent();

        submission.model = validMockModel;
        vi.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));
        const participationWebSocketService = TestBed.inject(ParticipationWebsocketService);

        const incompleteResult = new Result();
        incompleteResult.assessmentType = AssessmentType.MANUAL;
        incompleteResult.submission = submission;
        incompleteResult.completionDate = undefined;

        const resultSubject = new BehaviorSubject<Result | undefined>(incompleteResult);
        vi.spyOn(participationWebSocketService, 'subscribeForLatestResultOfParticipation').mockReturnValue(resultSubject);

        fixture.detectChanges();

        expect(comp.assessmentResult()).toBeUndefined();
    });

    it('should set correct properties on modeling exercise update when submitting', () => {
        createModelingSubmissionComponent();

        comp.submission.set(<ModelingSubmission>(<unknown>{
            id: 1,
            model: validMockModel,
            submitted: true,
            participation,
        }));
        const updateStub = vi.spyOn(service, 'update').mockReturnValue(of(new HttpResponse({ body: submission })));
        comp.modelingExercise.set(new ModelingExercise(UMLDiagramType.DeploymentDiagram, undefined, undefined));
        comp.modelingExercise().id = 1;
        fixture.detectChanges();
        comp.submitExercise();
        expect(updateStub).toHaveBeenCalledOnce();
        expect(comp.submission()).toEqual(submission);
    });

    it('should calculate number of elements from model', () => {
        createModelingSubmissionComponent();

        const elements = [{ id: 1 }, { id: 2 }, { id: 3 }];
        const relationships = [{ id: 4 }, { id: 5 }];
        submission.model = JSON.stringify({ elements, relationships });
        comp.submission.set(submission);
        fixture.changeDetectorRef.detectChanges();
        expect(comp.calculateNumberOfModelElements()).toBe(elements.length + relationships.length);
    });

    it('should update selected element IDs', () => {
        createModelingSubmissionComponent();

        const selectedIds = ['element1', 'element2', 'relationship1'];
        comp.onSelectedElementIdsChanged(selectedIds);
        expect(comp.selectedElementIds()).toEqual(selectedIds);
    });

    it('should not mark any feedback while nothing is selected on the diagram', () => {
        createModelingSubmissionComponent();

        const feedback = <Feedback>(<unknown>{ referenceType: 'Activity', referenceId: '5' });
        comp.onSelectedElementIdsChanged([]);
        fixture.changeDetectorRef.detectChanges();
        expect(comp.isFeedbackForSelection(feedback)).toBe(false);
    });

    it('should mark only the feedback belonging to the selected elements', () => {
        createModelingSubmissionComponent();

        const id = 'referenceId';
        const feedback = <Feedback>(<unknown>{ referenceType: 'Activity', referenceId: id });
        comp.onSelectedElementIdsChanged([id]);
        fixture.changeDetectorRef.detectChanges();
        expect(comp.isFeedbackForSelection(feedback)).toBe(true);

        comp.onSelectedElementIdsChanged(['otherId']);
        fixture.changeDetectorRef.detectChanges();
        expect(comp.isFeedbackForSelection(feedback)).toBe(false);
    });

    it("should highlight a feedback entry's element on the diagram while it is previewed", () => {
        createModelingSubmissionComponent();

        const feedback = <Feedback>(<unknown>{ referenceType: 'Activity', referenceId: 'element-7' });
        expect(comp['highlightedFeedbackElements']()).toBeUndefined();

        comp.previewFeedbackTarget(feedback);
        expect(comp['highlightedFeedbackElements']()?.has('element-7')).toBe(true);

        comp.clearFeedbackPreview();
        expect(comp['highlightedFeedbackElements']()).toBeUndefined();
    });

    it("should send the diagram to a feedback entry's element when it is chosen", () => {
        createModelingSubmissionComponent();

        const revealAssessment = vi.fn();
        vi.spyOn(comp, 'modelingAssessment').mockReturnValue({ revealAssessment } as unknown as ModelingAssessmentComponent);

        comp.showFeedbackOnDiagram(<Feedback>(<unknown>{ referenceId: 'element-7' }));
        expect(revealAssessment).toHaveBeenCalledWith('element-7');

        comp.showFeedbackOnDiagram(<Feedback>(<unknown>{ detailText: 'general remark' }));
        expect(revealAssessment).toHaveBeenCalledOnce();
    });

    it('should not highlight anything for feedback that references no element', () => {
        createModelingSubmissionComponent();

        comp.previewFeedbackTarget(<Feedback>(<unknown>{ detailText: 'general remark' }));
        expect(comp['highlightedFeedbackElements']()).toBeUndefined();
    });

    it('should update submission with current values', () => {
        createModelingSubmissionComponent();

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
        createModelingSubmissionComponent();

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
        fixture.detectChanges();
        comp.saveDiagram();
        expect(comp.isChanged()).toBe(false);
    });

    it('should mark the subsequent feedback', () => {
        createModelingSubmissionComponent();

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
        createModelingSubmissionComponent();

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

        fixture.detectChanges();

        expect(comp.modelingExercise()).toEqual(participation.exercise);
        expect(comp.submission()).toEqual(modelingSubmission);
        expect(comp.participation()).toEqual(participation);
        expect(comp.umlModel()).toBeTruthy();
        expect(comp.hasElements()).toBe(true);

        expect(getDataForFileUploadEditorSpy).not.toHaveBeenCalled();
    });

    it('should fetch and sort submission history correctly', () => {
        const route = {
            params: of({ courseId: 5, exerciseId: 22, participationId: 123, submissionId: 20 }),
        } as any as ActivatedRoute;
        createModelingSubmissionComponent(route);

        const createResult = (id: number, dateStr: string): Result => {
            const result = new Result();
            result.id = id;
            result.completionDate = dayjs(dateStr);
            return result;
        };

        const createSubmission = (id: number, results: Result[]): ModelingSubmission => {
            const submission = new ModelingSubmission();
            submission.id = id;
            submission.results = results;
            submission.participation = participation;
            return submission;
        };

        const resultData = [
            { id: 1, date: '2024-01-01T10:00:00' }, // Monday 10 AM
            { id: 2, date: '2024-01-03T09:15:00' }, // Wednesday 9:15 AM
            { id: 3, date: '2024-01-04T16:45:00' }, // Thursday 4:45 PM
            { id: 4, date: '2024-01-02T14:30:00' }, // Tuesday 2:30 PM
            { id: 5, date: '2024-01-05T11:20:00' }, // Friday 11:20 AM
        ];

        const results = resultData.reduce(
            (acc, { id, date }) => {
                acc[id] = createResult(id, date);
                return acc;
            },
            {} as Record<number, Result>,
        );

        const submissions = [
            createSubmission(0, [results[1], results[2]]), // Latest is date3 (Wed 9:15 AM)
            createSubmission(1, [results[3], results[4]]), // Latest is date4 (Thu 4:45 PM)
            createSubmission(2, [results[5]]), // Latest is date5 (Fri 11:20 AM)
        ];

        const expectedSortedSubmissions = [submissions[0], submissions[1], submissions[2]];
        const expectedSortedResults = [results[2], results[3], results[5]];

        const submissionsWithResultsSpy = vi.spyOn(service, 'getSubmissionsWithResultsForParticipation').mockReturnValue(of([submissions[2], submissions[1], submissions[0]]));

        comp.ngOnInit();

        expect(submissionsWithResultsSpy).toHaveBeenCalledWith(123);
        expect(comp.sortedSubmissionHistory()).toEqual(expectedSortedSubmissions);
        comp.sortedResultHistory().forEach((result, index) => {
            expect(result?.id).toBe(expectedSortedResults[index].id);
            expect(result?.completionDate?.isSame(expectedSortedResults[index].completionDate)).toBe(true);
        });
    });
    describe('feedback presentation', () => {
        it.each([
            { credits: 5, tone: 'positive', signed: true, pluralKey: 'many', icon: faCheck },
            { credits: 1, tone: 'positive', signed: true, pluralKey: 'one', icon: faCheck },
            { credits: -2.5, tone: 'negative', signed: false, pluralKey: 'many', icon: faXmark },
            { credits: -1, tone: 'negative', signed: false, pluralKey: 'one', icon: faXmark },
            { credits: 0, tone: 'zero', signed: false, pluralKey: 'many', icon: faTriangleExclamation },
            { credits: undefined, tone: 'zero', signed: false, pluralKey: 'many', icon: faTriangleExclamation },
        ])('describes feedback worth $credits credits as $tone', ({ credits, tone, signed, pluralKey, icon }) => {
            createModelingSubmissionComponent();
            const translate = vi.spyOn(TestBed.inject(TranslateService), 'instant');
            const feedback = { credits } as Feedback;

            expect(comp['feedbackTone'](feedback)).toBe(tone);
            expect(comp['feedbackToneIcon'](feedback)).toBe(icon);

            const rendered = comp['feedbackPoints'](feedback);
            expect(translate).toHaveBeenCalledWith(`artemisApp.assessment.detail.points.${pluralKey}`, { points: (credits ?? 0).toLocaleString('en') });
            expect(rendered.startsWith('+')).toBe(signed);
        });

        it('should name the element by its type, soften the Apollon owner separator, and stay silent without one', () => {
            createModelingSubmissionComponent();
            comp.assessmentsNames.set({ ref1: { name: 'Course::+ title: String', type: 'attribute' } });

            expect(comp['feedbackElementName']({ referenceId: 'ref1' } as Feedback)).toBe('attribute Course › + title: String');
            comp.assessmentsNames.set({ ref1: { name: 'TestClass', type: 'class' } });
            expect(comp['feedbackElementName']({ referenceId: 'ref1' } as Feedback)).toBe('class TestClass');
            comp.assessmentsNames.set({ ref1: { name: 'TestClass', type: '' } });
            expect(comp['feedbackElementName']({ referenceId: 'ref1' } as Feedback)).toBe('TestClass');
            comp.assessmentsNames.set({ ref1: { name: 'Course::+ title: String', type: 'attribute' } });
            expect(comp['feedbackElementName']({} as Feedback)).toBeUndefined();
            expect(comp['feedbackElementName']({ referenceId: 'unknown' } as Feedback)).toBeUndefined();
            comp.assessmentsNames.set({ ref1: { name: '', type: 'attribute' } });
            expect(comp['feedbackElementName']({ referenceId: 'ref1' } as Feedback)).toBeUndefined();
        });
    });

    describe('unsaved changes', () => {
        it('should allow leaving while the editor has not mounted, since there is nothing to lose yet', () => {
            createModelingSubmissionComponent();
            comp.submission.set(submission);
            (mockModelingEditor as any).isApollonEditorMounted = false;

            expect(comp.canDeactivate()).toBe(true);
            expect(mockModelingEditor.getCurrentModel).not.toHaveBeenCalled();
        });

        it('should treat an edited explanation as an unsaved change even when the diagram is untouched', () => {
            createModelingSubmissionComponent();
            const model = { nodes: [], edges: [], version: '3.0.0' } as unknown as UMLModel;
            (mockModelingEditor.getCurrentModel as ReturnType<typeof vi.fn>).mockReturnValue(model);
            (mockModelingEditor as any).isApollonEditorMounted = true;
            comp.submission.set(<ModelingSubmission>(<unknown>{ id: 1, model: JSON.stringify(model), explanationText: 'saved', participation }));

            comp.explanation = 'saved';
            expect(comp.canDeactivate()).toBe(true);

            comp.explanation = 'edited';
            expect(comp.canDeactivate()).toBe(false);
        });

        it('should count a first diagram drawn against an empty submission as unsaved', () => {
            createModelingSubmissionComponent();
            (mockModelingEditor as any).isApollonEditorMounted = true;
            comp.submission.set(<ModelingSubmission>(<unknown>{ id: 1, participation }));
            comp.explanation = '';

            (mockModelingEditor.getCurrentModel as ReturnType<typeof vi.fn>).mockReturnValue({ nodes: [], edges: [], version: '3.0.0' } as unknown as UMLModel);
            expect(comp.canDeactivate()).toBe(true);

            (mockModelingEditor.getCurrentModel as ReturnType<typeof vi.fn>).mockReturnValue({ nodes: [{ id: 'a' }], edges: [], version: '3.0.0' } as unknown as UMLModel);
            expect(comp.canDeactivate()).toBe(false);
        });

        it('should block the browser unload only while changes are unsaved', () => {
            createModelingSubmissionComponent();
            const canDeactivate = vi.spyOn(comp, 'canDeactivate');
            const event = { preventDefault: vi.fn() } as unknown as BeforeUnloadEvent;

            canDeactivate.mockReturnValue(true);
            expect(comp.unloadNotification(event)).toBe(true);
            expect(event.preventDefault).not.toHaveBeenCalled();

            canDeactivate.mockReturnValue(false);
            comp.unloadNotification(event);
            expect(event.preventDefault).toHaveBeenCalledOnce();
        });

        it('should report no model elements for a submission without a model', () => {
            createModelingSubmissionComponent();
            comp.submission.set(<ModelingSubmission>(<unknown>{ id: 1, participation }));

            expect(comp.calculateNumberOfModelElements()).toBe(0);
        });
    });

    describe('collaboration user', () => {
        it('should fall back from name to login when the account has no display name', async () => {
            createModelingSubmissionComponent();
            const accountService = TestBed.inject(AccountService);
            vi.spyOn(accountService, 'identity').mockResolvedValue({ login: 'ab12cde' } as User);

            comp.ngOnInit();
            await vi.waitFor(() => expect(comp['apollonCollaborationUser']()).toBeDefined());

            expect(comp['apollonCollaborationUser']()).toEqual(expect.objectContaining({ id: 'ab12cde', name: 'ab12cde' }));
        });

        it('should report a missing identity instead of mounting collaboration without one', async () => {
            createModelingSubmissionComponent();
            const accountService = TestBed.inject(AccountService);
            vi.spyOn(accountService, 'identity').mockResolvedValue(undefined);

            comp.ngOnInit();
            await vi.waitFor(() => expect(captureException).toHaveBeenCalled());

            expect(comp['apollonCollaborationUser']()).toBeUndefined();
        });
    });

    describe('complaint section', () => {
        it.each([
            { result: true, examMode: false, feedbackView: false, expected: true },
            { result: false, examMode: false, feedbackView: false, expected: false },
            { result: true, examMode: true, feedbackView: false, expected: false },
            { result: true, examMode: false, feedbackView: true, expected: false },
        ])('shows the complaint section: $expected (result=$result, exam=$examMode, feedbackView=$feedbackView)', ({ result, examMode, feedbackView, expected }) => {
            createModelingSubmissionComponent();
            comp.result.set(result ? ({ id: 1 } as Result) : undefined);
            comp.examMode.set(examMode);
            comp.isFeedbackView.set(feedbackView);

            expect(comp['showComplaintSection']()).toBe(expected);
        });
    });

    it('should open the editor when switching from the assessed graded participation to a practice participation', () => {
        const params = new BehaviorSubject<Params>({ courseId: 5, exerciseId: 22, participationId: 5 });
        createModelingSubmissionComponent({ params: params.asObservable() } as any as ActivatedRoute);

        const exercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
        exercise.id = 22;
        exercise.dueDate = dayjs().subtract(2, 'days');
        exercise.assessmentDueDate = dayjs().subtract(1, 'day');
        exercise.teamMode = false;

        const gradedParticipation = new StudentParticipation();
        gradedParticipation.id = 5;
        gradedParticipation.exercise = exercise;
        gradedParticipation.initializationDate = dayjs().subtract(5, 'days');

        const gradedResult = new Result();
        gradedResult.id = 30;
        gradedResult.score = 42;
        gradedResult.completionDate = dayjs().subtract(1, 'hour');
        gradedResult.assessmentType = AssessmentType.MANUAL;
        gradedResult.feedbacks = [];

        const gradedSubmission = new ModelingSubmission();
        gradedSubmission.id = 20;
        gradedSubmission.submitted = true;
        gradedSubmission.participation = gradedParticipation;
        gradedSubmission.results = [gradedResult];

        const practiceParticipation = new StudentParticipation();
        practiceParticipation.id = 9;
        practiceParticipation.exercise = exercise;
        practiceParticipation.testRun = true;
        practiceParticipation.initializationDate = dayjs();

        const practiceSubmission = new ModelingSubmission();
        practiceSubmission.id = 99;
        practiceSubmission.submitted = false;
        practiceSubmission.participation = practiceParticipation;

        vi.spyOn(service, 'getLatestSubmissionForModelingEditor').mockImplementation((participationId: number) =>
            of(participationId === 9 ? practiceSubmission : gradedSubmission),
        );

        comp.ngOnInit();
        expect(comp.result()?.id).toBe(30);

        params.next({ courseId: 5, exerciseId: 22, participationId: 9 });

        expect(comp.participation().id).toBe(9);
        expect(comp.result()).toBeUndefined();
        expect(comp.assessmentResult()).toBeUndefined();
        expect(comp['shouldShowLiveEditor']()).toBe(true);
    });
});
