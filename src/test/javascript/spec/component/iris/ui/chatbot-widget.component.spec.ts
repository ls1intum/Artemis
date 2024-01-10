import dayjs from 'dayjs/esm';
import { ComponentFixture, TestBed, fakeAsync, tick, waitForAsync } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MockPipe } from 'ng-mocks';
import { IrisChatbotWidgetComponent } from 'app/iris/exercise-chatbot/widget/chatbot-widget.component';
import { IrisStateStore } from 'app/iris/state-store.service';
import { AccountService } from 'app/core/auth/account.service';
import { TranslateService } from '@ngx-translate/core';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule } from '@angular/material/dialog';
import {
    ActiveConversationMessageLoadedAction,
    ConversationErrorOccurredAction,
    NumNewMessagesResetAction,
    RateMessageSuccessAction,
    SessionReceivedAction,
    StudentMessageSentAction,
} from 'app/iris/state-store.model';
import { of, throwError } from 'rxjs';
import {
    mockArtemisClientMessage,
    mockClientMessage,
    mockExercisePlan,
    mockExercisePlanStep,
    mockExercisePlanStepSolution,
    mockExercisePlanStepTemplate,
    mockExercisePlanStepTest,
    mockServerMessage,
    mockServerPlanMessage,
} from '../../../helpers/sample/iris-sample-data';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { MockHttpService } from '../../../helpers/mocks/service/mock-http.service';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { IrisSender, IrisUserMessage } from 'app/entities/iris/iris-message.model';
import { IrisErrorMessageKey } from 'app/entities/iris/iris-errors.model';
import { UserService } from 'app/core/user/user.service';
import { IrisHttpMessageService } from 'app/iris/http-message.service';
import { ExecutionStage, ExerciseComponent, IrisExercisePlan, IrisExercisePlanStep, IrisTextMessageContent } from 'app/entities/iris/iris-content-type.model';
import { IrisHttpCodeEditorMessageService } from 'app/iris/http-code-editor-message.service';
import { IrisCodeEditorSessionService } from 'app/iris/code-editor-session.service';

describe('IrisChatbotWidgetComponent', () => {
    let component: IrisChatbotWidgetComponent;
    let fixture: ComponentFixture<IrisChatbotWidgetComponent>;
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
            declarations: [IrisChatbotWidgetComponent, MockPipe(ArtemisTranslatePipe), MockPipe(HtmlForMarkdownPipe)],
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
                fixture = TestBed.createComponent(IrisChatbotWidgetComponent);
                component = fixture.componentInstance;
                component.shouldLoadGreetingMessage = false;
                fixture.nativeElement.querySelector('.chat-body').scrollTo = jest.fn();

                fixture.detectChanges();
            });
    });

    it('should set userAccepted to true if user has accepted the policy', () => {
        jest.spyOn(mockUserService, 'getIrisAcceptedAt').mockReturnValue(of(dayjs()));

        component.ngOnInit();
        expect(component.userAccepted).toBeTrue();
    });

    it('should set userAccepted to false if user has not accepted the policy', () => {
        jest.spyOn(mockUserService, 'getIrisAcceptedAt').mockReturnValue(of(null));
        component.ngOnInit();
        expect(component.userAccepted).toBeFalse();
    });

    it('should call API when user accept the policy', () => {
        const stub = jest.spyOn(mockUserService, 'acceptIris');
        stub.mockReturnValue(of(new HttpResponse<void>()));

        component.acceptPermission();

        expect(stub).toHaveBeenCalledOnce();
        expect(component.userAccepted).toBeTrue();
    });

    it('should add user message on send', waitForAsync(async () => {
        // given
        stateStore.dispatch(new SessionReceivedAction(123, [mockClientMessage, mockServerMessage]));
        jest.spyOn(stateStore, 'dispatchAndThen');
        component.newMessageTextContent = 'Hello';
        const createMessage = { sender: IrisSender.USER, content: [new IrisTextMessageContent('Hello')] } as IrisUserMessage;
        const sessionMock = jest.spyOn(mockCodeEditorSessionService, 'sendMessage');

        // when
        component.onSend();

        await fixture.whenStable();

        // then
        expect(component.messages).toContain('Hello');
        expect(sessionMock).toHaveBeenCalledWith(component.sessionId, createMessage);
        expect(stateStore.dispatchAndThen).toHaveBeenCalledWith(new StudentMessageSentAction(createMessage, null));
    }));

    it('should resend message', waitForAsync(async () => {
        // given
        stateStore.dispatch(new SessionReceivedAction(123, [mockClientMessage, mockServerMessage]));
        jest.spyOn(stateStore, 'dispatchAndThen');
        const resendMessage = { ...mockClientMessage, id: 2 };
        const sessionMock = jest.spyOn(mockCodeEditorSessionService, 'resendMessage');

        // when
        component.resendMessage(resendMessage);

        await fixture.whenStable();

        // then
        expect(component.messages).toContain('Hello, world!');
        expect(sessionMock).toHaveBeenCalledWith(component.sessionId, resendMessage);
        expect(stateStore.dispatchAndThen).toHaveBeenCalledWith(new StudentMessageSentAction(resendMessage, null));
    }));

    it('should rate message', waitForAsync(async () => {
        // given
        stateStore.dispatch(new SessionReceivedAction(123, [mockClientMessage, mockServerMessage]));
        jest.spyOn(stateStore, 'dispatch');
        const sessionMock = jest.spyOn(mockCodeEditorSessionService, 'rateMessage');

        // when
        component.rateMessage(mockServerMessage.id, 1, true);

        await fixture.whenStable();

        // then
        expect(sessionMock).toHaveBeenCalledWith(component.sessionId, mockServerMessage.id, true);
        expect(stateStore.dispatch).toHaveBeenCalledWith(new RateMessageSuccessAction(1, true));
    }));

    it('should execute plan step', waitForAsync(async () => {
        const executeMock = jest.spyOn(component, 'executePlanStep');
        const sessionMock = jest.spyOn(mockCodeEditorSessionService, 'executePlanStep');

        component.executePlanStep(mockServerPlanMessage.id, mockExercisePlanStep);
        await fixture.whenStable();

        const message = component.messages.find((m) => m.id === mockServerPlanMessage.id);
        const planId = mockExercisePlanStep.plan;
        const plan = message!.content.find((p) => p.id === planId) as IrisExercisePlan;
        const step = plan!.steps.find((s) => s.id === mockExercisePlanStep.id);
        expect(executeMock).toHaveBeenCalledWith(mockServerPlanMessage.id, mockExercisePlanStep);
        expect(sessionMock).toHaveBeenCalledWith(component.sessionId, mockServerPlanMessage.id, mockExercisePlanStep.plan, mockExercisePlanStep.id);
        expect(step!.executionStage).toEqual(ExecutionStage.IN_PROGRESS);
    }));

    it('should set executing', () => {
        const setExecuteMock = jest.spyOn(component, 'setExecuting');
        component.setExecuting(mockServerPlanMessage.id, mockExercisePlan);
        fixture.detectChanges();
        expect(setExecuteMock).toHaveBeenCalledWith(mockServerPlanMessage.id, mockExercisePlan);
    });

    it('should clear newMessage on send', async () => {
        component.newMessageTextContent = 'Hello';

        component.onSend();

        expect(component.newMessageTextContent).toBe('');
    });

    it('should handle an error and dispatch ConversationErrorOccurredAction', waitForAsync(async () => {
        const message = 'Hello';
        const error = 'Something went wrong. Please try again later!';
        const mockMessage = {
            sender: IrisSender.USER,
            content: [new IrisTextMessageContent('Hello')],
        };
        jest.spyOn(mockHttpCodeEditorMessageService, 'createMessage').mockReturnValueOnce(
            throwError({
                status: 500,
            }),
        );
        jest.spyOn(component, 'scrollToBottom');
        jest.spyOn(stateStore, 'dispatchAndThen');

        component.newMessageTextContent = message;
        component.onSend();
        await fixture.whenStable();

        expect(stateStore.dispatchAndThen).toHaveBeenCalled();
        expect(mockHttpCodeEditorMessageService.createMessage).toHaveBeenCalledWith(component.sessionId, mockMessage);
        expect(component.newMessageTextContent).toBe('');
        expect(component.error).toEqual(error);
        expect(component.scrollToBottom).toHaveBeenCalled();
    }));

    it('should not send a message if newMessageTextContent is empty', async () => {
        jest.spyOn(component, 'scrollToBottom');
        jest.spyOn(mockHttpCodeEditorMessageService, 'createMessage');
        jest.spyOn(stateStore, 'dispatchAndThen');

        await component.onSend();

        expect(stateStore.dispatchAndThen).toHaveBeenCalledWith(new ConversationErrorOccurredAction(IrisErrorMessageKey.EMPTY_MESSAGE));
        expect(mockHttpCodeEditorMessageService.createMessage).not.toHaveBeenCalled();
        expect(component.newMessageTextContent).toBe('');
        expect(component.scrollToBottom).toHaveBeenCalled();
    });

    it('should close the dialog', () => {
        component.closeChat();

        expect(mockDialog.closeAll).toHaveBeenCalled();
    });

    it('should increment the number of dots every 500 milliseconds', () => {
        jest.useFakeTimers();

        component.dots = 1;
        component.animateDots();

        jest.advanceTimersByTime(500);
        expect(component.dots).toBe(2);

        jest.advanceTimersByTime(500);
        expect(component.dots).toBe(3);

        jest.advanceTimersByTime(500);
        expect(component.dots).toBe(1);

        jest.useRealTimers();
    });

    it('should set the appropriate message styles based on the sender', waitForAsync(async () => {
        stateStore.dispatch(new SessionReceivedAction(123, [mockClientMessage, mockServerMessage]));

        fixture.detectChanges();
        await fixture.whenStable();

        const chatBodyElement: HTMLElement = fixture.nativeElement.querySelector('.chat-body');
        const clientChats = chatBodyElement.querySelectorAll('.client-chat');
        const myChats = chatBodyElement.querySelectorAll('.my-chat');

        expect(clientChats).toHaveLength(1);
        expect(myChats).toHaveLength(1);
    }));

    it('should render unread message line with correct position', () => {
        // given
        stateStore.dispatch(new SessionReceivedAction(123, [mockClientMessage, mockServerMessage])); // 2 old messages
        stateStore.dispatch(new ActiveConversationMessageLoadedAction(mockServerMessage)); // 1 new message

        // when
        component.ngAfterViewInit();

        // then
        expect(component.unreadMessageIndex).toBe(2);

        fixture.detectChanges();
        const unreadMessageLine: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('.unread-message');
        expect(unreadMessageLine).not.toBeNull();
    });

    it('should scroll to unread message position when there is new unread messages', () => {
        // given
        jest.spyOn(component, 'scrollToUnread');
        jest.spyOn(component, 'scrollToBottom');
        stateStore.dispatch(new SessionReceivedAction(123, [mockClientMessage, mockServerMessage])); // 2 old messages
        stateStore.dispatch(new ActiveConversationMessageLoadedAction(mockServerMessage)); // 1 new message

        // when
        component.ngAfterViewInit();

        // then
        expect(component.numNewMessages).toBe(1);
        expect(component.scrollToUnread).toHaveBeenCalled();
        expect(component.scrollToBottom).not.toHaveBeenCalled();
    });

    it('should scroll to bottom when there is no new unread messages', () => {
        // given
        jest.spyOn(component, 'scrollToUnread');
        jest.spyOn(component, 'scrollToBottom');
        stateStore.dispatch(new SessionReceivedAction(123, [mockClientMessage, mockServerMessage])); // 2 old messages

        // when
        component.ngAfterViewInit();

        // then
        expect(component.numNewMessages).toBe(0);
        expect(component.scrollToBottom).toHaveBeenCalled();
        expect(component.scrollToUnread).not.toHaveBeenCalled();
    });

    it('should call action to reset number of new messages when close chat', () => {
        // given
        jest.spyOn(stateStore, 'dispatch');

        // when
        component.closeChat();

        // then
        expect(stateStore.dispatch).toHaveBeenCalledWith(new NumNewMessagesResetAction());
    });

    it('should disable enter key if deactivateSubmitButton is true', () => {
        jest.spyOn(component, 'deactivateSubmitButton').mockReturnValue(true);
        const event = new KeyboardEvent('keyup', { key: 'Enter', shiftKey: false });
        jest.spyOn(component, 'onSend');

        component.handleKey(event);

        expect(component.onSend).not.toHaveBeenCalled();
    });

    it('should call onSend if Enter key is pressed without Shift key', () => {
        jest.spyOn(component, 'deactivateSubmitButton').mockReturnValue(false);
        const event = new KeyboardEvent('keyup', { key: 'Enter', shiftKey: false });
        jest.spyOn(component, 'onSend');

        jest.spyOn(event, 'preventDefault');

        component.handleKey(event);

        expect(event.preventDefault).toHaveBeenCalled();
        expect(component.onSend).toHaveBeenCalled();
    });

    it('should remove selected text and move cursor position if Enter key is pressed with Shift key', () => {
        jest.spyOn(component, 'deactivateSubmitButton').mockReturnValue(false);
        const event = new KeyboardEvent('keyup', { key: 'Enter', shiftKey: true });
        const textAreaElement = document.createElement('textarea');
        const selectionStart = 6;
        const selectionEnd = 10;
        textAreaElement.value = 'Sample text';
        textAreaElement.selectionStart = selectionStart;
        textAreaElement.selectionEnd = selectionEnd;
        jest.spyOn(event, 'target', 'get').mockReturnValue(textAreaElement);

        component.handleKey(event);

        const expectedValue = 'Samplet';
        const expectedSelectionStart = selectionStart + 1;
        const expectedSelectionEnd = selectionStart + 1;

        // Trigger the appropriate input events to simulate user input
        const inputEvent = new Event('input', { bubbles: true, cancelable: true });
        textAreaElement.dispatchEvent(inputEvent);

        expect(textAreaElement.value).toBe(expectedValue);
        expect(textAreaElement.selectionStart).toBe(expectedSelectionStart);
        expect(textAreaElement.selectionEnd).toBe(expectedSelectionEnd);
    });

    it('should adjust textarea rows and call adjustChatBodyHeight', () => {
        const textarea = fixture.nativeElement.querySelector('textarea');
        const originalScrollHeightGetter = textarea.__lookupGetter__('scrollHeight');
        const originalGetComputedStyle = window.getComputedStyle;

        const scrollHeightGetterSpy = jest.spyOn(textarea, 'scrollHeight', 'get').mockReturnValue(100);
        const getComputedStyleSpy = jest.spyOn(window, 'getComputedStyle').mockImplementation(
            () =>
                ({
                    lineHeight: '20px',
                }) as Partial<CSSStyleDeclaration> as any,
        );

        jest.spyOn(component, 'adjustChatBodyHeight');

        component.adjustTextareaRows();

        expect(textarea.style.height).toBe('60px'); // Assuming the calculated height is 60px
        expect(component.adjustChatBodyHeight).toHaveBeenCalledWith(3); // Assuming lineHeight is 20px, scrollHeight is 100, and maxRows is 3

        // Restore original getters and methods
        scrollHeightGetterSpy.mockRestore();
        getComputedStyleSpy.mockRestore();
        textarea.__defineGetter__('scrollHeight', originalScrollHeightGetter);
        window.getComputedStyle = originalGetComputedStyle;
    });

    it('should load greeting message if shouldLoadGreetingMessage is true', async () => {
        component.shouldLoadGreetingMessage = true;
        jest.spyOn(stateStore, 'dispatch');
        component.loadFirstMessage();
        expect(stateStore.dispatch).toHaveBeenCalled();
    });

    it('should return an IrisClientMessage with correct fields', () => {
        const testMessage = 'Test message';
        const expectedResult: IrisUserMessage = {
            sender: IrisSender.USER,
            content: [new IrisTextMessageContent(testMessage)],
        };

        const result = component.newUserMessage(testMessage);

        expect(result).toEqual(expectedResult);
    });

    it('should return true if the error key is SEND_MESSAGE_FAILED', () => {
        component.error = { key: IrisErrorMessageKey.SEND_MESSAGE_FAILED, fatal: false };
        expect(component.isSendMessageFailedError()).toBeTruthy();
    });

    it('should return true if the error key is IRIS_SERVER_RESPONSE_TIMEOUT', () => {
        component.error = { key: IrisErrorMessageKey.IRIS_SERVER_RESPONSE_TIMEOUT, fatal: false };
        expect(component.isSendMessageFailedError()).toBeTruthy();
    });

    it('should return false if the error key is neither SEND_MESSAGE_FAILED nor IRIS_SERVER_RESPONSE_TIMEOUT', () => {
        component.error = { key: IrisErrorMessageKey.IRIS_DISABLED, fatal: false };
        expect(component.isSendMessageFailedError()).toBeFalsy();
    });

    it('should return false if there is no error', () => {
        component.error = null;
        expect(component.isSendMessageFailedError()).toBeFalsy();
    });

    it('should return true if error key is EMPTY_MESSAGE', () => {
        component.error = { key: IrisErrorMessageKey.EMPTY_MESSAGE, fatal: true };
        expect(component.isEmptyMessageError()).toBeTruthy();
    });

    it('should return false if error key is not EMPTY_MESSAGE', () => {
        component.error = { key: IrisErrorMessageKey.IRIS_DISABLED, fatal: false };
        expect(component.isEmptyMessageError()).toBeFalsy();
    });

    it('should return false if there is no error for empty message', () => {
        component.error = null;
        expect(component.isEmptyMessageError()).toBeFalsy();
    });

    it('should disable submit button if isLoading is true', () => {
        component.isLoading = true;
        fixture.detectChanges();
        const sendButton: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#sendButton');

        expect(component.deactivateSubmitButton()).toBeTruthy();
        expect(sendButton.disabled).toBeTruthy();
    });

    it('should disable submit button if error exists and it is fatal', () => {
        component.error = { key: IrisErrorMessageKey.SEND_MESSAGE_FAILED, fatal: true };
        fixture.detectChanges();
        const sendButton: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#sendButton');

        expect(component.deactivateSubmitButton()).toBeTruthy();
        expect(sendButton.disabled).toBeTruthy();
    });

    it('should disable submit button if userAccepted is false', () => {
        component.userAccepted = false;
        component.isLoading = false;
        component.error = null;
        fixture.detectChanges();
        const sendButton: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#sendButton');

        expect(component.deactivateSubmitButton()).toBeTruthy();
        expect(sendButton.disabled).toBeTruthy();
    });

    it('should not disable submit button if isLoading is false and no error exists', () => {
        component.userAccepted = true;
        component.isLoading = false;
        component.error = null;
        fixture.detectChanges();
        const sendButton: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#sendButton');

        expect(component.deactivateSubmitButton()).toBeFalsy();
        expect(sendButton.disabled).toBeFalsy();
    });

    it('should not disable submit button if isLoading is false and error is not fatal', () => {
        component.userAccepted = true;
        component.isLoading = false;
        component.error = { key: IrisErrorMessageKey.SEND_MESSAGE_FAILED, fatal: false };
        fixture.detectChanges();
        const sendButton: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#sendButton');

        expect(component.deactivateSubmitButton()).toBeFalsy();
        expect(sendButton.disabled).toBeFalsy();
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

    describe('clear chat session', () => {
        it('should call service to clear old session and create a new one', () => {
            jest.spyOn(mockCodeEditorSessionService, 'createNewSession');
            component.exerciseId = 18;

            component.createNewSession();

            expect(mockCodeEditorSessionService.createNewSession).toHaveBeenCalledWith(18);
        });

        it('should open confirm modal when click on the clear button', () => {
            stateStore.dispatch(new SessionReceivedAction(123, [mockClientMessage, mockServerMessage]));
            fixture.detectChanges();
            const button: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#clear-chat-button');

            button.click();

            const openModalStub = jest.spyOn(mockModalService, 'open');
            expect(openModalStub).toHaveBeenCalledOnce();
        });

        it('should not render clear chat button if the history is empty', () => {
            const button: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#clear-chat-button');
            expect(button).toBeNull();
        });

        it('should not render clear chat button if the history has only one message from the client', () => {
            stateStore.dispatch(new SessionReceivedAction(123, [mockArtemisClientMessage]));
            fixture.detectChanges();
            const button: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#clear-chat-button');

            expect(button).toBeNull();
        });

        it('should render clear chat button if the history has one message from server', () => {
            stateStore.dispatch(new SessionReceivedAction(123, [mockServerMessage]));
            fixture.detectChanges();
            const button: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#clear-chat-button');

            expect(button).not.toBeNull();
        });

        it('should render clear chat button if the history has one message from user', () => {
            stateStore.dispatch(new SessionReceivedAction(123, [mockClientMessage]));
            fixture.detectChanges();
            const button: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#clear-chat-button');

            expect(button).not.toBeNull();
        });
    });

    it('should pass parameters to the pipe when error action is dispatched', fakeAsync(() => {
        jest.spyOn(component, 'getConvertedErrorMap');
        // given
        stateStore.dispatch(new SessionReceivedAction(123, [mockClientMessage, mockServerMessage]));
        tick();

        // when
        const map = new Map<string, any>();
        map.set('model', 'gpt-4');
        stateStore.dispatch(new ConversationErrorOccurredAction(IrisErrorMessageKey.NO_MODEL_AVAILABLE, map));
        tick();
        fixture.detectChanges();

        // then
        expect(component.getConvertedErrorMap).toHaveBeenCalled();
    }));
});
