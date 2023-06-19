import { ComponentFixture, TestBed, fakeAsync, flush, tick, waitForAsync } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FormsModule } from '@angular/forms';
import { HttpResponse } from '@angular/common/http';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { MockPipe } from 'ng-mocks';
import { ChatbotPopupComponent } from 'app/iris/exercise-chatbot/chatbot-popup/chatbot-popup.component';
import { ExerciseChatWidgetComponent } from 'app/iris/exercise-chatbot/exercise-chatwidget/exercise-chat-widget.component';
import { IrisStateStore } from 'app/iris/state-store.service';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule } from '@angular/material/dialog';
import { IrisHttpMessageService } from 'app/iris/http-message.service';
import {
    ActiveConversationMessageLoadedAction,
    NumNewMessagesResetAction,
    RateMessageSuccessAction,
    SessionReceivedAction,
    StudentMessageSentAction,
} from 'app/iris/state-store.model';
import { of, throwError } from 'rxjs';
import { mockClientMessage, mockServerMessage } from '../../../helpers/sample/iris-sample-data';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { IrisMessageContent, IrisMessageContentType } from 'app/entities/iris/iris-content-type.model';
import { IrisMessage, IrisSender, IrisServerMessage } from 'app/entities/iris/iris-message.model';
import { IrisErrorMessageKey, IrisErrorType } from 'app/entities/iris/iris-errors.model';
import { By } from '@angular/platform-browser';

describe('ExerciseChatWidgetComponent', () => {
    let component: ExerciseChatWidgetComponent;
    let fixture: ComponentFixture<ExerciseChatWidgetComponent>;
    let stateStore: IrisStateStore;
    let mockHttpMessageService: IrisHttpMessageService;
    let mockDialog: MatDialog;

    beforeEach(async () => {
        mockDialog = {
            open: jest.fn().mockReturnValue({
                afterClosed: jest.fn(),
                close: jest.fn(),
            }),
            closeAll: jest.fn(),
        } as unknown as MatDialog;

        mockHttpMessageService = {
            createMessage: jest.fn(),
            rateMessage: jest.fn(),
        } as any;

        stateStore = new IrisStateStore();

        await TestBed.configureTestingModule({
            imports: [FormsModule, FontAwesomeModule, MatDialogModule],
            declarations: [ExerciseChatWidgetComponent, ChatbotPopupComponent, MockPipe(ArtemisTranslatePipe), MockPipe(HtmlForMarkdownPipe)],
            providers: [
                { provide: MAT_DIALOG_DATA, useValue: { stateStore: stateStore } },
                { provide: IrisHttpMessageService, useValue: mockHttpMessageService },
                { provide: MatDialog, useValue: mockDialog },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseChatWidgetComponent);
                component = fixture.componentInstance;
                fixture.nativeElement.querySelector('.chat-body').scrollTo = jest.fn();
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should add user message on send', waitForAsync(async () => {
        // given
        jest.spyOn(stateStore, 'dispatch');
        component.newMessageTextContent = 'Hello';

        // when
        component.onSend();

        await fixture.whenStable();

        // then
        expect(component.messages).toContain('Hello');
        expect(stateStore.dispatch).toHaveBeenCalledWith(
            new StudentMessageSentAction({
                sender: IrisSender.USER,
                content: [
                    {
                        type: IrisMessageContentType.TEXT,
                        textContent: 'Hello',
                    },
                ],
            }),
        );
    }));

    it('should clear newMessage on send', async () => {
        component.newMessageTextContent = 'Hello';

        component.onSend();

        expect(component.newMessageTextContent).toBe('');
    });

    it('should handle an error and dispatch ConversationErrorOccurredAction', fakeAsync(() => {
        stateStore.dispatch(new SessionReceivedAction(component.sessionId, []));
        tick();
        const message = 'Hello';
        const error = { fatal: false, key: 'artemisApp.exerciseChatbot.errors.sendMessageFailed' };
        const mockMessage = {
            sender: component.SENDER_USER,
            content: [
                {
                    type: 'TEXT',
                    textContent: 'Hello',
                },
            ],
        };
        jest.spyOn(mockHttpMessageService, 'createMessage').mockReturnValue(
            throwError({
                status: 500,
            }),
        );
        jest.spyOn(mockHttpMessageService, 'createMessage');
        jest.spyOn(component, 'scrollToBottom');
        jest.spyOn(stateStore, 'dispatchAndThen');

        component.newMessageTextContent = message;

        tick();
        fixture.detectChanges();
        component.onSend();

        tick();
        fixture.detectChanges();

        expect(stateStore.dispatchAndThen).toHaveBeenCalled();
        expect(component.newMessageTextContent).toBe('');
        expect(component.error).toEqual(error);
        expect(component.scrollToBottom).toHaveBeenCalled();
    }));

    it('should not send a message if newMessageTextContent is empty', async () => {
        jest.spyOn(component, 'scrollToBottom');
        jest.spyOn(mockHttpMessageService, 'createMessage');
        jest.spyOn(stateStore, 'dispatchAndThen');

        await component.onSend();

        expect(stateStore.dispatchAndThen).not.toHaveBeenCalled();
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

    it('should set the appropriate message styles based on the sender', async () => {
        stateStore.dispatch(new SessionReceivedAction(123, [mockClientMessage, mockServerMessage]));

        fixture.detectChanges();

        const chatBodyElement: HTMLElement = fixture.nativeElement.querySelector('.chat-body');
        const clientChats = chatBodyElement.querySelectorAll('.client-chat');
        const myChats = chatBodyElement.querySelectorAll('.my-chat');

        expect(clientChats).toHaveLength(1);
        expect(myChats).toHaveLength(1);
    });

    it('should render rate message buttons for server responses only', () => {
        stateStore.dispatch(new SessionReceivedAction(123, [mockClientMessage, mockServerMessage, mockServerMessage]));

        fixture.detectChanges();

        const chatBodyElement: HTMLElement = fixture.nativeElement.querySelector('.chat-body');
        const myChats = chatBodyElement.querySelectorAll('.my-chat');
        const clientChats = chatBodyElement.querySelectorAll('.client-chat');
        const buttons = chatBodyElement.querySelectorAll('.rate-message-buttons');

        expect(myChats).toHaveLength(1);
        expect(clientChats).toHaveLength(2);
        expect(buttons).toHaveLength(2);
    });

    it('should render rate buttons with correct class style', () => {
        const mockMessageContent = {
            textContent: 'Hello, world!',
            type: IrisMessageContentType.TEXT,
        } as IrisMessageContent;
        const helpfulMessage = { sender: IrisSender.LLM, content: [mockMessageContent], helpful: true } as IrisServerMessage;
        const unhelpfulMessage = { sender: IrisSender.LLM, content: [mockMessageContent], helpful: false } as IrisServerMessage;
        const neutralMessage = { sender: IrisSender.LLM, content: [mockMessageContent] } as IrisServerMessage;

        stateStore.dispatch(new SessionReceivedAction(123, [helpfulMessage, unhelpfulMessage, neutralMessage, unhelpfulMessage]));

        fixture.detectChanges();

        const thumbsUpClickedButtons: HTMLInputElement = fixture.debugElement.nativeElement.querySelectorAll('.thumbs-up-clicked');
        const thumbsDownClickedButtons: HTMLInputElement = fixture.debugElement.nativeElement.querySelectorAll('.thumbs-down-clicked');
        const rateNotClickedButtons: HTMLInputElement = fixture.debugElement.nativeElement.querySelectorAll('.rate-button-not-clicked');
        const rateClickableButtons: HTMLInputElement = fixture.debugElement.nativeElement.querySelectorAll('.clickable');
        expect(thumbsUpClickedButtons).toHaveLength(1);
        expect(thumbsDownClickedButtons).toHaveLength(2);
        expect(rateNotClickedButtons).toHaveLength(5);
        expect(rateClickableButtons).toHaveLength(5);
    });

    it('should send request when pressing thumbs up button on a message', async () => {
        jest.spyOn(stateStore, 'dispatch');
        const rateMessageMock = jest.spyOn(mockHttpMessageService, 'rateMessage').mockReturnValueOnce(
            of(
                new HttpResponse<IrisMessage>({
                    status: 200,
                    body: mockServerMessage,
                }),
            ),
        );
        const mockMessageContent = {
            textContent: 'Hello, world!',
            type: IrisMessageContentType.TEXT,
        } as IrisMessageContent;
        const unhelpfulMessage = {
            sender: IrisSender.LLM,
            id: 18,
            content: [mockMessageContent],
            helpful: false,
        } as IrisServerMessage;

        stateStore.dispatch(new SessionReceivedAction(123, [unhelpfulMessage]));
        fixture.detectChanges();

        const button: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('.rate-button-not-clicked');

        button.click();

        expect(rateMessageMock).toHaveBeenCalledWith(123, 18, true);
        expect(stateStore.dispatch).toHaveBeenCalledWith(new RateMessageSuccessAction(0, true));
    });

    it('should send request when pressing thumbs down button on a message', async () => {
        jest.spyOn(stateStore, 'dispatch');
        const rateMessageMock = jest.spyOn(mockHttpMessageService, 'rateMessage').mockReturnValueOnce(
            of(
                new HttpResponse<IrisMessage>({
                    status: 200,
                    body: mockServerMessage,
                }),
            ),
        );
        const mockMessageContent = {
            textContent: 'Hello, world!',
            type: IrisMessageContentType.TEXT,
        } as IrisMessageContent;
        const helpfulMessage = {
            sender: IrisSender.LLM,
            id: 18,
            content: [mockMessageContent],
            helpful: true,
        } as IrisServerMessage;

        stateStore.dispatch(new SessionReceivedAction(123, [helpfulMessage]));
        fixture.detectChanges();

        const button: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('.rate-button-not-clicked');

        button.click();

        expect(rateMessageMock).toHaveBeenCalledWith(123, 18, false);
        expect(stateStore.dispatch).toHaveBeenCalledWith(new RateMessageSuccessAction(0, false));
    });

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

    it('should disable the send button if there is an error', () => {
        component.error = {
            key: IrisErrorMessageKey.SEND_MESSAGE_FAILED,
            fatal: false,
        };

        // when
        fixture.detectChanges();

        // then
        const buttonElement: HTMLElement = fixture.debugElement.nativeElement.querySelector('#sendButton');
        expect(buttonElement.getAttribute('disabled')).not.toBeNull();
    });

    it('should see the resend button if there is a sending error; a retry succeeds', fakeAsync(() => {
        // given
        stateStore.dispatch(new SessionReceivedAction(component.sessionId, [mockClientMessage]));
        component.error = { fatal: false, key: IrisErrorMessageKey.SEND_MESSAGE_FAILED } as IrisErrorType;

        tick(100);
        fixture.detectChanges();

        let resendButton = fixture.debugElement.nativeElement.querySelector('#resendButton');

        expect(resendButton.getAttribute('disabled')).toBeFalsy();

        jest.spyOn(mockHttpMessageService, 'createMessage').mockReturnValue(
            of(
                new HttpResponse<IrisMessage>({
                    status: 200,
                    body: mockServerMessage,
                }),
            ),
        );

        // when
        component.resendMessage(mockClientMessage);

        // then

        tick(100);
        fixture.detectChanges();

        expect(component.error).toBeNull();

        resendButton = fixture.debugElement.nativeElement.querySelector('#resendButton');
        expect(resendButton).toBeNull();
        flush();
    }));

    it('should see the resend button if there is a sending error; a retry fails', fakeAsync(() => {
        // given
        stateStore.dispatch(new SessionReceivedAction(component.sessionId, [mockClientMessage]));
        component.error = { fatal: false, key: IrisErrorMessageKey.SEND_MESSAGE_FAILED } as IrisErrorType;

        tick(100);
        fixture.detectChanges();

        let resendButton = fixture.debugElement.nativeElement.querySelector('#resendButton');

        expect(resendButton.getAttribute('disabled')).toBeFalsy();

        jest.spyOn(mockHttpMessageService, 'createMessage').mockReturnValue(
            throwError({
                status: 500,
            }),
        );

        // when
        component.resendMessage(mockClientMessage);

        // then
        tick(100);
        fixture.detectChanges();

        expect(component.error).not.toBeNull();

        resendButton = fixture.debugElement.nativeElement.querySelector('#resendButton');
        expect(resendButton.getAttribute('disabled')).toBeFalsy();

        const element = fixture.debugElement.query(By.css('.shake-animation')).nativeElement;
        expect(element.classList.contains('shake-animation')).toBeTrue();

        flush();
    }));
});
