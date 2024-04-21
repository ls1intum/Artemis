import * as ace from 'brace';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { ModelingSubmissionComponent } from 'app/exercises/modeling/participate/modeling-submission.component';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockParticipationWebsocketService } from '../../helpers/mocks/service/mock-participation-websocket.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateService } from '@ngx-translate/core';
import { RouterTestingModule } from '@angular/router/testing';
import { ActivatedRoute } from '@angular/router';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { ChangeDetectorRef, DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { MockComplaintService } from '../../helpers/mocks/service/mock-complaint.service';
import dayjs from 'dayjs/esm';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { ModelingEditorComponent } from 'app/exercises/modeling/shared/modeling-editor.component';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ComplaintService } from 'app/complaints/complaint.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Result } from 'app/entities/result.model';
import { routes } from 'app/exercises/modeling/participate/modeling-participation.route';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { UMLDiagramType, UMLElement, UMLModel } from '@ls1intum/apollon';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { HeaderParticipationPageComponent } from 'app/exercises/shared/exercise-headers/header-participation-page.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { ResizeableContainerComponent } from 'app/shared/resizeable-container/resizeable-container.component';
import { TeamParticipateInfoBoxComponent } from 'app/exercises/shared/team/team-participate/team-participate-info-box.component';
import { TeamSubmissionSyncComponent } from 'app/exercises/shared/team-submission-sync/team-submission-sync.component';
import { ModelingAssessmentComponent } from 'app/exercises/modeling/assess/modeling-assessment.component';
import { FullscreenComponent } from 'app/shared/fullscreen/fullscreen.component';
import { AdditionalFeedbackComponent } from 'app/shared/additional-feedback/additional-feedback.component';
import { RatingComponent } from 'app/exercises/shared/rating/rating.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ComplaintsStudentViewComponent } from 'app/complaints/complaints-for-students/complaints-student-view.component';
import { HttpResponse } from '@angular/common/http';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { AlertService } from 'app/core/util/alert.service';
import { ExerciseMode } from 'app/entities/exercise.model';
import { SubmissionPatch } from 'app/entities/submission-patch.model';

describe('ModelingSubmissionComponent', () => {
    // needed to make sure ace is defined
    ace.acequire('ace/ext/modelist.js');
    let comp: ModelingSubmissionComponent;
    let fixture: ComponentFixture<ModelingSubmissionComponent>;
    let debugElement: DebugElement;
    let service: ModelingSubmissionService;
    let alertService: AlertService;

    const route = { params: of({ courseId: 5, exerciseId: 22, participationId: 123 }) } as any as ActivatedRoute;
    const participation = new StudentParticipation();
    participation.exercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
    participation.exercise.teamMode = true;
    participation.exercise.mode = ExerciseMode.TEAM;
    participation.id = 1;
    const submission = <ModelingSubmission>(<unknown>{ id: 20, submitted: true, participation });
    const result = { id: 1 } as Result;

    const originalConsoleError = console.error;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([routes[0]])],
            declarations: [
                ModelingSubmissionComponent,
                MockComponent(ModelingEditorComponent),
                MockPipe(HtmlForMarkdownPipe),
                MockPipe(ArtemisTranslatePipe),
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
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: ActivatedRoute, useValue: route },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ModelingSubmissionComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                service = debugElement.injector.get(ModelingSubmissionService);
                alertService = debugElement.injector.get(AlertService);
                comp.modelingEditor = TestBed.createComponent(MockComponent(ModelingEditorComponent)).componentInstance;
            });
        console.error = jest.fn();
    });

    afterEach(() => {
        jest.restoreAllMocks();
        console.error = originalConsoleError;
    });

    it('should call load getDataForModelingEditor on init', () => {
        // GIVEN
        const getLatestSubmissionForModelingEditorStub = jest.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(getLatestSubmissionForModelingEditorStub).toHaveBeenCalledOnce();
        expect(comp.submission.id).toBe(20);
    });

    it('should subscribe to modeling editor patches.', () => {
        // Mock the submission
        jest.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));

        // Initialize the component
        comp.ngOnInit();
        fixture.detectChanges();

        // Subscribe to patches
        const receiverMock = jest.fn();
        comp.submissionPatchObservable.subscribe(receiverMock);

        // Force emit a patch
        jest.spyOn(comp.modelingEditor.onModelPatch, 'emit');
        comp.modelingEditor.onModelPatch.emit([{ value: 'test', op: 'add', path: '/test' }]);

        // We have got it?
        expect(receiverMock).toHaveBeenCalled();
        expect(receiverMock.mock.lastCall[0].patch[0].path).toBe('/test');
    });

    it('should update the submission when a patch is received.', () => {
        // Initialize submission
        submission.model = '{"elements": {"1": {"id": 1}}}';
        jest.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));

        // Initialize the component
        comp.ngOnInit();

        const editorImportSpy = jest.spyOn(comp.modelingEditor, 'importPatch');
        const submissionPatch = new SubmissionPatch([
            {
                op: 'replace',
                path: '/elements/1/name',
                value: 'john',
            },
        ]);
        comp.onReceiveSubmissionPatchFromTeam(submissionPatch);

        // We have got it?
        expect(editorImportSpy).toHaveBeenCalledWith([
            {
                op: 'replace',
                path: '/elements/1/name',
                value: 'john',
            },
        ]);
    });

    it('should allow to submit when exercise due date not set', () => {
        // GIVEN
        jest.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));

        // WHEN
        comp.isLoading = false;
        fixture.detectChanges();

        expect(debugElement.query(By.css('div'))).not.toBeNull();

        const submitButton = debugElement.query(By.css('jhi-button'));
        expect(submitButton).not.toBeNull();
        expect(submitButton.attributes['ng-reflect-disabled']).toBe('false');
        expect(comp.isActive).toBeTrue();
    });

    it('should not allow to submit after the due date if the initialization date is before the due date', () => {
        submission.participation!.initializationDate = dayjs().subtract(2, 'days');
        (<StudentParticipation>submission.participation).exercise!.dueDate = dayjs().subtract(1, 'days');
        jest.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));

        fixture.detectChanges();

        const submitButton = debugElement.query(By.css('jhi-button'));
        expect(submitButton).not.toBeNull();
        expect(submitButton.attributes['ng-reflect-disabled']).toBe('true');
    });

    it('should allow to submit after the due date if the initialization date is after the due date and not submitted', () => {
        submission.participation!.initializationDate = dayjs().add(1, 'days');
        (<StudentParticipation>submission.participation).exercise!.dueDate = dayjs();
        submission.submitted = false;
        jest.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));

        fixture.detectChanges();

        expect(comp.isLate).toBeTrue();
        const submitButton = debugElement.query(By.css('jhi-button'));
        expect(submitButton).not.toBeNull();
        expect(submitButton.attributes['ng-reflect-disabled']).toBe('false');
        submission.submitted = true;
    });

    it('should not allow to submit if there is a result and no due date', () => {
        comp.result = result;
        jest.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));

        fixture.detectChanges();

        const submitButton = debugElement.query(By.css('jhi-button'));
        expect(submitButton).not.toBeNull();
        expect(submitButton.attributes['ng-reflect-disabled']).toBe('true');
    });

    it('should get inactive as soon as the due date passes the current date', () => {
        (<StudentParticipation>submission.participation).exercise!.dueDate = dayjs().add(1, 'days');
        jest.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));

        fixture.detectChanges();
        comp.participation.initializationDate = dayjs();

        expect(comp.isActive).toBeTrue();

        comp.modelingExercise.dueDate = dayjs().subtract(1, 'days');

        fixture.detectChanges();
        expect(comp.isActive).toBeFalse();
    });

    it('should catch error on 403 error status', () => {
        jest.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(throwError(() => ({ status: 403 })));
        const alertServiceSpy = jest.spyOn(alertService, 'error');
        fixture.detectChanges();

        expect(alertServiceSpy).toHaveBeenCalledOnce();
    });

    it('should set correct properties on modeling exercise update when saving', () => {
        jest.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));
        fixture.detectChanges();

        const updateStub = jest.spyOn(service, 'update').mockReturnValue(of(new HttpResponse({ body: submission })));
        comp.saveDiagram();
        expect(updateStub).toHaveBeenCalledOnce();
        expect(comp.submission).toEqual(submission);
    });

    it('should set correct properties on modeling exercise create when saving', () => {
        fixture.detectChanges();

        const createStub = jest.spyOn(service, 'create').mockReturnValue(of(new HttpResponse({ body: submission })));
        comp.modelingExercise = new ModelingExercise(UMLDiagramType.DeploymentDiagram, undefined, undefined);
        comp.modelingExercise.id = 1;
        comp.saveDiagram();
        expect(createStub).toHaveBeenCalledOnce();
        expect(comp.submission).toEqual(submission);
    });

    it('should set correct properties on modeling exercise create when submitting', () => {
        fixture.detectChanges();

        const modelSubmission = <ModelingSubmission>(<unknown>{ model: '{"elements": [{"id": 1}]}', submitted: true, participation });
        comp.submission = modelSubmission;
        const createStub = jest.spyOn(service, 'create').mockReturnValue(of(new HttpResponse({ body: submission })));
        comp.modelingExercise = new ModelingExercise(UMLDiagramType.DeploymentDiagram, undefined, undefined);
        comp.modelingExercise.id = 1;
        comp.submit();
        expect(createStub).toHaveBeenCalledOnce();
        expect(comp.submission).toEqual(submission);
    });

    it('should catch error on submit', () => {
        const modelSubmission = <ModelingSubmission>(<unknown>{ model: '{"elements": [{"id": 1}]}', submitted: true, participation });
        comp.submission = modelSubmission;
        jest.spyOn(service, 'create').mockReturnValue(throwError(() => ({ status: 500 })));
        const alertServiceSpy = jest.spyOn(alertService, 'error');
        comp.modelingExercise = new ModelingExercise(UMLDiagramType.DeploymentDiagram, undefined, undefined);
        comp.modelingExercise.id = 1;
        comp.submit();
        expect(alertServiceSpy).toHaveBeenCalledOnce();
        expect(comp.submission).toBe(modelSubmission);
    });

    it('should set result when new result comes in from websocket', () => {
        submission.model = '{"elements": [{"id": 1}]}';
        jest.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));
        const participationWebSocketService = debugElement.injector.get(ParticipationWebsocketService);

        const unreferencedFeedback = new Feedback();
        unreferencedFeedback.id = 1;
        unreferencedFeedback.detailText = 'General Feedback';
        unreferencedFeedback.credits = 5;
        unreferencedFeedback.type = FeedbackType.MANUAL_UNREFERENCED;
        const newResult = new Result();
        newResult.score = 50.0;
        newResult.assessmentType = AssessmentType.MANUAL;
        newResult.submission = submission;
        newResult.participation = submission.participation;
        newResult.completionDate = dayjs();
        newResult.feedbacks = [unreferencedFeedback];
        const subscribeForLatestResultOfParticipationSubject = new BehaviorSubject<Result | undefined>(newResult);
        const subscribeForLatestResultOfParticipationStub = jest
            .spyOn(participationWebSocketService, 'subscribeForLatestResultOfParticipation')
            .mockReturnValue(subscribeForLatestResultOfParticipationSubject);
        fixture.detectChanges();
        expect(subscribeForLatestResultOfParticipationStub).toHaveBeenCalledOnce();
        expect(comp.assessmentResult).toEqual(newResult);
    });

    it('should update submission when new submission comes in from websocket', () => {
        submission.submitted = false;
        jest.spyOn(service, 'getLatestSubmissionForModelingEditor').mockReturnValue(of(submission));
        const websocketService = debugElement.injector.get(JhiWebsocketService);
        jest.spyOn(websocketService, 'subscribe');
        const modelSubmission = <ModelingSubmission>(<unknown>{
            id: 1,
            model: '{"elements": [{"id": 1}]}',
            submitted: true,
            participation,
        });
        const receiveStub = jest.spyOn(websocketService, 'receive').mockReturnValue(of(modelSubmission));
        fixture.detectChanges();
        expect(comp.submission).toEqual(modelSubmission);
        expect(receiveStub).toHaveBeenCalledOnce();
    });

    it('should set correct properties on modeling exercise update when submitting', () => {
        comp.submission = <ModelingSubmission>(<unknown>{
            id: 1,
            model: '{"elements": [{"id": 1}]}',
            submitted: true,
            participation,
        });
        const updateStub = jest.spyOn(service, 'update').mockReturnValue(of(new HttpResponse({ body: submission })));
        comp.modelingExercise = new ModelingExercise(UMLDiagramType.DeploymentDiagram, undefined, undefined);
        comp.modelingExercise.id = 1;
        fixture.detectChanges();
        comp.submit();
        expect(updateStub).toHaveBeenCalledOnce();
        expect(comp.submission).toEqual(submission);
    });

    it('should calculate number of elements from model', () => {
        const elements = [{ id: 1 }, { id: 2 }, { id: 3 }];
        const relationships = [{ id: 4 }, { id: 5 }];
        submission.model = JSON.stringify({ elements, relationships });
        comp.submission = submission;
        fixture.detectChanges();
        expect(comp.calculateNumberOfModelElements()).toBe(elements.length + relationships.length);
    });

    it('should update selected entities with given elements', () => {
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
        fixture.detectChanges();
        comp.onSelectionChanged(selection);
        expect(comp.selectedRelationships).toEqual(['relationShip1', 'relationShip2']);
        expect(comp.selectedEntities).toEqual(['ownerId1', 'ownerId2', 'elementId1', 'elementId2']);
    });

    it('should shouldBeDisplayed return true if no selectedEntities and selectedRelationships', () => {
        const feedback = <Feedback>(<unknown>{ referenceType: 'Activity', referenceId: '5' });
        comp.selectedEntities = [];
        comp.selectedRelationships = [];
        fixture.detectChanges();
        expect(comp.shouldBeDisplayed(feedback)).toBeTrue();
        comp.selectedEntities = ['3'];
        fixture.detectChanges();
        expect(comp.shouldBeDisplayed(feedback)).toBeFalse();
    });

    it('should shouldBeDisplayed return true if feedback reference is in selectedEntities or selectedRelationships', () => {
        const id = 'referenceId';
        const feedback = <Feedback>(<unknown>{ referenceType: 'Activity', referenceId: id });
        comp.selectedEntities = [id];
        comp.selectedRelationships = [];
        fixture.detectChanges();
        expect(comp.shouldBeDisplayed(feedback)).toBeTrue();
        comp.selectedEntities = [];
        comp.selectedRelationships = [id];
        fixture.detectChanges();
        expect(comp.shouldBeDisplayed(feedback)).toBeFalse();
    });

    it('should update submission with current values', () => {
        const model = <UMLModel>(<unknown>{
            elements: [<UMLElement>(<unknown>{ owner: 'ownerId1', id: 'elementId1' }), <UMLElement>(<unknown>{ owner: 'ownerId2', id: 'elementId2' })],
        });
        const currentModelStub = jest.spyOn(comp.modelingEditor, 'getCurrentModel').mockReturnValue(model as UMLModel);
        comp.explanation = 'Explanation Test';
        comp.updateSubmissionWithCurrentValues();
        expect(currentModelStub).toHaveBeenCalledTimes(2);
        expect(comp.hasElements).toBeTrue();
        expect(comp.submission).toBeDefined();
        expect(comp.submission.model).toBe(JSON.stringify(model));
        expect(comp.submission.explanationText).toBe('Explanation Test');
    });

    it('should display the feedback text properly', () => {
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
        const currentModel = <UMLModel>(<unknown>{
            elements: [<UMLElement>(<unknown>{ owner: 'ownerId1', id: 'elementId1' }), <UMLElement>(<unknown>{ owner: 'ownerId2', id: 'elementId2' })],
            version: 'version',
        });
        const unsavedModel = <UMLModel>(<unknown>{
            elements: [<UMLElement>(<unknown>{ owner: 'ownerId1', id: 'elementId1' })],
            version: 'version',
        });

        const currentModelStub = jest.spyOn(comp.modelingEditor, 'getCurrentModel').mockReturnValue(currentModel as UMLModel);
        jest.spyOn(comp.modelingEditor, 'isApollonEditorMounted', 'get').mockReturnValue(true);
        comp.submission = submission;
        comp.submission.model = JSON.stringify(unsavedModel);

        const canDeactivate = comp.canDeactivate();

        expect(currentModelStub).toHaveBeenCalledOnce();
        expect(canDeactivate).toBeFalse();
    });

    it('should set isChanged property to false after saving', () => {
        comp.submission = <ModelingSubmission>(<unknown>{
            id: 1,
            model: '{"elements": [{"id": 1}]}',
            submitted: true,
            participation,
        });
        comp.isChanged = true;
        jest.spyOn(service, 'update').mockReturnValue(of(new HttpResponse({ body: submission })));
        comp.modelingExercise = new ModelingExercise(UMLDiagramType.DeploymentDiagram, undefined, undefined);
        comp.modelingExercise.id = 1;
        fixture.detectChanges();
        comp.saveDiagram();
        expect(comp.isChanged).toBeFalse();
    });

    it('should mark the subsequent feedback', () => {
        comp.assessmentResult = new Result();

        const gradingInstruction = {
            id: 1,
            credits: 1,
            gradingScale: 'scale',
            instructionDescription: 'description',
            feedback: 'instruction feedback',
            usageCount: 1,
        } as GradingInstruction;

        const feedbacks = [
            {
                id: 1,
                detailText: 'feedback1',
                credits: 1,
                gradingInstruction,
                type: FeedbackType.MANUAL_UNREFERENCED,
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

        comp.assessmentResult.feedbacks = feedbacks;

        const unreferencedFeedback = comp.unreferencedFeedback;
        const referencedFeedback = comp.referencedFeedback;

        expect(unreferencedFeedback).toBeDefined();
        expect(unreferencedFeedback).toHaveLength(1);
        expect(unreferencedFeedback![0].isSubsequent).toBeUndefined();
        expect(referencedFeedback).toBeDefined();
        expect(referencedFeedback).toHaveLength(1);
        expect(referencedFeedback![0].isSubsequent).toBeTrue();
    });

    it('should be set up with input values if present instead of loading new values from server', () => {
        // @ts-ignore method is private
        const setUpComponentWithInputValuesSpy = jest.spyOn(comp, 'setupComponentWithInputValues');
        const getDataForFileUploadEditorSpy = jest.spyOn(service, 'getLatestSubmissionForModelingEditor');
        const modelingSubmission = submission;
        modelingSubmission.model = JSON.stringify({
            elements: [
                {
                    content: 'some element',
                },
            ],
        });
        comp.inputExercise = participation.exercise;
        comp.inputSubmission = modelingSubmission;
        comp.inputParticipation = participation;

        fixture.detectChanges();

        expect(setUpComponentWithInputValuesSpy).toHaveBeenCalledOnce();
        expect(comp.modelingExercise).toEqual(participation.exercise);
        expect(comp.submission).toEqual(modelingSubmission);
        expect(comp.participation).toEqual(participation);
        expect(comp.umlModel).toBeTruthy();
        expect(comp.hasElements).toBeTrue();

        // should not fetch additional information from server, reason for input values!
        expect(getDataForFileUploadEditorSpy).not.toHaveBeenCalled();
    });
});
