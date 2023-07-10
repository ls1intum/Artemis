import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FormsModule } from '@angular/forms';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { MockPipe } from 'ng-mocks';
import { ChatbotPopupComponent } from 'app/iris/exercise-chatbot/chatbot-popup/chatbot-popup.component';
import { ExerciseChatWidgetComponent } from 'app/iris/exercise-chatbot/exercise-chatwidget/exercise-chat-widget.component';
import { IrisStateStore } from 'app/iris/state-store.service';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule } from '@angular/material/dialog';
import { IrisHttpMessageService } from 'app/iris/http-message.service';
import {
    ActiveConversationMessageLoadedAction,
    ConversationErrorOccurredAction,
    NumNewMessagesResetAction,
    SessionReceivedAction,
    StudentMessageSentAction,
} from 'app/iris/state-store.model';
import { throwError } from 'rxjs';
import { mockClientMessage, mockServerMessage } from '../../../helpers/sample/iris-sample-data';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { IrisMessageContentType } from 'app/entities/iris/iris-content-type.model';
import { IrisSender } from 'app/entities/iris/iris-message.model';
import { IrisErrorMessageKey } from 'app/entities/iris/iris-errors.model';

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
                component.shouldLoadGreetingMessage = false;
                fixture.nativeElement.querySelector('.chat-body').scrollTo = jest.fn();
                fixture.detectChanges();
            });
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
            new StudentMessageSentAction(
                {
                    sender: IrisSender.USER,
                    content: [
                        {
                            type: IrisMessageContentType.TEXT,
                            textContent: 'Hello',
                        },
                    ],
                },
                null,
            ),
        );
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
            sender: component.SENDER_USER,
            content: [
                {
                    type: 'TEXT',
                    textContent: 'Hello',
                },
            ],
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
});
