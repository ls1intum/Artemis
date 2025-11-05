import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { IrisBaseChatbotComponent } from 'app/iris/overview/base-chatbot/iris-base-chatbot.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ChatStatusBarComponent } from 'app/iris/overview/base-chatbot/chat-status-bar/chat-status-bar.component';
import { IrisLogoComponent } from 'app/iris/overview/iris-logo/iris-logo.component';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { UserService } from 'app/core/user/shared/user.service';
import { IrisStatusService } from 'app/iris/overview/services/iris-status.service';
import { IrisChatHttpService } from 'app/iris/overview/services/iris-chat-http.service';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { IrisWebsocketService } from 'app/iris/overview/services/iris-websocket.service';
import { of } from 'rxjs';
import dayjs from 'dayjs/esm';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import {
    mockClientMessage,
    mockClientMessageWithMemories,
    mockServerMessage,
    mockServerMessageWithMemories,
    mockServerSessionHttpResponse,
    mockServerSessionHttpResponseWithEmptyConversation,
    mockServerSessionHttpResponseWithId,
    mockUserMessageWithContent,
    mockWebsocketClientMessageWithMemories,
    mockWebsocketServerMessage,
    mockWebsocketServerMessageWithMemories,
} from 'test/helpers/sample/iris-sample-data';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';
import { IrisErrorMessageKey } from 'app/iris/shared/entities/iris-errors.model';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { IrisMessage, IrisUserMessage } from 'app/iris/shared/entities/iris-message.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { IrisSessionDTO } from 'app/iris/shared/entities/iris-session-dto.model';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { User } from 'app/core/user/user.model';

describe('IrisBaseChatbotComponent', () => {
    let component: IrisBaseChatbotComponent;
    let fixture: ComponentFixture<IrisBaseChatbotComponent>;

    let chatService: IrisChatService;
    let httpService: jest.Mocked<IrisChatHttpService>;
    let wsMock: jest.Mocked<IrisWebsocketService>;
    let mockModalService: jest.Mocked<NgbModal>;
    let accountService: AccountService;

    const statusMock = {
        currentRatelimitInfo: jest.fn().mockReturnValue(of({})),
        handleRateLimitInfo: jest.fn(),
        getActiveStatus: jest.fn().mockReturnValue(of({})),
    } as any;
    const mockUserService = {
        updateExternalLLMUsageConsent: jest.fn(),
    } as any;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [
                IrisBaseChatbotComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(HtmlForMarkdownPipe),
                MockDirective(TranslateDirective),
                MockComponent(ChatStatusBarComponent),
                MockComponent(IrisLogoComponent),
                MockComponent(ButtonComponent),
            ],
            imports: [FontAwesomeModule, RouterModule, NoopAnimationsModule],
            providers: [
                MockProvider(NgbModal),
                LocalStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                SessionStorageService,
                { provide: HttpClient, useValue: {} },
                { provide: AccountService, useClass: MockAccountService },
                { provide: UserService, useValue: mockUserService },
                { provide: IrisStatusService, useValue: statusMock },
                MockProvider(ActivatedRoute),
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
                chatService.setCourseId(456);
                httpService = TestBed.inject(IrisChatHttpService) as jest.Mocked<IrisChatHttpService>;
                wsMock = TestBed.inject(IrisWebsocketService) as jest.Mocked<IrisWebsocketService>;
                mockModalService = TestBed.inject(NgbModal) as jest.Mocked<NgbModal>;
                accountService = TestBed.inject(AccountService);
                component = fixture.componentInstance;

                accountService.userIdentity.set({ externalLLMUsageAccepted: dayjs() } as User);
                jest.spyOn(accountService, 'getAuthenticationState').mockReturnValue(of());

                fixture.nativeElement.querySelector('.chat-body').scrollTo = jest.fn();
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should set userAccepted to false if user has not accepted the external LLM usage policy', () => {
        accountService.userIdentity.set({ externalLLMUsageAccepted: undefined } as User);
        component.ngOnInit();
        expect(component.userAccepted).toBeFalse();
    });

    it('should set userAccepted to true if user has accepted the external LLM usage policy', () => {
        component.ngOnInit();
        expect(component.userAccepted).toBeTrue();
    });

    it('should call API when user accept the policy', () => {
        const stub = jest.spyOn(mockUserService, 'updateExternalLLMUsageConsent');
        stub.mockReturnValue(of(new HttpResponse<void>()));

        component.acceptPermission();

        expect(stub).toHaveBeenCalledOnce();
        expect(component.userAccepted).toBeTrue();
    });

    it('should add user message on send', async () => {
        // given
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponse));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        const getChatSessionsSpy = jest.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));

        const content = 'Hello';
        const createdMessage = mockUserMessageWithContent(content);
        jest.spyOn(httpService, 'createMessage').mockReturnValueOnce(of({ body: createdMessage } as HttpResponse<IrisUserMessage>));

        jest.spyOn(component, 'scrollToBottom').mockImplementation(() => {});

        const stub = jest.spyOn(chatService, 'sendMessage');
        component.newMessageTextContent = content;
        chatService.switchTo(ChatServiceMode.COURSE, 123);

        // when
        component.onSend();

        await fixture.whenStable();

        // then
        expect(component.messages).toContainEqual(createdMessage);
        expect(stub).toHaveBeenCalledWith(content);
        expect(getChatSessionsSpy).toHaveBeenCalledOnce();
    });

    it('should resend message', async () => {
        // given
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponse));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        const getChatSessionsSpy = jest.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));

        const content = 'Hello';
        const createdMessage = mockUserMessageWithContent(content);
        createdMessage.id = 2;
        jest.spyOn(httpService, 'resendMessage').mockReturnValueOnce(of({ body: createdMessage } as HttpResponse<IrisUserMessage>));
        jest.spyOn(component, 'scrollToBottom').mockImplementation(() => {});

        const stub = jest.spyOn(chatService, 'resendMessage');
        component.newMessageTextContent = content;
        chatService.switchTo(ChatServiceMode.COURSE, 123);

        // when
        component.resendMessage(createdMessage);

        await fixture.whenStable();

        // then
        expect(component.messages).toContainEqual(createdMessage);
        expect(stub).toHaveBeenCalledWith(createdMessage);
        expect(getChatSessionsSpy).toHaveBeenCalledOnce();
    });

    it('should rate message', async () => {
        // given
        const id = 123;
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        jest.spyOn(httpService, 'rateMessage').mockReturnValueOnce(of({} as HttpResponse<IrisMessage>));
        jest.spyOn(component, 'scrollToBottom').mockImplementation(() => {});
        const getChatSessionsSpy = jest.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));

        const message = mockServerMessage;
        const stub = jest.spyOn(chatService, 'rateMessage');
        chatService.switchTo(ChatServiceMode.COURSE, id);

        // when
        component.rateMessage(message, true);

        await fixture.whenStable();

        //then
        expect(stub).toHaveBeenCalledWith(message, true);
        expect(httpService.rateMessage).toHaveBeenCalledWith(id, message.id, true);
        expect(getChatSessionsSpy).toHaveBeenCalledOnce();
    });

    it('should clear newMessage on send', async () => {
        // given
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponse));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        const getChatSessionsSpy = jest.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));

        const content = 'Hello';
        const createdMessage = mockUserMessageWithContent(content);
        jest.spyOn(httpService, 'createMessage').mockReturnValueOnce(of({ body: createdMessage } as HttpResponse<IrisUserMessage>));

        jest.spyOn(component, 'scrollToBottom').mockImplementation(() => {});

        component.newMessageTextContent = content;
        chatService.switchTo(ChatServiceMode.COURSE, 123);

        // when
        component.onSend();
        await fixture.whenStable();

        // then
        expect(component.newMessageTextContent).toBe('');
        expect(getChatSessionsSpy).toHaveBeenCalledOnce();
    });

    it('should not send a message if newMessageTextContent is empty', async () => {
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponse));
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
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponse));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        jest.spyOn(component, 'scrollToBottom').mockImplementation(() => {});
        const getChatSessionsSpy = jest.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));

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
        expect(getChatSessionsSpy).toHaveBeenCalledOnce();
    }));

    it('should not scroll to bottom when there is no new unread messages', fakeAsync(() => {
        // given
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithEmptyConversation));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        jest.spyOn(component, 'checkUnreadMessageScroll');
        jest.spyOn(component, 'scrollToBottom').mockImplementation(() => {});
        const getChatSessionsSpy = jest.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));

        chatService.switchTo(ChatServiceMode.COURSE, 123);

        // when
        component.ngAfterViewInit();
        fixture.whenStable();
        tick();

        // then
        expect(component.numNewMessages).toBe(0);
        expect(component.checkUnreadMessageScroll).toHaveBeenCalled();
        expect(component.scrollToBottom).not.toHaveBeenCalled();
        expect(getChatSessionsSpy).toHaveBeenCalledOnce();
    }));

    it('should scroll to bottom when there is new unread messages', fakeAsync(() => {
        // given
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithEmptyConversation));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of(mockWebsocketServerMessage));
        jest.spyOn(component, 'checkUnreadMessageScroll');
        jest.spyOn(component, 'scrollToBottom').mockImplementation(() => {});
        const getChatSessionsSpy = jest.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));

        chatService.switchTo(ChatServiceMode.COURSE, 123);
        // when
        component.ngAfterViewInit();
        fixture.whenStable();
        tick();

        // then
        expect(component.numNewMessages).toBe(1);
        expect(component.checkUnreadMessageScroll).toHaveBeenCalledTimes(2);
        expect(component.scrollToBottom).toHaveBeenCalled();
        expect(getChatSessionsSpy).toHaveBeenCalledOnce();
    }));

    it('should log accessed memories to console', fakeAsync(() => {
        // given
        jest.spyOn(console, 'log').mockImplementation(() => {});
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithEmptyConversation));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of(mockWebsocketServerMessageWithMemories));
        jest.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));

        chatService.switchTo(ChatServiceMode.COURSE, 123);
        // when
        component.ngAfterViewInit();
        fixture.whenStable();
        tick();

        // then
        expect(console.log).toHaveBeenCalledWith('Accessed memories found in message:', mockServerMessageWithMemories.accessedMemories);
    }));

    it('should log created memories to console', fakeAsync(() => {
        // given
        jest.spyOn(console, 'log').mockImplementation(() => {});
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithEmptyConversation));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of(mockWebsocketClientMessageWithMemories));
        jest.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));

        chatService.switchTo(ChatServiceMode.COURSE, 123);
        // when
        component.ngAfterViewInit();
        fixture.whenStable();
        tick();

        // then
        expect(console.log).toHaveBeenCalledWith('Created memories found in message:', mockClientMessageWithMemories.createdMemories);
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

        expect(sendButton.disabled).toBeTruthy();
    });

    it('should not render submit button if hasUserAcceptedExternalLLMUsage is false', () => {
        component.userAccepted = false;
        component.isLoading = false;
        component.error = undefined;
        fixture.detectChanges();
        const sendButton = fixture.debugElement.query(By.css('#irisSendButton'));

        expect(sendButton).toBeNull();
    });

    it('should not disable submit button if isLoading is false and no error exists', () => {
        component.userAccepted = true;
        component.isLoading = false;
        component.error = undefined;
        fixture.detectChanges();
        const sendButton = fixture.debugElement.query(By.css('#irisSendButton')).componentInstance;

        expect(sendButton.disabled).toBeFalsy();
    });

    it('should not disable submit button if isLoading is false and error is not fatal', () => {
        component.userAccepted = true;
        component.isLoading = false;
        component.error = IrisErrorMessageKey.SEND_MESSAGE_FAILED;
        fixture.detectChanges();
        const sendButton = fixture.debugElement.query(By.css('#irisSendButton')).componentInstance;

        expect(sendButton.disabled).toBeFalsy();
    });

    it('should set suggestions correctly', () => {
        const expectedSuggestions = ['suggestion1', 'suggestion2', 'suggestion3'];
        jest.spyOn(chatService, 'currentSuggestions').mockReturnValue(of(expectedSuggestions));

        component.ngOnInit();

        expect(component.suggestions).toEqual(expectedSuggestions);
    });

    it('should handle suggestion click correctly', () => {
        const suggestion = 'test suggestion';
        jest.spyOn(component, 'onSend');
        jest.spyOn(chatService, 'sendMessage');

        component.onSuggestionClick(suggestion);

        expect(chatService.sendMessage).toHaveBeenCalledWith(suggestion);
        expect(component.onSend).toHaveBeenCalled();
    });

    it('should clear suggestions after clicking on a suggestion', () => {
        const suggestion = 'test suggestion';
        jest.spyOn(component, 'onSend');

        component.onSuggestionClick(suggestion);

        expect(component.suggestions).toEqual([]);
    });

    it('should render suggestions when suggestions array is not empty', () => {
        // Arrange
        const expectedSuggestions = ['suggestion1', 'suggestion2', 'suggestion3'];
        const mockMessages = [mockClientMessage, mockServerMessage];

        jest.spyOn(chatService, 'currentSuggestions').mockReturnValue(of(expectedSuggestions));
        jest.spyOn(chatService, 'currentMessages').mockReturnValue(of(mockMessages));

        // Act
        component.ngOnInit();
        fixture.detectChanges();

        // Assert
        const suggestionsElement: HTMLElement = fixture.nativeElement.querySelector('.suggestions-container');
        const suggestionButtons = suggestionsElement.querySelectorAll('.suggestion-button');
        expect(suggestionButtons).toHaveLength(expectedSuggestions.length);
        suggestionButtons.forEach((button, index) => {
            expect(button.textContent).toBe(expectedSuggestions[index]);
        });
    });

    it('should not render suggestions when suggestions array is empty', () => {
        // Arrange
        const expectedSuggestions: string[] = [];
        const mockMessages = [mockClientMessage, mockServerMessage];

        jest.spyOn(chatService, 'currentSuggestions').mockReturnValue(of(expectedSuggestions));
        jest.spyOn(chatService, 'currentMessages').mockReturnValue(of(mockMessages));

        // Act
        component.ngOnInit();
        fixture.detectChanges();

        // Assert
        const suggestionButtons = fixture.nativeElement.querySelectorAll('.suggestion-button');
        expect(suggestionButtons).toHaveLength(0);
    });

    it('should not render suggestions if isLoading is true', () => {
        // Arrange
        const expectedSuggestions = ['suggestion1', 'suggestion2'];
        const mockMessages = [mockClientMessage, mockServerMessage];

        jest.spyOn(chatService, 'currentSuggestions').mockReturnValue(of(expectedSuggestions));
        jest.spyOn(chatService, 'currentMessages').mockReturnValue(of(mockMessages));

        // Act
        component.ngOnInit();
        component.isLoading = true;
        fixture.detectChanges();

        // Assert
        const suggestionButtons = fixture.nativeElement.querySelectorAll('.suggestion-button');
        expect(suggestionButtons).toHaveLength(0);
    });

    it('should not render suggestions if hasUserAcceptedExternalLLMUsage is false', () => {
        // Arrange
        const expectedSuggestions = ['suggestion1', 'suggestion2'];
        const mockMessages = [mockClientMessage, mockServerMessage];

        jest.spyOn(chatService, 'currentSuggestions').mockReturnValue(of(expectedSuggestions));
        jest.spyOn(chatService, 'currentMessages').mockReturnValue(of(mockMessages));

        // Act
        component.ngOnInit();
        component.userAccepted = false;
        fixture.detectChanges();

        // Assert
        const suggestionButtons = fixture.nativeElement.querySelectorAll('.suggestion-button');
        expect(suggestionButtons).toHaveLength(0);
    });

    it('should not render suggestions if the rate limit is exceeded', () => {
        // Arrange
        const expectedSuggestions = ['suggestion1', 'suggestion2'];
        const mockMessages = [mockClientMessage, mockServerMessage];

        jest.spyOn(chatService, 'currentSuggestions').mockReturnValue(of(expectedSuggestions));
        jest.spyOn(chatService, 'currentMessages').mockReturnValue(of(mockMessages));

        // Act
        component.ngOnInit();
        component.rateLimitInfo = { currentMessageCount: 100, rateLimit: 100, rateLimitTimeframeHours: 1 };
        fixture.detectChanges();

        // Assert
        const suggestionButtons = fixture.nativeElement.querySelectorAll('.suggestion-button');
        expect(suggestionButtons).toHaveLength(0);
    });

    it('should not render suggestions if the user is not active', () => {
        // Arrange
        const expectedSuggestions = ['suggestion1', 'suggestion2'];
        const mockMessages = [mockClientMessage, mockServerMessage];

        jest.spyOn(chatService, 'currentSuggestions').mockReturnValue(of(expectedSuggestions));
        jest.spyOn(chatService, 'currentMessages').mockReturnValue(of(mockMessages));

        // Act
        component.ngOnInit();
        component.active = false;
        fixture.detectChanges();

        // Assert
        const suggestionButtons = fixture.nativeElement.querySelectorAll('.suggestion-button');
        expect(suggestionButtons).toHaveLength(0);
    });

    it('should not render suggestions if hasActiveStage is true', () => {
        // Arrange
        const expectedSuggestions = ['suggestion1', 'suggestion2'];
        const mockMessages = [mockClientMessage, mockServerMessage];

        jest.spyOn(chatService, 'currentSuggestions').mockReturnValue(of(expectedSuggestions));
        jest.spyOn(chatService, 'currentMessages').mockReturnValue(of(mockMessages));

        // Act
        component.ngOnInit();
        component.hasActiveStage = true;
        fixture.detectChanges();

        // Assert
        const suggestionButtons = fixture.nativeElement.querySelectorAll('.suggestion-button');
        expect(suggestionButtons).toHaveLength(0);
    });

    describe('clear chat session', () => {
        it('should clear chat session when confirm modal is confirmed', fakeAsync(() => {
            jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponse));
            jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
            jest.spyOn(component, 'scrollToBottom').mockImplementation(() => {});
            jest.spyOn(chatService, 'clearChat').mockReturnValueOnce();
            const getChatSessionsSpy = jest.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));

            const modalRefMock = {
                result: Promise.resolve('confirm'),
            };
            jest.spyOn(mockModalService, 'open').mockReturnValue(modalRefMock as NgbModalRef);

            chatService.switchTo(ChatServiceMode.COURSE, 123);

            fixture.detectChanges();
            tick();
            const button: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#clear-chat-button');

            button.click();
            tick();

            expect(chatService.clearChat).toHaveBeenCalledOnce();
            expect(getChatSessionsSpy).toHaveBeenCalledOnce();
        }));

        it('should not render clear chat button if the history is empty', () => {
            const button: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#clear-chat-button');
            expect(button).toBeNull();
        });
    });

    it('should set irisQuestion onInit when provided in the queryParams', () => {
        const mockQueryParams = { irisQuestion: 'Can you explain me the error I got?' };
        const activatedRoute = TestBed.inject(ActivatedRoute);

        (activatedRoute.queryParams as any) = of(mockQueryParams);

        component.ngOnInit();

        expect(component.newMessageTextContent).toBe(mockQueryParams.irisQuestion);
    });

    it('should leave irisQuestion empty onInit when no question provided in the queryParams', () => {
        const mockQueryParams = {};
        const activatedRoute = TestBed.inject(ActivatedRoute);

        (activatedRoute.queryParams as any) = of(mockQueryParams);

        component.ngOnInit();

        expect(component.newMessageTextContent).toBe('');
    });

    it('should switch to the selected session on session click', () => {
        const mockSession: IrisSessionDTO = {
            id: 2,
            creationDate: new Date(),
            chatMode: ChatServiceMode.COURSE,
            entityId: 1,
            entityName: 'Course 1',
        };
        const switchToSessionSpy = jest.spyOn(chatService, 'switchToSession').mockReturnValue();

        component.onSessionClick(mockSession);

        expect(switchToSessionSpy).toHaveBeenCalledWith(mockSession);
    });

    it('should set isChatHistoryOpen to true when called with true', () => {
        component.isChatHistoryOpen = false;
        component.setChatHistoryVisibility(true);
        expect(component.isChatHistoryOpen).toBeTrue();
    });

    it('should set isChatHistoryOpen to false when called with false', () => {
        component.isChatHistoryOpen = true;
        component.setChatHistoryVisibility(false);
        expect(component.isChatHistoryOpen).toBeFalse();
    });

    it('should call chatService.clearChat when openNewSession is executed', () => {
        const clearChatSpy = jest.spyOn(chatService, 'clearChat').mockReturnValue();
        component.openNewSession();
        expect(clearChatSpy).toHaveBeenCalledOnce();
    });

    describe('search/filtering in chat history', () => {
        const mockDate = new Date('2025-10-06T12:00:00.000Z');
        const sessionToday: IrisSessionDTO = {
            id: 1,
            title: 'Greeting and study support',
            creationDate: new Date('2025-10-06T10:00:00.000Z'),
            chatMode: ChatServiceMode.COURSE,
            entityId: 1,
            entityName: 'Course 1',
        };
        const sessionYesterday: IrisSessionDTO = {
            id: 2,
            title: 'Difference between strategy and bridge pattern',
            creationDate: new Date('2025-10-05T10:00:00.000Z'),
            chatMode: ChatServiceMode.COURSE,
            entityId: 1,
            entityName: 'Course 1',
        };
        const sessionNoTitle: IrisSessionDTO = {
            id: 3,
            creationDate: new Date('2025-10-05T08:00:00.000Z'),
            chatMode: ChatServiceMode.COURSE,
            entityId: 1,
            entityName: 'Course 1',
        };

        const sortedSessions = [sessionToday, sessionYesterday, sessionNoTitle];

        beforeAll(() => {
            jest.useFakeTimers();
            jest.setSystemTime(mockDate);
        });

        afterAll(() => {
            jest.useRealTimers();
        });

        beforeEach(() => {
            component.chatSessions = [...sortedSessions];
        });

        it('filters by title (case-insensitive)', () => {
            component.setSearchValue('greet');
            const res = component.getSessionsBetween(0, 7);
            expect(res.map((s) => s.id)).toEqual([1]);
        });

        it('matches when the term appears in the middle of the title', () => {
            component.setSearchValue('strategy');
            const res = component.getSessionsBetween(0, 7);
            expect(res.map((s) => s.id)).toEqual([2]);
        });

        it('ignores sessions with null title when searching', () => {
            component.setSearchValue('anything');
            const res = component.getSessionsBetween(0, 7);
            expect(res.some((s) => s.id === 3)).toBeFalse();
        });

        it('returns all sessions again when search is cleared', () => {
            component.setSearchValue('greet');
            expect(component.getSessionsBetween(0, 7)).toHaveLength(1);

            component.setSearchValue(''); // clear
            expect(component.getSessionsBetween(0, 7)).toHaveLength(3);
        });
    });

    describe('getSessionsBetween', () => {
        const mockDate = new Date('2025-06-23T12:00:00.000Z');
        const sessionToday: IrisSessionDTO = {
            id: 1,
            creationDate: new Date('2025-06-23T10:00:00.000Z'),
            chatMode: ChatServiceMode.COURSE,
            entityId: 1,
            entityName: 'Course 1',
        };
        const sessionYesterday: IrisSessionDTO = {
            id: 2,
            creationDate: new Date('2025-06-22T12:00:00.000Z'),
            chatMode: ChatServiceMode.COURSE,
            entityId: 1,
            entityName: 'Course 1',
        };
        const session7DaysAgo: IrisSessionDTO = {
            id: 3,
            creationDate: new Date('2025-06-16T12:00:00.000Z'),
            chatMode: ChatServiceMode.COURSE,
            entityId: 1,
            entityName: 'Course 1',
        };
        const session8DaysAgo: IrisSessionDTO = {
            id: 4,
            creationDate: new Date('2025-06-15T12:00:00.000Z'),
            chatMode: ChatServiceMode.COURSE,
            entityId: 1,
            entityName: 'Course 1',
        };
        const session30DaysAgo: IrisSessionDTO = {
            id: 5,
            creationDate: new Date('2025-05-24T12:00:00.000Z'),
            chatMode: ChatServiceMode.COURSE,
            entityId: 1,
            entityName: 'Course 1',
        };

        const sortedSessions = [sessionToday, sessionYesterday, session7DaysAgo, session8DaysAgo, session30DaysAgo];

        beforeAll(() => {
            jest.useFakeTimers();
            jest.setSystemTime(mockDate);
        });

        afterAll(() => {
            jest.useRealTimers();
        });

        beforeEach(() => {
            component.chatSessions = [...sortedSessions];
        });

        it('should handle invalid day ranges gracefully', () => {
            expect(component.getSessionsBetween(-1, 5)).toEqual([]);
            expect(component.getSessionsBetween(0, -5)).toEqual([]);
            expect(component.getSessionsBetween(7, 0)).toEqual([]);
        });

        it('should retrieve sessions from the last 7 days (0 to 7)', () => {
            const result = component.getSessionsBetween(0, 7);
            expect(result).toHaveLength(3);
            expect(result.map((s) => s.id)).toEqual([sessionToday.id, sessionYesterday.id, session7DaysAgo.id]);
        });

        it('should retrieve sessions from yesterday only (1 to 1)', () => {
            const result = component.getSessionsBetween(1, 1);
            expect(result).toHaveLength(1);
            expect(result[0].id).toBe(sessionYesterday.id);
        });

        it('should retrieve sessions from between 8 and 30 days ago and be sorted correctly', () => {
            const result = component.getSessionsBetween(8, 30);
            expect(result).toHaveLength(2);
            expect(result.map((s) => s.id)).toEqual([session8DaysAgo.id, session30DaysAgo.id]);
        });

        it('should return an empty array for a range with no sessions', () => {
            const result = component.getSessionsBetween(2, 5);
            expect(result).toEqual([]);
        });

        it('should retrieve all sessions on or before 7 days ago with ignoreOlderBoundary', () => {
            const result = component.getSessionsBetween(7, undefined, true);
            expect(result).toHaveLength(3);
            expect(result.map((s) => s.id)).toEqual([session7DaysAgo.id, session8DaysAgo.id, session30DaysAgo.id]);
        });

        it('should retrieve all sessions on or before yesterday with ignoreOlderBoundary', () => {
            const result = component.getSessionsBetween(1, undefined, true);
            expect(result).toHaveLength(4);
            expect(result.map((s) => s.id)).toEqual([sessionYesterday.id, session7DaysAgo.id, session8DaysAgo.id, session30DaysAgo.id]);
        });
    });

    describe('Related entity button', () => {
        const setupAndVerifyRelatedEntityButton = (session: IrisSessionDTO, expectedLinkFragment: string) => {
            jest.spyOn(chatService, 'switchToSession').mockImplementation(() => {
                component['currentChatMode'].set(session.chatMode);
                component['currentRelatedEntityId'].set(session.entityId);
            });

            fixture.componentRef.setInput('isChatHistoryAvailable', true);

            chatService.switchToSession(session);
            fixture.detectChanges();
            tick();

            const relatedEntityButton = fixture.nativeElement.querySelector('.related-entity-button') as HTMLButtonElement;
            expect(relatedEntityButton).not.toBeNull();
            expect(component.relatedEntityRoute()).toBe(expectedLinkFragment);
        };

        it('should display correct related entity button when lecture session selected', fakeAsync(() => {
            const session: IrisSessionDTO = {
                id: 10,
                creationDate: new Date(),
                chatMode: ChatServiceMode.LECTURE,
                entityId: 55,
                entityName: 'Lecture 1',
            };

            setupAndVerifyRelatedEntityButton(session, '../lectures/55');
        }));

        it('should display correct related entity button when programming exercise session selected', fakeAsync(() => {
            const session: IrisSessionDTO = {
                id: 11,
                creationDate: new Date(),
                chatMode: ChatServiceMode.PROGRAMMING_EXERCISE,
                entityId: 99,
                entityName: 'Exercise 1',
            };

            setupAndVerifyRelatedEntityButton(session, '../exercises/99');
        }));
    });
});
