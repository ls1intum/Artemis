import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MockPipe } from 'ng-mocks';
import { IrisStateStore } from 'app/iris/state-store.service';
import { AccountService } from 'app/core/auth/account.service';
import { TranslateService } from '@ngx-translate/core';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule } from '@angular/material/dialog';
import { SessionReceivedAction } from 'app/iris/state-store.model';
import {
    mockClientMessage,
    mockExercisePlan,
    mockExercisePlanStep,
    mockExercisePlanStepSolution,
    mockExercisePlanStepTemplate,
    mockExercisePlanStepTest,
    mockServerPlanMessage,
} from '../../../helpers/sample/iris-sample-data';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { MockHttpService } from '../../../helpers/mocks/service/mock-http.service';
import { HttpClient } from '@angular/common/http';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { IrisErrorMessageKey } from 'app/entities/iris/iris-errors.model';
import { UserService } from 'app/core/user/user.service';
import { IrisHttpMessageService } from 'app/iris/http-message.service';
import { ExecutionStage, ExerciseComponent, IrisExercisePlan, IrisExercisePlanStep } from 'app/entities/iris/iris-content-type.model';
import { IrisHttpCodeEditorMessageService } from 'app/iris/http-code-editor-message.service';
import { IrisCodeEditorSessionService } from 'app/iris/code-editor-session.service';
import { IrisChatbotDialogWidgetComponent } from 'app/iris/exercise-chatbot/widget/chatbot-dialog-widget.component';
import { ExerciseCreationWidgetComponent } from 'app/iris/exercise-chatbot/widget/exercise-creation-widget.component';

describe('IrisChatbotDialogWidgetComponent', () => {
    let component: IrisChatbotDialogWidgetComponent;
    let fixture: ComponentFixture<IrisChatbotDialogWidgetComponent>;
    let stateStore: IrisStateStore;
    let mockHttpCodeEditorMessageService: IrisHttpCodeEditorMessageService;
    let mockCodeEditorSessionService: IrisCodeEditorSessionService;
    let mockDialog: MatDialog;
    let mockModalService: NgbModal;
    let mockUserService: UserService;

    beforeEach(async () => {
        mockDialog = {
            open: jest.fn().mockReturnValue({
                afterClosed: jest.fn(),
                close: jest.fn(),
            }),
            closeAll: jest.fn(),
        } as unknown as MatDialog;

        mockHttpCodeEditorMessageService = {
            createMessage: jest.fn(),
            resendMessage: jest.fn(),
            rateMessage: jest.fn(),
            executePlanStep: jest.fn(),
        } as any;

        mockCodeEditorSessionService = {
            createNewSession: jest.fn(),
            sendMessage: jest.fn().mockImplementation(async (sessionId, message) => {
                return mockHttpCodeEditorMessageService.createMessage(sessionId, message);
            }),
            resendMessage: jest.fn().mockImplementation(async (sessionId, message) => {
                return mockHttpCodeEditorMessageService.resendMessage(sessionId, message);
            }),
            rateMessage: jest.fn().mockImplementation(async (sessionId, messageId, helpful) => {
                return mockHttpCodeEditorMessageService.rateMessage(sessionId, messageId, helpful);
            }),
            executePlanStep: jest.fn().mockImplementation(async (sessionId, messageId, planId, stepId) => {
                return mockHttpCodeEditorMessageService.executePlanStep(sessionId, messageId, planId, stepId);
            }),
        } as any;

        mockUserService = {
            acceptIris: jest.fn().mockReturnValue({
                subscribe: jest.fn(),
            }),
            getIrisAcceptedAt: jest.fn().mockReturnValue({
                subscribe: jest.fn(),
            }),
        } as any;

        stateStore = new IrisStateStore();
        mockModalService = {
            open: jest.fn(),
        } as any;

        await TestBed.configureTestingModule({
            imports: [FormsModule, FontAwesomeModule, MatDialogModule],
            declarations: [IrisChatbotDialogWidgetComponent, MockPipe(ArtemisTranslatePipe), MockPipe(HtmlForMarkdownPipe), ExerciseCreationWidgetComponent],
            providers: [
                { provide: MAT_DIALOG_DATA, useValue: { stateStore: stateStore, courseId: 1, exerciseId: 1, sessionService: mockCodeEditorSessionService } },
                { provide: IrisHttpMessageService, useValue: mockHttpCodeEditorMessageService },
                { provide: NgbModal, useValue: mockModalService },
                { provide: MatDialog, useValue: mockDialog },
                { provide: ActivatedRoute, useValue: {} },
                { provide: LocalStorageService, useValue: {} },
                { provide: UserService, useValue: mockUserService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: HttpClient, useClass: MockHttpService },
                { provide: AccountService, useClass: MockAccountService },
            ],
        })
            .compileComponents()
            .then(() => {
                jest.spyOn(console, 'error').mockImplementation(() => {});
                global.window ??= window;
                window.scroll = jest.fn();
                window.HTMLElement.prototype.scrollTo = jest.fn();
                fixture = TestBed.createComponent(IrisChatbotDialogWidgetComponent);
                component = fixture.componentInstance;
                component.shouldLoadGreetingMessage = false;
                fixture.nativeElement.querySelector('.chat-body').scrollTo = jest.fn();
                fixture.detectChanges();
            });
    });

    it('should close the dialog', () => {
        component.closeChat();

        expect(mockDialog.closeAll).toHaveBeenCalled();
    });

    it('should set executing', () => {
        const setExecuteMock = jest.spyOn(component, 'setExecuting');
        component.setExecuting(mockServerPlanMessage.id, mockExercisePlan);
        fixture.detectChanges();
        expect(setExecuteMock).toHaveBeenCalledWith(mockServerPlanMessage.id, mockExercisePlan);
    });

    it('should notify step completed', () => {
        stateStore.dispatch(new SessionReceivedAction(123, [mockClientMessage, mockServerPlanMessage]));
        const notifyMock = jest.spyOn(component, 'notifyStepCompleted');
        component.notifyStepCompleted(2, 2, 2);
        fixture.detectChanges();

        const message = component.messages.find((m) => m.id === 2);
        const plan = message!.content.find((p) => p.id === 2) as IrisExercisePlan;
        const step = plan!.steps.find((s) => s.id === 2);
        expect(notifyMock).toHaveBeenCalledWith(2, 2, 2);
        expect(step!.executionStage).toEqual(ExecutionStage.COMPLETE);
    });

    it('should notify step completed without corresponding message', () => {
        stateStore.dispatch(new SessionReceivedAction(123, [mockClientMessage, mockServerPlanMessage]));
        const notifyMock = jest.spyOn(component, 'notifyStepCompleted');
        component.notifyStepCompleted(3, 2, 2);
        fixture.detectChanges();

        const message = component.messages.find((m) => m.id === 3);

        expect(notifyMock).toHaveBeenCalledWith(3, 2, 2);
        expect(message).toBeUndefined();
    });

    it('should notify step completed without corresponding plan', () => {
        stateStore.dispatch(new SessionReceivedAction(123, [mockClientMessage, mockServerPlanMessage]));
        const notifyMock = jest.spyOn(component, 'notifyStepCompleted');
        component.notifyStepCompleted(2, 3, 2);
        fixture.detectChanges();

        const message = component.messages.find((m) => m.id === 2);
        const plan = message!.content.find((p) => p.id === 3);

        expect(notifyMock).toHaveBeenCalledWith(2, 3, 2);
        expect(plan).toBeUndefined();
    });

    it('should notify step completed without corresponding step', () => {
        stateStore.dispatch(new SessionReceivedAction(123, [mockClientMessage, mockServerPlanMessage]));
        const notifyMock = jest.spyOn(component, 'notifyStepCompleted');
        component.notifyStepCompleted(2, 2, 8);
        fixture.detectChanges();

        const message = component.messages.find((m) => m.id === 2);
        const plan = message!.content.find((p) => p.id === 2) as IrisExercisePlan;
        const step = plan!.steps.find((s) => s.id === 8);

        expect(notifyMock).toHaveBeenCalledWith(2, 2, 8);
        expect(step).toBeUndefined();
    });

    it('should notify step failed with indicated errorMessageKey', () => {
        stateStore.dispatch(new SessionReceivedAction(123, [mockClientMessage, mockServerPlanMessage]));
        const notifyMock = jest.spyOn(component, 'notifyStepFailed');
        component.notifyStepFailed(2, 2, 2, IrisErrorMessageKey.INTERNAL_PYRIS_ERROR);
        fixture.detectChanges();

        const message = component.messages.find((m) => m.id === 2);
        const plan = message!.content.find((p) => p.id === 2) as IrisExercisePlan;
        const step = plan!.steps.find((s) => s.id === 2);
        expect(notifyMock).toHaveBeenCalledWith(2, 2, 2, IrisErrorMessageKey.INTERNAL_PYRIS_ERROR);
        expect(step!.executionStage).toEqual(ExecutionStage.FAILED);
        expect(plan.executing).toBeFalse();
    });

    it('should notify step failed without indicated errorMessageKey', () => {
        stateStore.dispatch(new SessionReceivedAction(123, [mockClientMessage, mockServerPlanMessage]));
        const notifyMock = jest.spyOn(component, 'notifyStepFailed');
        component.notifyStepFailed(2, 2, 2);
        fixture.detectChanges();

        const message = component.messages.find((m) => m.id === 2);
        const plan = message!.content.find((p) => p.id === 2) as IrisExercisePlan;
        const step = plan!.steps.find((s) => s.id === 2);
        expect(notifyMock).toHaveBeenCalledWith(2, 2, 2);
        expect(step!.executionStage).toEqual(ExecutionStage.FAILED);
        expect(plan.executing).toBeFalse();
    });

    it('should notify step failed without corresponding message', () => {
        stateStore.dispatch(new SessionReceivedAction(123, [mockClientMessage, mockServerPlanMessage]));
        const notifyMock = jest.spyOn(component, 'notifyStepFailed');
        component.notifyStepFailed(3, 2, 2);
        fixture.detectChanges();

        const message = component.messages.find((m) => m.id === 3);

        expect(notifyMock).toHaveBeenCalledWith(3, 2, 2);
        expect(message).toBeUndefined();
    });

    it('should notify step failed without corresponding plan', () => {
        stateStore.dispatch(new SessionReceivedAction(123, [mockClientMessage, mockServerPlanMessage]));
        const notifyMock = jest.spyOn(component, 'notifyStepFailed');
        component.notifyStepFailed(2, 3, 2);
        fixture.detectChanges();

        const message = component.messages.find((m) => m.id === 2);
        const plan = message!.content.find((p) => p.id === 3);
        expect(notifyMock).toHaveBeenCalledWith(2, 3, 2);
        expect(plan).toBeUndefined();
    });

    it('should notify step failed without corresponding step', () => {
        stateStore.dispatch(new SessionReceivedAction(123, [mockClientMessage, mockServerPlanMessage]));
        const notifyMock = jest.spyOn(component, 'notifyStepFailed');
        component.notifyStepFailed(2, 2, 10);
        fixture.detectChanges();

        const message = component.messages.find((m) => m.id === 2);
        const plan = message!.content.find((p) => p.id === 2) as IrisExercisePlan;
        const step = plan!.steps.find((s) => s.id === 10);
        expect(notifyMock).toHaveBeenCalledWith(2, 2, 10);
        expect(step).toBeUndefined();
    });

    it('should get Pause as step button title', () => {
        const plan = { ...mockExercisePlan, executing: true };
        const getButtonMock = jest.spyOn(component, 'getPlanButtonTitle');
        component.getPlanButtonTitle(plan);

        expect(getButtonMock).toHaveBeenCalledWith(plan);
        expect(getButtonMock).toHaveLastReturnedWith('Pause');
    });

    it('should get Completed as step button title', () => {
        const step = { ...mockExercisePlanStep, executionStage: ExecutionStage.COMPLETE };
        const plan = {
            id: 6,
            steps: [step],
        } as IrisExercisePlan;
        const getButtonMock = jest.spyOn(component, 'getPlanButtonTitle');
        component.getPlanButtonTitle(plan);

        expect(getButtonMock).toHaveBeenCalledWith(plan);
        expect(getButtonMock).toHaveLastReturnedWith('Completed');
    });

    it('should get Retry as step button title', () => {
        const step1 = {
            id: 10,
            plan: 10,
            component: ExerciseComponent.PROBLEM_STATEMENT,
            executionStage: ExecutionStage.COMPLETE,
        };
        const step2 = { ...mockExercisePlanStepSolution, executionStage: ExecutionStage.FAILED };
        const plan = {
            id: 10,
            steps: [step1, step2],
        } as IrisExercisePlan;
        const getButtonMock = jest.spyOn(component, 'getPlanButtonTitle');
        component.getPlanButtonTitle(plan);

        expect(getButtonMock).toHaveBeenCalledWith(plan);
        expect(getButtonMock).toHaveLastReturnedWith('Retry');
    });

    it('should get Execute as step button title', () => {
        const step = {
            id: 9,
            plan: 9,
            component: ExerciseComponent.PROBLEM_STATEMENT,
        } as IrisExercisePlanStep;
        const plan = {
            id: 9,
            steps: [step],
        } as IrisExercisePlan;
        const getButtonMock = jest.spyOn(component, 'getPlanButtonTitle');
        component.getPlanButtonTitle(plan);

        expect(getButtonMock).toHaveBeenCalledWith(plan);
        expect(getButtonMock).toHaveLastReturnedWith('Execute');
    });

    it('should get Resume as step button title', () => {
        const step1 = {
            id: 8,
            plan: 8,
            component: ExerciseComponent.PROBLEM_STATEMENT,
            executionStage: ExecutionStage.COMPLETE,
        };
        const step2 = mockExercisePlanStepTemplate;
        const plan = {
            id: 8,
            steps: [step1, step2],
        } as IrisExercisePlan;
        const getButtonMock = jest.spyOn(component, 'getPlanButtonTitle');
        component.getPlanButtonTitle(plan);

        expect(getButtonMock).toHaveBeenCalledWith(plan);
        expect(getButtonMock).toHaveLastReturnedWith('Resume');
    });

    it('should get problem statement as step name', () => {
        const getStepMock = jest.spyOn(component, 'getStepName');
        component.getStepName(mockExercisePlanStep);
        expect(getStepMock).toHaveBeenCalledWith(mockExercisePlanStep);
        expect(getStepMock).toHaveLastReturnedWith('Problem Statement');
    });

    it('should get template repo as step name', () => {
        const getStepMock = jest.spyOn(component, 'getStepName');
        component.getStepName(mockExercisePlanStepTemplate);
        expect(getStepMock).toHaveBeenCalledWith(mockExercisePlanStepTemplate);
        expect(getStepMock).toHaveLastReturnedWith('Template Repository');
    });

    it('should get solution repo as step name', () => {
        const getStepMock = jest.spyOn(component, 'getStepName');
        component.getStepName(mockExercisePlanStepSolution);
        expect(getStepMock).toHaveBeenCalledWith(mockExercisePlanStepSolution);
        expect(getStepMock).toHaveLastReturnedWith('Solution Repository');
    });

    it('should get test repo as step name', () => {
        const getStepMock = jest.spyOn(component, 'getStepName');
        component.getStepName(mockExercisePlanStepTest);
        expect(getStepMock).toHaveBeenCalledWith(mockExercisePlanStepTest);
        expect(getStepMock).toHaveLastReturnedWith('Test Repository');
    });

    it('should display not executed execution stage color', () => {
        const step = { ...mockExercisePlanStep, executionStage: ExecutionStage.NOT_EXECUTED };
        const getStepMock = jest.spyOn(component, 'getStepColor');
        const getStatusMock = jest.spyOn(component, 'getStepStatus');
        component.getStepColor(step);
        expect(getStepMock).toHaveBeenCalledWith(step);
        expect(getStepMock).toHaveLastReturnedWith('var(--iris-chat-widget-background)');
        component.getStepStatus(step);
        expect(getStatusMock).toHaveBeenCalledWith(step);
        expect(getStatusMock).toHaveLastReturnedWith('');
    });

    it('should display in progress execution stage color', () => {
        const step = { ...mockExercisePlanStep, executionStage: ExecutionStage.IN_PROGRESS };
        const getStepMock = jest.spyOn(component, 'getStepColor');
        const getStatusMock = jest.spyOn(component, 'getStepStatus');
        component.getStepColor(step);
        expect(getStepMock).toHaveBeenCalledWith(step);
        expect(getStepMock).toHaveLastReturnedWith('#ffc107');
        component.getStepStatus(step);
        expect(getStatusMock).toHaveBeenCalledWith(step);
        expect(getStatusMock).toHaveLastReturnedWith('Generating changes, please be patient...');
    });

    it('should display completed execution stage color', () => {
        const step = { ...mockExercisePlanStep, executionStage: ExecutionStage.COMPLETE };
        const getStepMock = jest.spyOn(component, 'getStepColor');
        const getStatusMock = jest.spyOn(component, 'getStepStatus');
        component.getStepColor(step);
        expect(getStepMock).toHaveBeenCalledWith(step);
        expect(getStepMock).toHaveLastReturnedWith('#28a745');
        component.getStepStatus(step);
        expect(getStatusMock).toHaveBeenCalledWith(step);
        expect(getStatusMock).toHaveLastReturnedWith('Changes applied.');
    });

    it('should display failed execution stage color', () => {
        const step = { ...mockExercisePlanStep, executionStage: ExecutionStage.FAILED };
        const getStepMock = jest.spyOn(component, 'getStepColor');
        const getStatusMock = jest.spyOn(component, 'getStepStatus');
        component.getStepColor(step);
        expect(getStepMock).toHaveBeenCalledWith(step);
        expect(getStepMock).toHaveLastReturnedWith('#dc3545');
        component.getStepStatus(step);
        expect(getStatusMock).toHaveBeenCalledWith(step);
        expect(getStatusMock).toHaveLastReturnedWith('Encountered an error.');
    });
});
