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
import { mockArtemisClientMessage, mockClientMessage, mockServerMessage } from '../../../helpers/sample/iris-sample-data';
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
import { IrisTextMessageContent } from 'app/entities/iris/iris-content-type.model';
import { IrisSessionService } from 'app/iris/session.service';

describe('IrisChatbotWidgetComponent', () => {
    let component: IrisChatbotWidgetComponent;
    let fixture: ComponentFixture<IrisChatbotWidgetComponent>;
    let stateStore: IrisStateStore;
    let mockDialog: MatDialog;
    let mockModalService: NgbModal;
    let mockUserService: UserService;
    let mockHttpMessageService: IrisHttpMessageService;
    let mockSessionService: IrisSessionService;

    beforeEach(async () => {
        mockDialog = {
            open: jest.fn().mockReturnValue({
                afterClosed: jest.fn(),
                close: jest.fn(),
            }),
            closeAll: jest.fn(),
        } as unknown as MatDialog;

        mockUserService = {
            acceptIris: jest.fn().mockReturnValue({
                subscribe: jest.fn(),
            }),
            getIrisAcceptedAt: jest.fn().mockReturnValue({
                subscribe: jest.fn(),
            }),
        } as any;

        mockHttpMessageService = {
            createMessage: jest.fn(),
        } as any;

        mockSessionService = {
            createNewSession: jest.fn(),
            sendMessage: jest.fn(),
            resendMessage: jest.fn(),
            rateMessage: jest.fn().mockReturnValue(Promise.resolve()),
        } as any;

        stateStore = new IrisStateStore();
        mockModalService = {
            open: jest.fn(),
        } as any;

        await TestBed.configureTestingModule({
            imports: [FormsModule, FontAwesomeModule, MatDialogModule],
            declarations: [IrisChatbotWidgetComponent, MockPipe(ArtemisTranslatePipe), MockPipe(HtmlForMarkdownPipe)],
            providers: [
                { provide: MAT_DIALOG_DATA, useValue: { stateStore: stateStore, courseId: 1, exerciseId: 1, sessionService: mockSessionService } },
                { provide: IrisHttpMessageService, useValue: mockHttpMessageService },
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
        const sessionMock = jest.spyOn(mockSessionService, 'sendMessage');

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
        const sessionMock = jest.spyOn(mockSessionService, 'resendMessage');

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
        const sessionMock = jest.spyOn(mockSessionService, 'rateMessage');

        // when
        component.rateMessage(mockServerMessage.id, 1, true);

        await fixture.whenStable();

        // then
        expect(sessionMock).toHaveBeenCalledWith(component.sessionId, mockServerMessage.id, true);
        expect(stateStore.dispatch).toHaveBeenCalledWith(new RateMessageSuccessAction(1, true));
    }));

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
        jest.spyOn(mockHttpMessageService, 'createMessage').mockReturnValueOnce(
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
        expect(mockHttpMessageService.createMessage).toHaveBeenCalledWith(component.sessionId, mockMessage);
        expect(component.newMessageTextContent).toBe('');
        expect(component.error).toEqual(error);
        expect(component.scrollToBottom).toHaveBeenCalled();
    }));

    it('should not send a message if newMessageTextContent is empty', async () => {
        jest.spyOn(component, 'scrollToBottom');
        jest.spyOn(mockHttpMessageService, 'createMessage');
        jest.spyOn(stateStore, 'dispatchAndThen');

        await component.onSend();

        expect(stateStore.dispatchAndThen).toHaveBeenCalledWith(new ConversationErrorOccurredAction(IrisErrorMessageKey.EMPTY_MESSAGE));
        expect(mockHttpMessageService.createMessage).not.toHaveBeenCalled();
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

    describe('clear chat session', () => {
        it('should call service to clear old session and create a new one', () => {
            jest.spyOn(mockSessionService, 'createNewSession');
            component.exerciseId = 18;

            component.createNewSession();

            expect(mockSessionService.createNewSession).toHaveBeenCalledWith(18);
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
