import { ComponentFixture, TestBed, fakeAsync, tick, waitForAsync } from '@angular/core/testing';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { IrisBaseChatbotComponent } from 'app/iris/base-chatbot/iris-base-chatbot.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ChatStatusBarComponent } from 'app/iris/base-chatbot/chat-status-bar/chat-status-bar.component';
import { IrisLogoComponent } from 'app/iris/iris-logo/iris-logo.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateService } from '@ngx-translate/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { UserService } from 'app/core/user/user.service';
import { IrisStatusService } from 'app/iris/iris-status.service';
import { IrisChatHttpService } from 'app/iris/iris-chat-http.service';
import { ChatServiceMode, IrisChatService } from 'app/iris/iris-chat.service';
import { IrisWebsocketService } from 'app/iris/iris-websocket.service';
import { of } from 'rxjs';
import dayjs from 'dayjs/esm';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ButtonComponent } from 'app/shared/components/button.component';
import { FormsModule } from 'app/forms/forms.module';
import {
    mockConversation,
    mockConversationWithNoMessages,
    mockServerMessage,
    mockUserMessageWithContent,
    mockWebsocketServerMessage,
} from '../../../helpers/sample/iris-sample-data';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';
import { IrisErrorMessageKey } from 'app/entities/iris/iris-errors.model';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';

describe('IrisBaseChatbotComponent', () => {
    let component: IrisBaseChatbotComponent;
    let chatService: IrisChatService;
    let httpService: jest.Mocked<IrisChatHttpService>;
    let wsMock: jest.Mocked<IrisWebsocketService>;
    let mockModalService: jest.Mocked<NgbModal>;
    let fixture: ComponentFixture<IrisBaseChatbotComponent>;

    const statusMock = {
        currentRatelimitInfo: jest.fn().mockReturnValue(of({})),
        handleRateLimitInfo: jest.fn(),
        getActiveStatus: jest.fn().mockReturnValue(of({})),
    } as any;
    const mockUserService = {
        acceptIris: jest.fn(),
    } as any;
    let accountMock = {
        userIdentity: { irisAccepted: dayjs() },
    } as any;

    beforeEach(async () => {
        accountMock = {
            userIdentity: { irisAccepted: dayjs() },
        } as any;

        await TestBed.configureTestingModule({
            imports: [FormsModule, FontAwesomeModule, RouterModule, BrowserAnimationsModule],
            declarations: [
                IrisBaseChatbotComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(HtmlForMarkdownPipe),
                MockComponent(ChatStatusBarComponent),
                MockComponent(IrisLogoComponent),
                MockComponent(ButtonComponent),
            ],
            providers: [
                MockProvider(NgbModal),
                { provide: ActivatedRoute, useValue: {} },
                { provide: LocalStorageService, useValue: {} },
                { provide: TranslateService, useValue: {} },
                { provide: SessionStorageService, useValue: {} },
                { provide: HttpClient, useValue: {} },
                { provide: AccountService, useValue: accountMock },
                { provide: UserService, useValue: mockUserService },
                { provide: IrisStatusService, useValue: statusMock },
                MockProvider(IrisChatHttpService),
                MockProvider(IrisWebsocketService),
            ],
        })
            .compileComponents()
            .then(() => {
                jest.spyOn(console, 'error').mockImplementation(() => {});
                global.window ??= window;
                window.scroll = jest.fn();
                window.HTMLElement.prototype.scrollTo = jest.fn();

                fixture = TestBed.createComponent(IrisBaseChatbotComponent);
                chatService = TestBed.inject(IrisChatService);
                httpService = TestBed.inject(IrisChatHttpService) as jest.Mocked<IrisChatHttpService>;
                wsMock = TestBed.inject(IrisWebsocketService) as jest.Mocked<IrisWebsocketService>;
                mockModalService = TestBed.inject(NgbModal) as jest.Mocked<NgbModal>;
                component = fixture.componentInstance;

                fixture.nativeElement.querySelector('.chat-body').scrollTo = jest.fn();
                fixture.detectChanges();
            });
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should set userAccepted to false if user has not accepted the policy', () => {
        accountMock.userIdentity.irisAccepted = undefined;
        component.ngOnInit();
        expect(component.userAccepted).toBeFalse();
    });

    it('should set userAccepted to true if user has accepted the policy', () => {
        component.ngOnInit();
        expect(component.userAccepted).toBeTrue();
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
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of({ body: { ...mockConversation } }));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());

        const content = 'Hello';
        const createdMessage = mockUserMessageWithContent(content);
        jest.spyOn(httpService, 'createMessage').mockReturnValueOnce(of({ body: createdMessage }));

        jest.spyOn(component, 'scrollToBottom').mockImplementation(() => {});

        const stub = jest.spyOn(chatService, 'sendMessage');
        component.newMessageTextContent = content;
        chatService.switchTo(ChatServiceMode.COURSE, 123);

        // when
        component.onSend();

        await fixture.whenStable();

        // then
        expect(component.messages).toContain(createdMessage);
        expect(stub).toHaveBeenCalledWith(content);
    }));

    it('should resend message', waitForAsync(async () => {
        // given
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of({ body: { ...mockConversation } }));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());

        const content = 'Hello';
        const createdMessage = mockUserMessageWithContent(content);
        createdMessage.id = 2;
        jest.spyOn(httpService, 'resendMessage').mockReturnValueOnce(of({ body: createdMessage }));
        jest.spyOn(component, 'scrollToBottom').mockImplementation(() => {});

        const stub = jest.spyOn(chatService, 'sendMessage');
        component.newMessageTextContent = content;
        chatService.switchTo(ChatServiceMode.COURSE, 123);

        // when
        component.resendMessage(createdMessage);

        await fixture.whenStable();

        // then
        expect(component.messages).toContain(createdMessage);
        expect(stub).toHaveBeenCalledWith(content);
    }));

    it('should rate message', waitForAsync(async () => {
        // given
        const id = 123;
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of({ body: { ...mockConversation, id: id } }));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        jest.spyOn(httpService, 'rateMessage').mockReturnValueOnce(of({}));
        jest.spyOn(component, 'scrollToBottom').mockImplementation(() => {});

        const message = mockServerMessage;
        const stub = jest.spyOn(chatService, 'rateMessage');
        chatService.switchTo(ChatServiceMode.COURSE, id);

        // when
        component.rateMessage(message, true);

        //then
        expect(stub).toHaveBeenCalledWith(message, true);
        expect(httpService.rateMessage).toHaveBeenCalledWith(id, message.id, true);
    }));

    it('should clear newMessage on send', async () => {
        // given
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of({ body: { ...mockConversation } }));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());

        const content = 'Hello';
        const createdMessage = mockUserMessageWithContent(content);
        jest.spyOn(httpService, 'createMessage').mockReturnValueOnce(of({ body: createdMessage }));

        jest.spyOn(component, 'scrollToBottom').mockImplementation(() => {});

        component.newMessageTextContent = content;
        chatService.switchTo(ChatServiceMode.COURSE, 123);

        // when
        component.onSend();
        await fixture.whenStable();

        // then
        expect(component.newMessageTextContent).toBe('');
    });

    it('should not send a message if newMessageTextContent is empty', async () => {
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of({ body: { ...mockConversation } }));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());

        jest.spyOn(httpService, 'createMessage');

        jest.spyOn(component, 'scrollToBottom').mockImplementation(() => {});

        chatService.switchTo(ChatServiceMode.COURSE, 123);

        await component.onSend();

        expect(httpService.createMessage).not.toHaveBeenCalled();
        expect(component.newMessageTextContent).toBe('');
        expect(component.scrollToBottom).toHaveBeenCalled();
    });

    it('should set the appropriate message styles based on the sender', fakeAsync(() => {
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of({ body: { ...mockConversation } }));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        jest.spyOn(component, 'scrollToBottom').mockImplementation(() => {});
        component.userAccepted = true;
        chatService.switchTo(ChatServiceMode.COURSE, 123);

        component.ngAfterViewInit();
        fixture.detectChanges();
        fixture.whenStable();
        tick();

        const chatBodyElement: HTMLElement = fixture.nativeElement.querySelector('.chat-body');
        const clientChats = chatBodyElement.querySelectorAll('.bubble-left');
        const myChats = chatBodyElement.querySelectorAll('.bubble-right');

        expect(clientChats).toHaveLength(1);
        expect(myChats).toHaveLength(1);
    }));

    it('should not scroll to bottom when there is no new unread messages', fakeAsync(() => {
        // given
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of({ body: { ...mockConversationWithNoMessages } }));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        jest.spyOn(component, 'checkUnreadMessageScroll');
        jest.spyOn(component, 'scrollToBottom').mockImplementation(() => {});
        chatService.switchTo(ChatServiceMode.COURSE, 123);

        // when
        component.ngAfterViewInit();
        fixture.whenStable();
        tick();

        // then
        expect(component.numNewMessages).toBe(0);
        expect(component.checkUnreadMessageScroll).toHaveBeenCalled();
        expect(component.scrollToBottom).not.toHaveBeenCalled();
    }));

    it('should scroll to bottom when there is new unread messages', fakeAsync(() => {
        // given
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of({ body: { ...mockConversationWithNoMessages } }));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of(mockWebsocketServerMessage));
        jest.spyOn(component, 'checkUnreadMessageScroll');
        jest.spyOn(component, 'scrollToBottom').mockImplementation(() => {});
        chatService.switchTo(ChatServiceMode.COURSE, 123);
        // when
        component.ngAfterViewInit();
        fixture.whenStable();
        tick();

        // then
        expect(component.numNewMessages).toBe(1);
        expect(component.checkUnreadMessageScroll).toHaveBeenCalledTimes(2);
        expect(component.scrollToBottom).toHaveBeenCalled();
    }));

    it('should disable enter key if isLoading and active', () => {
        component.active = true;
        component.isLoading = true;
        const event = new KeyboardEvent('keyup', { key: 'Enter', shiftKey: false });
        jest.spyOn(component, 'onSend');

        component.handleKey(event);

        expect(component.onSend).not.toHaveBeenCalled();
    });

    it('should call onSend if Enter key is pressed without Shift key', () => {
        const event = new KeyboardEvent('keyup', { key: 'Enter', shiftKey: false });
        jest.spyOn(component, 'onSend');

        jest.spyOn(event, 'preventDefault');

        component.handleKey(event);

        expect(event.preventDefault).toHaveBeenCalled();
        expect(component.onSend).toHaveBeenCalled();
    });

    it('should remove selected text and move cursor position if Enter key is pressed with Shift key', () => {
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

        component.adjustTextareaRows();

        // Line height is calculated as follows: 20px (line height) + 4px (padding) = 24px
        // The height of the textarea should be 72px (3 rows * (lineHeight + padding))
        expect(textarea.style.height).toBe('72px'); // Assuming the calculated height is 60px

        // Restore original getters and methods
        scrollHeightGetterSpy.mockRestore();
        getComputedStyleSpy.mockRestore();
        textarea.__defineGetter__('scrollHeight', originalScrollHeightGetter);
        window.getComputedStyle = originalGetComputedStyle;
    });

    it('should disable submit button if isLoading is true', () => {
        component.isLoading = true;
        fixture.detectChanges();
        const sendButton = fixture.debugElement.query(By.css('#irisSendButton')).componentInstance;

        expect(component.checkIfDisabled()).toBeTruthy();
        expect(sendButton.disabled).toBeTruthy();
    });
    it('should not render submit button if userAccepted is false', () => {
        component.userAccepted = false;
        component.isLoading = false;
        component.error = null;
        fixture.detectChanges();
        const sendButton = fixture.debugElement.query(By.css('#irisSendButton'));

        expect(sendButton).toBeNull();
    });

    it('should not disable submit button if isLoading is false and no error exists', () => {
        component.userAccepted = true;
        component.isLoading = false;
        component.error = null;
        fixture.detectChanges();
        const sendButton = fixture.debugElement.query(By.css('#irisSendButton')).componentInstance;

        expect(component.checkIfDisabled()).toBeFalsy();
        expect(sendButton.disabled).toBeFalsy();
    });

    it('should not disable submit button if isLoading is false and error is not fatal', () => {
        component.userAccepted = true;
        component.isLoading = false;
        component.error = { key: IrisErrorMessageKey.SEND_MESSAGE_FAILED, fatal: false };
        fixture.detectChanges();
        const sendButton = fixture.debugElement.query(By.css('#irisSendButton')).componentInstance;

        expect(component.checkIfDisabled()).toBeFalsy();
        expect(sendButton.disabled).toBeFalsy();
    });

    describe('clear chat session', () => {
        it('should open confirm modal when click on the clear button', fakeAsync(() => {
            jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of({ body: { ...mockConversation } }));
            jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
            jest.spyOn(component, 'scrollToBottom').mockImplementation(() => {});
            const openModalStub = jest.spyOn(mockModalService, 'open');
            chatService.switchTo(ChatServiceMode.COURSE, 123);

            fixture.detectChanges();
            tick();
            const button: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#clear-chat-button');

            button.click();
            expect(openModalStub).toHaveBeenCalledOnce();
        }));
        it('should clear chat session when confirm modal is confirmed', fakeAsync(() => {
            jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of({ body: { ...mockConversation } }));
            jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
            jest.spyOn(component, 'scrollToBottom').mockImplementation(() => {});
            jest.spyOn(chatService, 'clearChat').mockReturnValueOnce(of({}));

            const modalRefMock = {
                result: Promise.resolve('confirm'),
            };
            jest.spyOn(mockModalService, 'open').mockReturnValue(modalRefMock);

            chatService.switchTo(ChatServiceMode.COURSE, 123);

            fixture.detectChanges();
            tick();
            const button: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#clear-chat-button');

            button.click();
            tick();

            expect(chatService.clearChat).toHaveBeenCalledOnce();
        }));

        it('should not render clear chat button if the history is empty', () => {
            const button: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#clear-chat-button');
            expect(button).toBeNull();
        });
    });
});
