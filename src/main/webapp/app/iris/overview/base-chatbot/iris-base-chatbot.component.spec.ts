import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { IrisBaseChatbotComponent } from 'app/iris/overview/base-chatbot/iris-base-chatbot.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ChatStatusBarComponent } from 'app/iris/overview/base-chatbot/chat-status-bar/chat-status-bar.component';
import { IrisLogoComponent } from 'app/iris/overview/iris-logo/iris-logo.component';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { TranslateService } from '@ngx-translate/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { UserService } from 'app/core/user/shared/user.service';
import { IrisStatusService } from 'app/iris/overview/services/iris-status.service';
import { IrisChatHttpService } from 'app/iris/overview/services/iris-chat-http.service';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { IrisWebsocketService } from 'app/iris/overview/services/iris-websocket.service';
import { BehaviorSubject, of } from 'rxjs';
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
    mockWebsocketServerMessage,
} from 'test/helpers/sample/iris-sample-data';
import { By } from '@angular/platform-browser';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { IrisAssistantMessage, IrisSender, IrisUserMessage } from 'app/iris/shared/entities/iris-message.model';
import { IrisMessageResponseDTO } from 'app/iris/shared/entities/iris-message-response-dto.model';
import { IrisJsonMessageContent, IrisMessageContentType, IrisTextMessageContent, getMcqData, isMcqContent } from 'app/iris/shared/entities/iris-content-type.model';
import dayjs from 'dayjs/esm';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { IrisSessionDTO } from 'app/iris/shared/entities/iris-session-dto.model';
import { IrisStageDTO, IrisStageStateDTO } from 'app/iris/shared/entities/iris-stage-dto.model';
import { IrisThinkingBubbleComponent } from 'app/iris/overview/base-chatbot/iris-thinking-bubble/iris-thinking-bubble.component';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { User } from 'app/core/user/user.model';
import { LLMSelectionDecision, LLM_MODAL_DISMISSED } from 'app/core/user/shared/dto/updateLLMSelectionDecision.dto';
import { LLMSelectionModalService } from 'app/logos/llm-selection-popup.service';
import { ConfirmationService } from 'primeng/api';
import { DialogService } from 'primeng/dynamicdialog';
import { AlertService } from 'app/shared/service/alert.service';
import { ContextSelectionComponent } from 'app/iris/overview/context-selection/context-selection.component';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { COURSE_SUGGESTION_CHIPS } from 'app/iris/overview/base-chatbot/iris-chatbot-suggestion-chips';

// Must match the constants in the component
const PLACEHOLDER_CYCLE_INTERVAL_MS = 5000;
const PLACEHOLDER_FADE_DURATION_MS = 300;

describe('IrisBaseChatbotComponent', () => {
    setupTestBed({ zoneless: true });

    let component: IrisBaseChatbotComponent;
    let fixture: ComponentFixture<IrisBaseChatbotComponent>;

    let chatService: IrisChatService;
    let httpService: IrisChatHttpService;
    let wsMock: IrisWebsocketService;
    let accountService: AccountService;

    const statusMock = {
        currentRatelimitInfo: vi.fn().mockReturnValue(of({})),
        handleRateLimitInfo: vi.fn(),
        getActiveStatus: vi.fn().mockReturnValue(of({})),
        setCurrentCourse: vi.fn(),
    } as any;
    const mockLLMModalService = {
        open: vi.fn().mockResolvedValue(LLM_MODAL_DISMISSED),
    } as any;
    const mockUserService = {
        updateLLMSelectionDecision: vi.fn().mockReturnValue(of(new HttpResponse<void>())),
    } as any;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                IrisBaseChatbotComponent,
                FontAwesomeModule,
                RouterModule,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(HtmlForMarkdownPipe),
                MockDirective(TranslateDirective),
                MockComponent(ChatStatusBarComponent),
                MockComponent(IrisLogoComponent),
                MockComponent(ButtonComponent),
                MockComponent(ContextSelectionComponent),
                MockComponent(IrisThinkingBubbleComponent),
            ],
            providers: [
                LocalStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                SessionStorageService,
                { provide: HttpClient, useValue: {} },
                { provide: AccountService, useClass: MockAccountService },
                { provide: UserService, useValue: mockUserService },
                { provide: IrisStatusService, useValue: statusMock },
                { provide: LLMSelectionModalService, useValue: mockLLMModalService },
                MockProvider(AlertService),
                MockProvider(DialogService),
                MockProvider(ActivatedRoute),
                MockProvider(IrisChatHttpService),
                MockProvider(IrisWebsocketService),
                MockProvider(CourseStorageService),
            ],
        })
            .compileComponents()
            .then(() => {
                vi.spyOn(console, 'error').mockImplementation(() => {});
                global.window ??= window;
                window.scroll = vi.fn();
                window.HTMLElement.prototype.scrollTo = vi.fn();

                // Set up services BEFORE creating component
                chatService = TestBed.inject(IrisChatService);
                chatService.setCourseId(456);
                httpService = TestBed.inject(IrisChatHttpService);
                wsMock = TestBed.inject(IrisWebsocketService);
                accountService = TestBed.inject(AccountService);

                // Set user identity BEFORE creating component (constructor reads this)
                accountService.userIdentity.set({ selectedLLMUsage: LLMSelectionDecision.CLOUD_AI } as User);
                vi.spyOn(accountService, 'getAuthenticationState').mockReturnValue(of());

                // Now create component
                fixture = TestBed.createComponent(IrisBaseChatbotComponent);
                component = fixture.componentInstance;

                fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should set userAccepted to CLOUD_AI if user has accepted the external LLM usage policy', () => {
        expect(component.userAccepted()).toBe(LLMSelectionDecision.CLOUD_AI);
    });

    describe('when user has not accepted LLM usage policy', () => {
        it('should set userAccepted to undefined', async () => {
            accountService.userIdentity.set({ selectedLLMUsage: undefined } as User);
            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;

            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.detectChanges();
            // Flush the pending setTimeout from the constructor that triggers showAISelectionModal
            await new Promise((resolve) => setTimeout(resolve, 0));
            await fixture.whenStable();
            expect(component.userAccepted()).toBeUndefined();
        });
    });

    it('should call API when user accept the policy', () => {
        const chatServiceSpy = vi.spyOn(chatService, 'updateLLMUsageConsent').mockImplementation(() => {});

        component.acceptPermission(LLMSelectionDecision.CLOUD_AI);

        expect(chatServiceSpy).toHaveBeenCalledOnce();
        expect(chatServiceSpy).toHaveBeenCalledWith(LLMSelectionDecision.CLOUD_AI);
        expect(component.userAccepted()).toBe(LLMSelectionDecision.CLOUD_AI);
    });

    it('should add user message on send', async () => {
        // given
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponse));
        vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        const getChatSessionsSpy = vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));

        const content = 'Hello';
        const createdMessage = mockUserMessageWithContent(content);
        vi.spyOn(httpService, 'createMessage').mockReturnValueOnce(of({ body: createdMessage } as HttpResponse<IrisMessageResponseDTO>));

        vi.spyOn(component, 'scrollToBottom').mockImplementation(() => {});

        const stub = vi.spyOn(chatService, 'sendMessage');
        component.newMessageTextContent.set(content);
        chatService.switchTo(ChatServiceMode.COURSE, 123);

        // when
        component.onSend();

        await fixture.whenStable();

        // then
        expect(component.messages()).toContainEqual(createdMessage);
        expect(stub).toHaveBeenCalledWith(content);
        expect(getChatSessionsSpy).toHaveBeenCalledOnce();
    });

    it('should resend message', async () => {
        // given
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponse));
        vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        const getChatSessionsSpy = vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));

        const content = 'Hello';
        const createdMessage = mockUserMessageWithContent(content);
        createdMessage.id = 2;
        vi.spyOn(httpService, 'resendMessage').mockReturnValueOnce(of({ body: createdMessage } as HttpResponse<IrisMessageResponseDTO>));
        vi.spyOn(component, 'scrollToBottom').mockImplementation(() => {});

        const stub = vi.spyOn(chatService, 'resendMessage');
        component.newMessageTextContent.set(content);
        chatService.switchTo(ChatServiceMode.COURSE, 123);

        // when
        component.resendMessage(createdMessage);

        await fixture.whenStable();

        // then
        expect(component.messages()).toContainEqual(createdMessage);
        expect(stub).toHaveBeenCalledWith(createdMessage);
        expect(getChatSessionsSpy).toHaveBeenCalledOnce();
    });

    it('should rate message', async () => {
        // given
        const id = 123;
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
        vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        vi.spyOn(httpService, 'rateMessage').mockReturnValueOnce(of({} as HttpResponse<IrisMessageResponseDTO>));
        vi.spyOn(component, 'scrollToBottom').mockImplementation(() => {});
        const getChatSessionsSpy = vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));

        const message = mockServerMessage;
        const stub = vi.spyOn(chatService, 'rateMessage');
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
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponse));
        vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        const getChatSessionsSpy = vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));

        const content = 'Hello';
        const createdMessage = mockUserMessageWithContent(content);
        vi.spyOn(httpService, 'createMessage').mockReturnValueOnce(of({ body: createdMessage } as HttpResponse<IrisMessageResponseDTO>));

        vi.spyOn(component, 'scrollToBottom').mockImplementation(() => {});

        component.newMessageTextContent.set(content);
        chatService.switchTo(ChatServiceMode.COURSE, 123);

        // when
        component.onSend();
        await fixture.whenStable();

        // then
        expect(component.newMessageTextContent()).toBe('');
        expect(getChatSessionsSpy).toHaveBeenCalledOnce();
    });

    it('should not send a message if newMessageTextContent is empty', async () => {
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponse));
        vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));

        vi.spyOn(httpService, 'createMessage');

        chatService.switchTo(ChatServiceMode.COURSE, 123);

        component.onSend();

        expect(httpService.createMessage).not.toHaveBeenCalled();
        expect(component.newMessageTextContent()).toBe('');
    });

    it('should set the appropriate message styles based on the sender', async () => {
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponse));
        vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        vi.spyOn(component, 'scrollToBottom').mockImplementation(() => {});
        const getChatSessionsSpy = vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));

        component.userAccepted.set(LLMSelectionDecision.CLOUD_AI);
        chatService.switchTo(ChatServiceMode.COURSE, 123);

        component.ngAfterViewInit();
        fixture.detectChanges();
        await fixture.whenStable();

        const chatBodyElement: HTMLElement = fixture.nativeElement.querySelector('.chat-body');
        const clientChats = chatBodyElement.querySelectorAll('.bubble-left');
        const myChats = chatBodyElement.querySelectorAll('.bubble-right');

        expect(clientChats).toHaveLength(1);
        expect(myChats).toHaveLength(1);
        expect(getChatSessionsSpy).toHaveBeenCalledOnce();
    });

    it('should render memories indicator for messages with memories', async () => {
        const messagesWithMemories = [mockClientMessageWithMemories, mockServerMessageWithMemories];
        vi.spyOn(chatService, 'currentMessages').mockReturnValue(of(messagesWithMemories));

        fixture = TestBed.createComponent(IrisBaseChatbotComponent);
        component = fixture.componentInstance;
        fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
        fixture.detectChanges();
        await fixture.whenStable();

        const indicators = fixture.nativeElement.querySelectorAll('[data-testid="memories-indicator-button"]');
        expect(indicators.length).toBeGreaterThan(0);
    });

    it('should not scroll to bottom when there is no new unread messages', async () => {
        // given
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithEmptyConversation));
        vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        vi.spyOn(component, 'scrollToBottom').mockImplementation(() => {});
        const getChatSessionsSpy = vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));

        chatService.switchTo(ChatServiceMode.COURSE, 123);

        // when
        component.ngAfterViewInit();
        await fixture.whenStable();

        // then
        expect(component.numNewMessages()).toBe(0);
        expect(component.scrollToBottom).not.toHaveBeenCalled();
        expect(getChatSessionsSpy).toHaveBeenCalledOnce();
    });

    it('should scroll to bottom when there is new unread messages', async () => {
        // given - set up mocks before component creation for proper toSignal initialization
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithEmptyConversation));
        vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of(mockWebsocketServerMessage));
        const getChatSessionsSpy = vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));

        // Recreate component with mocked services
        fixture = TestBed.createComponent(IrisBaseChatbotComponent);
        component = fixture.componentInstance;
        fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();

        // Set up spy on scrollToBottom after component is created but before switchTo
        const scrollSpy = vi.spyOn(component, 'scrollToBottom').mockImplementation(() => {});

        fixture.detectChanges();

        chatService.switchTo(ChatServiceMode.COURSE, 123);

        // when
        component.ngAfterViewInit();
        await fixture.whenStable();

        // then
        expect(component.numNewMessages()).toBe(1);
        expect(scrollSpy).toHaveBeenCalled();
        expect(getChatSessionsSpy).toHaveBeenCalledOnce();
    });

    it('should disable enter key if isLoading and active', () => {
        // active() is true by default (initialValue: true in toSignal)
        component.isLoading.set(true);
        const event = new KeyboardEvent('keyup', { key: 'Enter', shiftKey: false });
        vi.spyOn(component, 'onSend');

        component.handleKey(event);

        expect(component.onSend).not.toHaveBeenCalled();
    });

    it('should call onSend if Enter key is pressed without Shift key', () => {
        const event = new KeyboardEvent('keyup', { key: 'Enter', shiftKey: false });
        vi.spyOn(component, 'onSend');

        vi.spyOn(event, 'preventDefault');

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
        vi.spyOn(event, 'target', 'get').mockReturnValue(textAreaElement);

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
        // The textarea is only rendered when userAccepted is true
        component.userAccepted.set(LLMSelectionDecision.CLOUD_AI);
        fixture.detectChanges();

        const textarea = fixture.nativeElement.querySelector('textarea');
        const originalScrollHeightGetter = textarea.__lookupGetter__('scrollHeight');
        const originalGetComputedStyle = window.getComputedStyle;

        // Set some text content to avoid early return
        textarea.value = 'Some text content';

        const scrollHeightGetterSpy = vi.spyOn(textarea, 'scrollHeight', 'get').mockReturnValue(100);
        const getComputedStyleSpy = vi.spyOn(window, 'getComputedStyle').mockImplementation(
            () =>
                ({
                    lineHeight: '20px',
                }) as Partial<CSSStyleDeclaration> as any,
        );

        component.adjustTextareaRows();

        // The method caps height at min(scrollHeight, maxHeight=200), so with scrollHeight=100 it should be 100px
        expect(textarea.style.height).toBe('100px');

        // Restore original getters and methods
        scrollHeightGetterSpy.mockRestore();
        getComputedStyleSpy.mockRestore();
        textarea.__defineGetter__('scrollHeight', originalScrollHeightGetter);
        window.getComputedStyle = originalGetComputedStyle;
    });

    it('should disable submit button if isLoading is true', () => {
        component.isLoading.set(true);
        fixture.changeDetectorRef.detectChanges();
        const sendButton = fixture.debugElement.query(By.css('#irisSendButton')).componentInstance;

        expect(sendButton.disabled).toBeTruthy();
    });

    it('should not render submit button if userAccepted is undefined or NO_AI', () => {
        component.userAccepted.set(undefined);
        component.isLoading.set(false);
        // error is from toSignal and readonly - but button visibility only depends on userAccepted
        fixture.changeDetectorRef.detectChanges();
        const sendButton = fixture.debugElement.query(By.css('#irisSendButton'));

        expect(sendButton).toBeNull();
    });

    it('should not disable submit button if isLoading is false and no error exists', () => {
        component.userAccepted.set(LLMSelectionDecision.CLOUD_AI);
        component.isLoading.set(false);
        component.newMessageTextContent.set('test message');
        // error is from toSignal - button disabled state doesn't depend on error
        fixture.changeDetectorRef.detectChanges();
        const sendButton = fixture.debugElement.query(By.css('#irisSendButton')).componentInstance;

        expect(sendButton.disabled()).toBeFalsy();
    });

    it('should not disable submit button if isLoading is false and error is not fatal', () => {
        component.userAccepted.set(LLMSelectionDecision.CLOUD_AI);
        component.isLoading.set(false);
        component.newMessageTextContent.set('test message');
        // error is from toSignal - button disabled state doesn't depend on error
        fixture.changeDetectorRef.detectChanges();
        const sendButton = fixture.debugElement.query(By.css('#irisSendButton')).componentInstance;

        expect(sendButton.disabled()).toBeFalsy();
    });

    it('should handle suggestion click correctly', () => {
        const suggestion = 'test suggestion';
        vi.spyOn(component, 'onSend');
        vi.spyOn(chatService, 'sendMessage').mockReturnValue(of(undefined));

        component.onSuggestionClick(suggestion);

        expect(chatService.sendMessage).toHaveBeenCalledWith(suggestion);
        expect(component.onSend).toHaveBeenCalled();
    });

    it('should set clickedSuggestion when clicking a suggestion', () => {
        const suggestion = 'test suggestion';
        vi.spyOn(component, 'onSend');
        vi.spyOn(chatService, 'sendMessage').mockReturnValue(of(undefined));

        component.onSuggestionClick(suggestion);

        expect(component.clickedSuggestion()).toEqual(suggestion);
    });

    describe('suggestions rendering', () => {
        const expectedSuggestions = ['suggestion1', 'suggestion2', 'suggestion3'];
        const mockMessages = [mockClientMessage, mockServerMessage];

        beforeEach(() => {
            // Mock observables before component creation for toSignal to pick up
            vi.spyOn(chatService, 'currentSuggestions').mockReturnValue(of(expectedSuggestions));
            vi.spyOn(chatService, 'currentMessages').mockReturnValue(of(mockMessages));

            // Recreate component with mocked observables
            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.detectChanges();
        });

        it('should render suggestions when suggestions array is not empty', () => {
            const suggestionsElement: HTMLElement = fixture.nativeElement.querySelector('.suggestions-container');
            const suggestionButtons = suggestionsElement.querySelectorAll('.suggestion-button');
            expect(suggestionButtons).toHaveLength(expectedSuggestions.length);
            suggestionButtons.forEach((button, index) => {
                expect(button.textContent).toBe(expectedSuggestions[index]);
            });
        });

        it('should disable suggestion buttons if isLoading is true', () => {
            component.isLoading.set(true);
            fixture.changeDetectorRef.detectChanges();

            const suggestionButtons = fixture.nativeElement.querySelectorAll('.suggestion-button');
            expect(suggestionButtons).toHaveLength(expectedSuggestions.length);
            suggestionButtons.forEach((button: HTMLButtonElement) => {
                expect(button.disabled).toBe(true);
            });
        });

        it('should not render suggestions if userAccepted is not CLOUD_AI or LOCAL_AI', () => {
            component.userAccepted.set(LLMSelectionDecision.NO_AI);
            fixture.changeDetectorRef.detectChanges();

            const suggestionButtons = fixture.nativeElement.querySelectorAll('.suggestion-button');
            expect(suggestionButtons).toHaveLength(0);
        });
    });

    describe('suggestions not rendered', () => {
        it('should not render suggestions when suggestions array is empty', () => {
            vi.spyOn(chatService, 'currentSuggestions').mockReturnValue(of([]));
            vi.spyOn(chatService, 'currentMessages').mockReturnValue(of([mockClientMessage, mockServerMessage]));

            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.detectChanges();

            const suggestionButtons = fixture.nativeElement.querySelectorAll('.suggestion-button');
            expect(suggestionButtons).toHaveLength(0);
        });

        it('should not render suggestions if the rate limit is exceeded', () => {
            vi.spyOn(chatService, 'currentSuggestions').mockReturnValue(of(['suggestion1', 'suggestion2']));
            vi.spyOn(chatService, 'currentMessages').mockReturnValue(of([mockClientMessage, mockServerMessage]));
            statusMock.currentRatelimitInfo.mockReturnValue(of({ currentMessageCount: 100, rateLimit: 100, rateLimitTimeframeHours: 1 }));

            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.detectChanges();

            const suggestionButtons = fixture.nativeElement.querySelectorAll('.suggestion-button');
            expect(suggestionButtons).toHaveLength(0);
        });

        it('should not render suggestions if the user is not active', () => {
            vi.spyOn(chatService, 'currentSuggestions').mockReturnValue(of(['suggestion1', 'suggestion2']));
            vi.spyOn(chatService, 'currentMessages').mockReturnValue(of([mockClientMessage, mockServerMessage]));
            statusMock.getActiveStatus.mockReturnValue(of(false));

            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.detectChanges();

            const suggestionButtons = fixture.nativeElement.querySelectorAll('.suggestion-button');
            expect(suggestionButtons).toHaveLength(0);
        });

        it('should not render suggestions if hasActiveStage is true', () => {
            const activeStage = { state: IrisStageStateDTO.IN_PROGRESS } as IrisStageDTO;
            vi.spyOn(chatService, 'currentSuggestions').mockReturnValue(of(['suggestion1', 'suggestion2']));
            vi.spyOn(chatService, 'currentMessages').mockReturnValue(of([mockClientMessage, mockServerMessage]));
            vi.spyOn(chatService, 'currentStages').mockReturnValue(of([activeStage]));

            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.detectChanges();

            const suggestionButtons = fixture.nativeElement.querySelectorAll('.suggestion-button');
            expect(suggestionButtons).toHaveLength(0);
        });
    });

    describe('clear chat session', () => {
        it('should clear chat session when clear button is clicked', async () => {
            vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponse));
            vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
            vi.spyOn(component, 'scrollToBottom').mockImplementation(() => {});
            vi.spyOn(chatService, 'clearChat').mockReturnValueOnce();
            const getChatSessionsSpy = vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));

            chatService.switchTo(ChatServiceMode.COURSE, 123);

            fixture.detectChanges();
            await fixture.whenStable();
            const button: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#clear-chat-button');

            button.click();
            await fixture.whenStable();

            expect(chatService.clearChat).toHaveBeenCalledOnce();
            expect(getChatSessionsSpy).toHaveBeenCalledOnce();
        });

        it('should not render clear chat button if the history is empty', () => {
            const button: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#clear-chat-button');
            expect(button).toBeNull();
        });
    });

    it('should leave newMessageTextContent empty when no irisQuestion provided in queryParams', () => {
        // newMessageTextContent starts empty by default
        expect(component.newMessageTextContent()).toBe('');
    });

    describe('irisQuestion from queryParams', () => {
        it('should set newMessageTextContent when irisQuestion is provided', () => {
            const activatedRoute = TestBed.inject(ActivatedRoute);
            (activatedRoute.queryParams as any) = of({ irisQuestion: 'Can you explain me the error I got?' });

            // Recreate component with mocked queryParams
            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.detectChanges();

            expect(component.newMessageTextContent()).toBe('Can you explain me the error I got?');
        });
    });

    it('should start a new session when the new chat item is clicked', () => {
        const newChatSession: IrisSessionDTO = {
            id: 2,
            title: 'New chat',
            creationDate: new Date(),
            chatMode: ChatServiceMode.COURSE,
            entityId: 1,
            entityName: 'Course 1',
        };
        const clearChatSpy = vi.spyOn(chatService, 'clearChat').mockReturnValue();
        const switchToSessionSpy = vi.spyOn(chatService, 'switchToSession').mockReturnValue();

        component.onSessionClick(newChatSession);

        expect(clearChatSpy).toHaveBeenCalledOnce();
        expect(switchToSessionSpy).not.toHaveBeenCalled();
    });

    it('should switch to the selected session on session click', () => {
        const mockSession: IrisSessionDTO = {
            id: 3,
            title: 'Course chat',
            creationDate: new Date(),
            chatMode: ChatServiceMode.COURSE,
            entityId: 1,
            entityName: 'Course 1',
        };
        const switchToSessionSpy = vi.spyOn(chatService, 'switchToSession').mockReturnValue();

        component.onSessionClick(mockSession);

        expect(switchToSessionSpy).toHaveBeenCalledWith(mockSession);
    });

    it('should set isChatHistoryOpen to true when called with true', () => {
        component.isChatHistoryOpen.set(false);
        component.setChatHistoryVisibility(true);
        expect(component.isChatHistoryOpen()).toBe(true);
    });

    it('should set isChatHistoryOpen to false when called with false', () => {
        component.isChatHistoryOpen.set(true);
        component.setChatHistoryVisibility(false);
        expect(component.isChatHistoryOpen()).toBe(false);
    });

    it('should call chatService.clearChat when openNewSession is executed', () => {
        const clearChatSpy = vi.spyOn(chatService, 'clearChat').mockReturnValue();
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
            vi.useFakeTimers();
            vi.setSystemTime(mockDate);
        });

        afterAll(() => {
            vi.useRealTimers();
        });

        beforeEach(() => {
            // Mock chatSessions before component creation (chatSessions is readonly from toSignal)
            vi.spyOn(chatService, 'availableChatSessions').mockReturnValue(of([...sortedSessions]));
            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.detectChanges();
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
            expect(res.some((s) => s.id === 3)).toBe(false);
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
            vi.useFakeTimers();
            vi.setSystemTime(mockDate);
        });

        afterAll(() => {
            vi.useRealTimers();
        });

        beforeEach(() => {
            // Mock chatSessions before component creation (chatSessions is readonly from toSignal)
            vi.spyOn(chatService, 'availableChatSessions').mockReturnValue(of([...sortedSessions]));
            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.detectChanges();
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

    describe('copyMessage', () => {
        it('should not copy if message has no content', () => {
            const message = { id: 1, content: undefined } as any;

            component.copyMessage(message);

            expect(component.copiedMessageKey()).toBeUndefined();
        });

        it('should not copy if message content is empty array', () => {
            const message = { id: 1, content: [] } as any;

            component.copyMessage(message);

            expect(component.copiedMessageKey()).toBeUndefined();
        });

        it('should not throw when clipboard API is not available', () => {
            const message = {
                id: 1,
                content: [{ type: 'TEXT', textContent: 'Hello world' }],
            } as any;

            // Should not throw regardless of clipboard availability
            expect(() => component.copyMessage(message)).not.toThrow();
        });

        it('should correctly identify copied message by id', () => {
            const message = { id: 42 } as any;

            component.copiedMessageKey.set(42);

            expect(component.isCopied(message)).toBe(true);
            expect(component.isCopied({ id: 99 } as any)).toBe(false);
        });

        it('should use messageIndex as key when message has no id', () => {
            const message = { id: undefined } as any;

            component.copiedMessageKey.set(5);

            expect(component.isCopied(message, 5)).toBe(true);
            expect(component.isCopied(message, 3)).toBe(false);
        });
    });

    describe('adjustTextareaRows', () => {
        it('should reset height and return early when textarea is empty', () => {
            component.userAccepted.set(LLMSelectionDecision.CLOUD_AI);
            fixture.detectChanges();

            const textarea = fixture.nativeElement.querySelector('textarea');
            textarea.value = '';

            component.adjustTextareaRows();

            expect(textarea.style.height).toBe('');
        });

        it('should reset height and return early when textarea has only whitespace', () => {
            component.userAccepted.set(LLMSelectionDecision.CLOUD_AI);
            fixture.detectChanges();

            const textarea = fixture.nativeElement.querySelector('textarea');
            textarea.value = '   ';

            component.adjustTextareaRows();

            expect(textarea.style.height).toBe('');
        });
    });

    describe('Related entity button', () => {
        it('should display correct related entity button when lecture session selected', async () => {
            // Mock the service observables before component creation
            vi.spyOn(chatService, 'currentChatMode').mockReturnValue(of(ChatServiceMode.LECTURE));
            vi.spyOn(chatService, 'currentRelatedEntityId').mockReturnValue(of(55));

            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.componentRef.setInput('isChatHistoryAvailable', true);
            fixture.componentRef.setInput('fullSize', undefined);
            fixture.componentRef.setInput('showCloseButton', false);
            fixture.componentRef.setInput('isChatGptWrapper', false);
            fixture.detectChanges();
            await fixture.whenStable();

            const relatedEntityButton = fixture.nativeElement.querySelector('.related-entity-button') as HTMLButtonElement;
            expect(relatedEntityButton).not.toBeNull();
            expect(component.relatedEntityRoute()).toBe('../lectures/55');
        });

        it('should display correct related entity button when programming exercise session selected', async () => {
            // Mock the service observables before component creation
            vi.spyOn(chatService, 'currentChatMode').mockReturnValue(of(ChatServiceMode.PROGRAMMING_EXERCISE));
            vi.spyOn(chatService, 'currentRelatedEntityId').mockReturnValue(of(99));

            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.componentRef.setInput('isChatHistoryAvailable', true);
            fixture.componentRef.setInput('fullSize', undefined);
            fixture.componentRef.setInput('showCloseButton', false);
            fixture.componentRef.setInput('isChatGptWrapper', false);
            fixture.detectChanges();
            await fixture.whenStable();

            const relatedEntityButton = fixture.nativeElement.querySelector('.related-entity-button') as HTMLButtonElement;
            expect(relatedEntityButton).not.toBeNull();
            expect(component.relatedEntityRoute()).toBe('../exercises/99');
        });

        it('should display correct related entity button when text exercise session selected', async () => {
            vi.spyOn(chatService, 'currentChatMode').mockReturnValue(of(ChatServiceMode.TEXT_EXERCISE));
            vi.spyOn(chatService, 'currentRelatedEntityId').mockReturnValue(of(77));

            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.componentRef.setInput('isChatHistoryAvailable', true);
            fixture.componentRef.setInput('fullSize', undefined);
            fixture.componentRef.setInput('showCloseButton', false);
            fixture.componentRef.setInput('isChatGptWrapper', false);
            fixture.detectChanges();
            await fixture.whenStable();

            const relatedEntityButton = fixture.nativeElement.querySelector('.related-entity-button') as HTMLButtonElement;
            expect(relatedEntityButton).not.toBeNull();
            expect(component.relatedEntityRoute()).toBe('../exercises/77');
            expect(component.relatedEntityLinkButtonLabel()).toBe('artemisApp.exerciseChatbot.goToRelatedEntityButton.exerciseLabel');
        });
    });

    describe('LLM Selection Modal', () => {
        let mockLLMModalService: any;

        beforeEach(() => {
            mockLLMModalService = TestBed.inject(LLMSelectionModalService);
        });

        it('should show LLM selection modal when userAccepted is undefined', async () => {
            accountService.userIdentity.set({ selectedLLMUsage: undefined } as User);

            const openSpy = vi.spyOn(mockLLMModalService, 'open').mockResolvedValue(LLM_MODAL_DISMISSED);

            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.detectChanges();
            // Flush the pending setTimeout from the constructor that triggers showAISelectionModal
            await new Promise((resolve) => setTimeout(resolve, 0));
            await fixture.whenStable();

            expect(component.userAccepted()).toBeUndefined();
            expect(openSpy).toHaveBeenCalled();
        });

        it('should not show LLM selection modal when userAccepted is already set', async () => {
            const mockUser = { selectedLLMUsage: LLMSelectionDecision.CLOUD_AI } as User;
            accountService.userIdentity.set(mockUser);
            vi.spyOn(accountService, 'userIdentity').mockReturnValue(mockUser as any);

            mockLLMModalService.open.mockClear();
            const openSpy = vi.spyOn(mockLLMModalService, 'open');

            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            const chatBody = fixture.nativeElement.querySelector('.chat-body');
            if (chatBody) {
                chatBody.scrollTo = vi.fn();
            }
            fixture.detectChanges();

            await fixture.whenStable();

            expect(component.userAccepted()).toBe(LLMSelectionDecision.CLOUD_AI);
            expect(openSpy).not.toHaveBeenCalled();
        });

        it('should set userAccepted to CLOUD_AI when user selects cloud in modal', async () => {
            accountService.userIdentity.set({ selectedLLMUsage: undefined } as User);
            vi.spyOn(mockLLMModalService, 'open').mockResolvedValue(LLMSelectionDecision.CLOUD_AI);
            vi.spyOn(chatService, 'updateLLMUsageConsent').mockImplementation(() => {});

            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.detectChanges();

            // Flush the pending setTimeout from the constructor and then call directly
            await new Promise((resolve) => setTimeout(resolve, 0));
            await component.showAISelectionModal();

            expect(component.userAccepted()).toBe(LLMSelectionDecision.CLOUD_AI);
        });

        it('should set userAccepted to LOCAL_AI when user selects local in modal', async () => {
            vi.spyOn(mockLLMModalService, 'open').mockResolvedValue(LLMSelectionDecision.LOCAL_AI);
            vi.spyOn(chatService, 'updateLLMUsageConsent').mockImplementation(() => {});

            await component.showAISelectionModal();

            expect(component.userAccepted()).toBe(LLMSelectionDecision.LOCAL_AI);
        });

        it('should close chat when user selects no_ai in modal', async () => {
            vi.spyOn(mockLLMModalService, 'open').mockResolvedValue(LLMSelectionDecision.NO_AI);
            vi.spyOn(chatService, 'updateLLMUsageConsent').mockImplementation(() => {});
            vi.spyOn(component.closeClicked, 'emit');

            await component.showAISelectionModal();

            expect(chatService.updateLLMUsageConsent).toHaveBeenCalledWith(LLMSelectionDecision.NO_AI);
            expect(component.closeClicked.emit).toHaveBeenCalledOnce();
        });

        it('should close chat when user dismisses modal (none)', async () => {
            vi.spyOn(mockLLMModalService, 'open').mockResolvedValue(LLM_MODAL_DISMISSED);
            vi.spyOn(component.closeClicked, 'emit');

            await component.showAISelectionModal();

            expect(component.closeClicked.emit).toHaveBeenCalledOnce();
        });
    });

    describe('LLM Selection rendering', () => {
        it('should render chat input when userAccepted is CLOUD_AI', () => {
            component.userAccepted.set(LLMSelectionDecision.CLOUD_AI);
            fixture.changeDetectorRef.detectChanges();

            const chatInput = fixture.nativeElement.querySelector('.chat-input');
            expect(chatInput).not.toBeNull();
        });

        it('should render chat input when userAccepted is LOCAL_AI', () => {
            component.userAccepted.set(LLMSelectionDecision.LOCAL_AI);
            fixture.changeDetectorRef.detectChanges();

            const chatInput = fixture.nativeElement.querySelector('.chat-input');
            expect(chatInput).not.toBeNull();
        });

        it('should show error message when userAccepted is NO_AI', () => {
            component.userAccepted.set(LLMSelectionDecision.NO_AI);
            fixture.changeDetectorRef.detectChanges();

            const errorMessages = fixture.nativeElement.querySelectorAll('.client-chat-error');
            expect(errorMessages.length).toBeGreaterThan(0);
            const allErrorText = Array.from(errorMessages)
                .map((msg: any) => msg.textContent)
                .join(' ');

            expect(allErrorText).toContain('aiUsageDeclined');
        });

        it('should not render chat input when userAccepted is NO_AI', () => {
            component.userAccepted.set(LLMSelectionDecision.NO_AI);
            fixture.changeDetectorRef.detectChanges();

            const chatInput = fixture.nativeElement.querySelector('.chat-input');
            expect(chatInput).toBeNull();
        });

        it('should not render chat input when userAccepted is undefined', () => {
            component.userAccepted.set(undefined);
            fixture.changeDetectorRef.detectChanges();

            const chatInput = fixture.nativeElement.querySelector('.chat-input');
            expect(chatInput).toBeNull();
        });
    });

    describe('suggestions with LLM selection', () => {
        const expectedSuggestions = ['suggestion1', 'suggestion2'];
        const mockMessages = [mockClientMessage, mockServerMessage];

        beforeEach(() => {
            vi.spyOn(chatService, 'currentSuggestions').mockReturnValue(of(expectedSuggestions));
            vi.spyOn(chatService, 'currentMessages').mockReturnValue(of(mockMessages));

            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.detectChanges();
        });

        it('should not render suggestions when userAccepted is NO_AI', () => {
            component.userAccepted.set(LLMSelectionDecision.NO_AI);
            fixture.changeDetectorRef.detectChanges();

            const suggestionButtons = fixture.nativeElement.querySelectorAll('.iris-suggestion-buttons');
            expect(suggestionButtons).toHaveLength(0);
        });

        it('should not render suggestions when userAccepted is undefined', () => {
            component.userAccepted.set(undefined);
            fixture.changeDetectorRef.detectChanges();

            const suggestionButtons = fixture.nativeElement.querySelectorAll('.iris-suggestion-buttons');
            expect(suggestionButtons).toHaveLength(0);
        });
    });

    describe('Session switcher (widget layout)', () => {
        const mockDate = new Date('2025-10-06T12:00:00.000Z');
        const exerciseSession1: IrisSessionDTO = {
            id: 10,
            title: 'Help with recursion',
            creationDate: new Date('2025-10-06T09:00:00.000Z'),
            chatMode: ChatServiceMode.PROGRAMMING_EXERCISE,
            entityId: 42,
            entityName: 'Exercise 1',
        };
        const exerciseSession2: IrisSessionDTO = {
            id: 11,
            title: 'Array sorting question',
            creationDate: new Date('2025-10-05T09:00:00.000Z'),
            chatMode: ChatServiceMode.PROGRAMMING_EXERCISE,
            entityId: 42,
            entityName: 'Exercise 1',
        };
        const otherEntitySession: IrisSessionDTO = {
            id: 12,
            title: 'Other exercise chat',
            creationDate: new Date('2025-10-06T08:00:00.000Z'),
            chatMode: ChatServiceMode.PROGRAMMING_EXERCISE,
            entityId: 99,
            entityName: 'Exercise 2',
        };
        const lectureSession: IrisSessionDTO = {
            id: 13,
            title: 'Lecture question',
            creationDate: new Date('2025-10-06T07:00:00.000Z'),
            chatMode: ChatServiceMode.LECTURE,
            entityId: 42,
            entityName: 'Lecture 1',
        };

        beforeAll(() => {
            vi.useFakeTimers();
            vi.setSystemTime(mockDate);
        });

        afterAll(() => {
            vi.useRealTimers();
        });

        beforeEach(() => {
            vi.spyOn(chatService, 'availableChatSessions').mockReturnValue(of([exerciseSession1, exerciseSession2, otherEntitySession, lectureSession]));
            vi.spyOn(chatService, 'currentChatMode').mockReturnValue(of(ChatServiceMode.PROGRAMMING_EXERCISE));
            vi.spyOn(chatService, 'currentRelatedEntityId').mockReturnValue(of(42));
            vi.spyOn(chatService, 'currentSessionId').mockReturnValue(of(10));
            vi.spyOn(chatService, 'currentMessages').mockReturnValue(of([mockClientMessage, mockServerMessage]));

            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            fixture.componentRef.setInput('layout', 'widget');
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.detectChanges();
        });

        it('should display current session title', () => {
            expect(component.currentSessionTitle()).toBe('Help with recursion');
        });

        it('should display "New Chat" when current session id is undefined', () => {
            vi.spyOn(chatService, 'currentSessionId').mockReturnValue(of(undefined));

            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            fixture.componentRef.setInput('layout', 'widget');
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.detectChanges();

            // MockTranslateService returns the key as the translation
            expect(component.currentSessionTitle()).toBe('artemisApp.iris.chatHistory.newChat');
        });

        it('should render session title trigger in widget layout', () => {
            const trigger = fixture.nativeElement.querySelector('.session-title-trigger');
            expect(trigger).not.toBeNull();
        });

        it('should not render session title trigger in client layout', () => {
            fixture.componentRef.setInput('layout', 'client');
            fixture.detectChanges();

            const trigger = fixture.nativeElement.querySelector('.session-title-trigger');
            expect(trigger).toBeNull();
        });

        it('should build menu items with group labels and all sessions on toggle', () => {
            const mockEvent = new MouseEvent('click');
            component.toggleSessionMenu(mockEvent);

            // "Today" group label + 3 today sessions + "Older" group label + 1 older session
            expect(component.sessionMenuItems()).toHaveLength(6);
            expect(component.sessionMenuItems()[0].disabled).toBe(true); // "Today" group label
            expect(component.sessionMenuItems()[1].label).toBe('Help with recursion'); // most recent today
            expect(component.sessionMenuItems()[1].data?.isActive).toBe(true); // Current session
            expect(component.sessionMenuItems()[2].label).toBe('Other exercise chat');
            expect(component.sessionMenuItems()[3].label).toBe('Lecture question');
            expect(component.sessionMenuItems()[4].disabled).toBe(true); // "Older" group label
            expect(component.sessionMenuItems()[5].label).toBe('Array sorting question');
            expect(component.sessionMenuItems()[5].data?.isActive).toBe(false);
        });

        it('should still build grouped menu when current session id is undefined', () => {
            vi.spyOn(chatService, 'currentSessionId').mockReturnValue(of(undefined));

            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            fixture.componentRef.setInput('layout', 'widget');
            vi.spyOn(chatService, 'currentChatMode').mockReturnValue(of(ChatServiceMode.PROGRAMMING_EXERCISE));
            vi.spyOn(chatService, 'currentRelatedEntityId').mockReturnValue(of(42));
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.detectChanges();

            const mockEvent = new MouseEvent('click');
            component.toggleSessionMenu(mockEvent);

            // No session is active, but sessions are still grouped
            expect(component.sessionMenuItems()[1].data?.isActive).toBe(false);
            expect(component.sessionMenuItems()[3].data?.isActive).toBe(false);
        });

        it('should call onSessionClick when a today session menu item is clicked', () => {
            vi.spyOn(chatService, 'switchToSession').mockImplementation(() => {});
            const onSessionClickSpy = vi.spyOn(component, 'onSessionClick');
            const mockEvent = new MouseEvent('click');
            component.toggleSessionMenu(mockEvent);

            component.sessionMenuItems()[1].command!({} as any);

            expect(onSessionClickSpy).toHaveBeenCalledWith(exerciseSession1);
        });

        it('should call onSessionClick when an older session menu item is clicked', () => {
            vi.spyOn(chatService, 'switchToSession').mockImplementation(() => {});
            const onSessionClickSpy = vi.spyOn(component, 'onSessionClick');
            const mockEvent = new MouseEvent('click');
            component.toggleSessionMenu(mockEvent);

            // index 5: "Older" label is at [4], exerciseSession2 (the only older session) is at [5]
            component.sessionMenuItems()[5].command!({} as any);

            expect(onSessionClickSpy).toHaveBeenCalledWith(exerciseSession2);
        });

        it('should have hasHeaderContent true for widget layout', () => {
            expect(component.hasHeaderContent()).toBe(true);
        });

        it('should have proper aria attributes on the trigger button', () => {
            const trigger = fixture.nativeElement.querySelector('.session-title-trigger');
            expect(trigger.getAttribute('aria-haspopup')).toBe('menu');
            expect(trigger.getAttribute('aria-expanded')).toBe('false');
        });
    });

    describe('Session switcher (embedded layout)', () => {
        const embeddedSession: IrisSessionDTO = {
            id: 20,
            title: 'Embedded session',
            creationDate: new Date('2025-10-06T09:00:00.000Z'),
            chatMode: ChatServiceMode.PROGRAMMING_EXERCISE,
            entityId: 42,
            entityName: 'Exercise 1',
        };
        const embeddedPastSession: IrisSessionDTO = {
            id: 21,
            title: 'Older embedded session',
            creationDate: new Date('2025-10-05T09:00:00.000Z'),
            chatMode: ChatServiceMode.PROGRAMMING_EXERCISE,
            entityId: 42,
            entityName: 'Exercise 1',
        };
        const unrelatedSession: IrisSessionDTO = {
            id: 22,
            title: 'Unrelated session',
            creationDate: new Date('2025-10-05T08:00:00.000Z'),
            chatMode: ChatServiceMode.LECTURE,
            entityId: 99,
            entityName: 'Lecture 99',
        };

        it('should not render session title trigger in empty embedded mode without related sessions', () => {
            vi.spyOn(chatService, 'availableChatSessions').mockReturnValue(of([embeddedSession]));
            vi.spyOn(chatService, 'currentChatMode').mockReturnValue(of(ChatServiceMode.PROGRAMMING_EXERCISE));
            vi.spyOn(chatService, 'currentRelatedEntityId').mockReturnValue(of(42));
            vi.spyOn(chatService, 'currentSessionId').mockReturnValue(of(20));
            vi.spyOn(chatService, 'currentMessages').mockReturnValue(of([]));

            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            fixture.componentRef.setInput('layout', 'embedded');
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.detectChanges();

            const trigger = fixture.nativeElement.querySelector('.session-title-trigger');
            expect(trigger).toBeNull();
        });

        it('should render session title trigger in empty embedded mode with related sessions', () => {
            vi.spyOn(chatService, 'availableChatSessions').mockReturnValue(of([embeddedSession, embeddedPastSession]));
            vi.spyOn(chatService, 'currentChatMode').mockReturnValue(of(ChatServiceMode.PROGRAMMING_EXERCISE));
            vi.spyOn(chatService, 'currentRelatedEntityId').mockReturnValue(of(42));
            vi.spyOn(chatService, 'currentSessionId').mockReturnValue(of(20));
            vi.spyOn(chatService, 'currentMessages').mockReturnValue(of([]));

            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            fixture.componentRef.setInput('layout', 'embedded');
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.detectChanges();

            const trigger = fixture.nativeElement.querySelector('.session-title-trigger');
            expect(trigger).not.toBeNull();
        });

        it('should not render session title trigger when only unrelated past sessions exist in embedded mode', () => {
            vi.spyOn(chatService, 'availableChatSessions').mockReturnValue(of([embeddedSession, unrelatedSession]));
            vi.spyOn(chatService, 'currentChatMode').mockReturnValue(of(ChatServiceMode.PROGRAMMING_EXERCISE));
            vi.spyOn(chatService, 'currentRelatedEntityId').mockReturnValue(of(42));
            vi.spyOn(chatService, 'currentSessionId').mockReturnValue(of(20));
            vi.spyOn(chatService, 'currentMessages').mockReturnValue(of([]));

            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            fixture.componentRef.setInput('layout', 'embedded');
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.detectChanges();

            const trigger = fixture.nativeElement.querySelector('.session-title-trigger');
            expect(trigger).toBeNull();
        });

        const freshCourseSession: IrisSessionDTO = {
            id: 30,
            title: undefined,
            creationDate: new Date('2025-10-06T10:00:00.000Z'),
            chatMode: ChatServiceMode.COURSE,
            entityId: 7,
            entityName: '',
        };
        const pastCourseSession: IrisSessionDTO = {
            id: 31,
            title: 'Earlier course chat',
            creationDate: new Date('2025-10-05T10:00:00.000Z'),
            chatMode: ChatServiceMode.COURSE,
            entityId: 7,
            entityName: 'Course 7',
        };

        it.each(['widget', 'embedded'] as const)('should not render session title trigger after switching to course context without past course sessions (%s layout)', (layout) => {
            vi.spyOn(chatService, 'availableChatSessions').mockReturnValue(of([freshCourseSession]));
            vi.spyOn(chatService, 'currentChatMode').mockReturnValue(of(ChatServiceMode.COURSE));
            vi.spyOn(chatService, 'currentRelatedEntityId').mockReturnValue(of(7));
            vi.spyOn(chatService, 'currentSessionId').mockReturnValue(of(30));
            vi.spyOn(chatService, 'currentMessages').mockReturnValue(of([]));

            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            fixture.componentRef.setInput('layout', layout);
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.detectChanges();

            const trigger = fixture.nativeElement.querySelector('.session-title-trigger');
            expect(trigger).toBeNull();
        });

        it.each(['widget', 'embedded'] as const)(
            'should render session title trigger after switching to course context when a past course session exists (%s layout)',
            (layout) => {
                vi.spyOn(chatService, 'availableChatSessions').mockReturnValue(of([freshCourseSession, pastCourseSession]));
                vi.spyOn(chatService, 'currentChatMode').mockReturnValue(of(ChatServiceMode.COURSE));
                vi.spyOn(chatService, 'currentRelatedEntityId').mockReturnValue(of(7));
                vi.spyOn(chatService, 'currentSessionId').mockReturnValue(of(30));
                vi.spyOn(chatService, 'currentMessages').mockReturnValue(of([]));

                fixture = TestBed.createComponent(IrisBaseChatbotComponent);
                component = fixture.componentInstance;
                fixture.componentRef.setInput('layout', layout);
                fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
                fixture.detectChanges();

                const trigger = fixture.nativeElement.querySelector('.session-title-trigger');
                expect(trigger).not.toBeNull();
            },
        );

        it('should return an empty activeSuggestionChips list when currentChatMode is undefined', () => {
            vi.spyOn(chatService, 'currentChatMode').mockReturnValue(of(undefined as unknown as ChatServiceMode));
            vi.spyOn(chatService, 'currentMessages').mockReturnValue(of([]));

            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.detectChanges();

            expect((component as any).activeSuggestionChips()).toEqual([]);
            const chips = fixture.nativeElement.querySelectorAll('.prompt-suggestion-chip');
            expect(chips).toHaveLength(0);
        });

        it.each(['widget', 'embedded'] as const)('should render session title trigger when current session has messages even without past sessions (%s layout)', (layout) => {
            const userMessage = {
                sender: IrisSender.USER,
                id: 99,
                content: [{ type: IrisMessageContentType.TEXT, textContent: 'hi' } as IrisTextMessageContent],
                sentAt: dayjs(),
            } as IrisUserMessage;

            vi.spyOn(chatService, 'availableChatSessions').mockReturnValue(of([freshCourseSession]));
            vi.spyOn(chatService, 'currentChatMode').mockReturnValue(of(ChatServiceMode.COURSE));
            vi.spyOn(chatService, 'currentRelatedEntityId').mockReturnValue(of(7));
            vi.spyOn(chatService, 'currentSessionId').mockReturnValue(of(30));
            vi.spyOn(chatService, 'currentMessages').mockReturnValue(of([userMessage]));

            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            fixture.componentRef.setInput('layout', layout);
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.detectChanges();

            expect(component.hasCurrentSessionContent()).toBe(true);
            expect(component.hasSessionSwitcher()).toBe(true);
            const trigger = fixture.nativeElement.querySelector('.session-title-trigger');
            expect(trigger).not.toBeNull();
        });
    });

    describe('onDeleteSession', () => {
        let confirmationService: ConfirmationService;
        let alertService: AlertService;
        const mockSession: IrisSessionDTO = {
            id: 42,
            title: 'Test session',
            creationDate: new Date('2025-06-20T10:00:00.000Z'),
            chatMode: ChatServiceMode.COURSE,
            entityId: 1,
            entityName: 'Course 1',
        };

        beforeEach(() => {
            confirmationService = fixture.debugElement.injector.get(ConfirmationService);
            alertService = TestBed.inject(AlertService);
        });

        it('should show confirmation dialog with correct i18n keys', () => {
            const confirmSpy = vi.spyOn(confirmationService, 'confirm');

            component.onDeleteSession(mockSession);

            expect(confirmSpy).toHaveBeenCalledOnce();
            const callArgs = confirmSpy.mock.calls[0][0];
            expect(callArgs.header).toBe('artemisApp.iris.chatHistory.deleteSessionHeader');
            expect(callArgs.message).toContain('artemisApp.iris.chatHistory.deleteSessionQuestion');
            expect(callArgs.acceptLabel).toBe('entity.action.delete');
            expect(callArgs.rejectLabel).toBe('entity.action.cancel');
        });

        it('should call chatService.deleteSession on accept', () => {
            vi.spyOn(confirmationService, 'confirm').mockImplementation((confirmation) => {
                confirmation.accept!();
                return confirmationService;
            });
            const deleteSessionSpy = vi.spyOn(chatService, 'deleteSession').mockReturnValue(of(undefined));

            component.onDeleteSession(mockSession);

            expect(deleteSessionSpy).toHaveBeenCalledWith(42);
        });

        it('should show success alert after successful deletion', () => {
            vi.spyOn(confirmationService, 'confirm').mockImplementation((confirmation) => {
                confirmation.accept!();
                return confirmationService;
            });
            vi.spyOn(chatService, 'deleteSession').mockReturnValue(of(undefined));
            const successSpy = vi.spyOn(alertService, 'success');

            component.onDeleteSession(mockSession);

            expect(successSpy).toHaveBeenCalledWith('artemisApp.iris.chatHistory.deleteSessionSuccess');
        });

        it('should not call deleteSession on rejection', () => {
            vi.spyOn(confirmationService, 'confirm').mockImplementation((confirmation) => {
                if (confirmation.reject) {
                    confirmation.reject();
                }
                return confirmationService;
            });
            const deleteSessionSpy = vi.spyOn(chatService, 'deleteSession');

            component.onDeleteSession(mockSession);

            expect(deleteSessionSpy).not.toHaveBeenCalled();
        });
    });

    describe('MCQ content rendering', () => {
        it('should identify MCQ content using isMcqContent helper', () => {
            const mcqContent = new IrisJsonMessageContent({
                type: 'mcq',
                question: 'Q?',
                options: [
                    { text: 'A', correct: false },
                    { text: 'B', correct: true },
                ],
                explanation: 'E',
            });
            const textContent = new IrisTextMessageContent('hello');

            expect(isMcqContent(mcqContent)).toBe(true);
            expect(isMcqContent(textContent)).toBe(false);
        });

        it('should extract MCQ data using getMcqData helper', () => {
            const mcqContent = new IrisJsonMessageContent({
                type: 'mcq',
                question: 'Q?',
                options: [
                    { text: 'A', correct: false },
                    { text: 'B', correct: true },
                ],
                explanation: 'E',
            });
            const textContent = new IrisTextMessageContent('hello');

            const data = getMcqData(mcqContent);
            expect(data).toBeDefined();
            expect(data?.question).toBe('Q?');
            expect(data?.options).toHaveLength(2);

            expect(getMcqData(textContent)).toBeUndefined();
        });

        it('should render MCQ component for MCQ messages', () => {
            const mcqContent = new IrisJsonMessageContent({
                type: 'mcq',
                question: 'What is 1+1?',
                options: [
                    { text: '2', correct: true },
                    { text: '3', correct: false },
                ],
                explanation: 'Math',
            });
            const mcqMessage = {
                sender: IrisSender.LLM,
                id: 20,
                content: [mcqContent],
                sentAt: dayjs(),
            } as IrisAssistantMessage;

            vi.spyOn(chatService, 'currentMessages').mockReturnValue(of([mcqMessage]));

            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.detectChanges();

            const mcqElement = fixture.nativeElement.querySelector('jhi-iris-mcq-question');
            expect(mcqElement).toBeTruthy();

            const textBubble = fixture.nativeElement.querySelector('.bubble-left');
            expect(textBubble).toBeFalsy();
        });
    });

    describe('activeChatMessage computed signal', () => {
        const mockMessages = [mockClientMessage, mockServerMessage];

        it('should show thinking bubble when a stage has IN_PROGRESS state and chatMessage', () => {
            const stageWithChat: IrisStageDTO = {
                name: 'Thinking',
                weight: 1,
                state: IrisStageStateDTO.IN_PROGRESS,
                message: 'Processing...',
                internal: false,
                chatMessage: 'Analyzing your code...',
            };
            vi.spyOn(chatService, 'currentStages').mockReturnValue(of([stageWithChat]));
            vi.spyOn(chatService, 'currentMessages').mockReturnValue(of(mockMessages));

            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.detectChanges();

            expect(component.activeChatMessage()).toBe('Analyzing your code...');
            const thinkingBubble = fixture.debugElement.query(By.css('jhi-iris-thinking-bubble'));
            expect(thinkingBubble).toBeTruthy();
        });

        it('should not show thinking bubble when no stage has chatMessage', () => {
            const stageWithoutChat: IrisStageDTO = {
                name: 'Thinking',
                weight: 1,
                state: IrisStageStateDTO.IN_PROGRESS,
                message: 'Processing...',
                internal: false,
            };
            vi.spyOn(chatService, 'currentStages').mockReturnValue(of([stageWithoutChat]));
            vi.spyOn(chatService, 'currentMessages').mockReturnValue(of(mockMessages));

            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.detectChanges();

            expect(component.activeChatMessage()).toBeUndefined();
            const thinkingBubble = fixture.debugElement.query(By.css('jhi-iris-thinking-bubble'));
            expect(thinkingBubble).toBeFalsy();
        });

        it('should not show thinking bubble when all stages are DONE', () => {
            const doneStage: IrisStageDTO = {
                name: 'Complete',
                weight: 1,
                state: IrisStageStateDTO.DONE,
                message: 'Done',
                internal: false,
                chatMessage: 'Finished analysis',
            };
            vi.spyOn(chatService, 'currentStages').mockReturnValue(of([doneStage]));
            vi.spyOn(chatService, 'currentMessages').mockReturnValue(of(mockMessages));

            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.detectChanges();

            expect(component.activeChatMessage()).toBeUndefined();
            const thinkingBubble = fixture.debugElement.query(By.css('jhi-iris-thinking-bubble'));
            expect(thinkingBubble).toBeFalsy();
        });

        it('should update thinking bubble message when chatMessage changes', () => {
            const stagesSubject = new BehaviorSubject<IrisStageDTO[]>([
                {
                    name: 'Thinking',
                    weight: 1,
                    state: IrisStageStateDTO.IN_PROGRESS,
                    message: 'Processing...',
                    internal: false,
                    chatMessage: 'Initial message',
                },
            ]);
            vi.spyOn(chatService, 'currentStages').mockReturnValue(stagesSubject.asObservable());
            vi.spyOn(chatService, 'currentMessages').mockReturnValue(of(mockMessages));

            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.detectChanges();

            expect(component.activeChatMessage()).toBe('Initial message');

            // Update the chatMessage
            stagesSubject.next([
                {
                    name: 'Thinking',
                    weight: 1,
                    state: IrisStageStateDTO.IN_PROGRESS,
                    message: 'Processing...',
                    internal: false,
                    chatMessage: 'Updated message',
                },
            ]);
            fixture.detectChanges();

            expect(component.activeChatMessage()).toBe('Updated message');
            const thinkingBubble = fixture.debugElement.query(By.css('jhi-iris-thinking-bubble'));
            expect(thinkingBubble).toBeTruthy();
        });
    });

    describe('processMessages newline handling', () => {
        it('should not apply newline doubling to any messages', () => {
            const tableMarkdown = '| Item | Details |\n|------|--------|\n| Lang | Java |';
            const llmMessage = {
                sender: IrisSender.LLM,
                id: 10,
                content: [{ type: IrisMessageContentType.TEXT, textContent: tableMarkdown } as IrisTextMessageContent],
                sentAt: dayjs(),
            } as IrisAssistantMessage;

            const userText = 'Line1\nLine2\n\nLine3';
            const userMessage = {
                sender: IrisSender.USER,
                id: 11,
                content: [{ type: IrisMessageContentType.TEXT, textContent: userText } as IrisTextMessageContent],
                sentAt: dayjs(),
            } as IrisUserMessage;

            vi.spyOn(chatService, 'currentMessages').mockReturnValue(of([userMessage, llmMessage]));

            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.detectChanges();

            const processedMessages = component.messages();
            const userContent = processedMessages[0].content![0] as IrisTextMessageContent;
            const llmContent = processedMessages[1].content![0] as IrisTextMessageContent;
            // Neither message type should have newlines modified — line breaks are handled by markdown-it's breaks: true option
            expect(llmContent.textContent).toBe(tableMarkdown);
            expect(userContent.textContent).toBe(userText);
        });
    });

    describe('openAboutIrisModal transport selection', () => {
        it('should open via MatDialog when layout is widget', () => {
            const matDialog = TestBed.inject(MatDialog);
            const matDialogOpenSpy = vi.spyOn(matDialog, 'open').mockReturnValue({ close: vi.fn() } as any);
            const dialogService = TestBed.inject(DialogService);
            const dialogServiceOpenSpy = vi.spyOn(dialogService, 'open');

            fixture.componentRef.setInput('layout', 'widget');
            fixture.detectChanges();

            component.openAboutIrisModal();

            expect(matDialogOpenSpy).toHaveBeenCalledOnce();
            expect(dialogServiceOpenSpy).not.toHaveBeenCalled();
        });

        it('should open via PrimeNG DialogService when layout is client', () => {
            const matDialog = TestBed.inject(MatDialog);
            const matDialogOpenSpy = vi.spyOn(matDialog, 'open');
            const dialogService = TestBed.inject(DialogService);
            const dialogServiceOpenSpy = vi.spyOn(dialogService, 'open').mockReturnValue({ close: vi.fn() } as any);

            fixture.componentRef.setInput('layout', 'client');
            fixture.detectChanges();

            component.openAboutIrisModal();

            expect(dialogServiceOpenSpy).toHaveBeenCalledOnce();
            expect(matDialogOpenSpy).not.toHaveBeenCalled();
        });
    });

    describe('suggestion chips', () => {
        beforeEach(() => {
            statusMock.getActiveStatus.mockReturnValue(of({}));
            statusMock.currentRatelimitInfo.mockReturnValue(of({}));
            vi.spyOn(chatService, 'currentChatMode').mockReturnValue(of(ChatServiceMode.COURSE));
            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
        });

        it('should render suggestion chips on empty general state', () => {
            fixture.detectChanges();
            const chips = fixture.nativeElement.querySelectorAll('.prompt-suggestion-chip');
            expect(chips).toHaveLength(3);
        });

        it('should disable suggestion chips when iris is unavailable', () => {
            statusMock.getActiveStatus.mockReturnValue(of(false));
            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.detectChanges();

            const chips: NodeListOf<HTMLButtonElement> = fixture.nativeElement.querySelectorAll('.prompt-suggestion-chip');
            expect(chips).toHaveLength(3);
            chips.forEach((chip) => expect(chip.disabled).toBe(true));
        });

        it('should not render suggestion chips when isEmbeddedChat is true', () => {
            fixture.componentRef.setInput('isEmbeddedChat', true);
            fixture.detectChanges();
            const chips = fixture.nativeElement.querySelectorAll('.prompt-suggestion-chip');
            expect(chips).toHaveLength(0);
        });

        it('should not render suggestion chips in embedded layout empty state', () => {
            fixture.componentRef.setInput('layout', 'embedded');
            fixture.detectChanges();
            const chips = fixture.nativeElement.querySelectorAll('.prompt-suggestion-chip');
            expect(chips).toHaveLength(0);
        });

        it('should not render suggestion chips in widget layout empty state', () => {
            fixture.componentRef.setInput('layout', 'widget');
            fixture.detectChanges();
            const chips = fixture.nativeElement.querySelectorAll('.prompt-suggestion-chip');
            expect(chips).toHaveLength(0);
        });

        it('should not render suggestion chips when messages exist', () => {
            vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponse));
            vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
            vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));

            chatService.switchTo(ChatServiceMode.COURSE, 456);
            fixture.detectChanges();

            const chips = fixture.nativeElement.querySelectorAll('.prompt-suggestion-chip');
            expect(chips).toHaveLength(0);
        });

        it('should call applyChipText with correct starter key when Learn chip is clicked', () => {
            fixture.detectChanges();
            const applyChipTextSpy = vi.spyOn(component, 'applyChipText');
            const chips = fixture.nativeElement.querySelectorAll('.prompt-suggestion-chip');
            chips[0].click();
            expect(applyChipTextSpy).toHaveBeenCalledWith('artemisApp.iris.chat.suggestions.learnStarter');
        });

        it('should call applyChipText with correct starter key when Quiz chip is clicked', () => {
            fixture.detectChanges();
            const applyChipTextSpy = vi.spyOn(component, 'applyChipText');
            const chips = fixture.nativeElement.querySelectorAll('.prompt-suggestion-chip');
            chips[1].click();
            expect(applyChipTextSpy).toHaveBeenCalledWith('artemisApp.iris.chat.suggestions.quizTopicStarter');
        });

        it('should call applyChipText with correct starter key when Tips chip is clicked', () => {
            fixture.detectChanges();
            const applyChipTextSpy = vi.spyOn(component, 'applyChipText');
            const chips = fixture.nativeElement.querySelectorAll('.prompt-suggestion-chip');
            chips[2].click();
            expect(applyChipTextSpy).toHaveBeenCalledWith('artemisApp.iris.chat.suggestions.tipsStarter');
        });

        it('should set textarea content and focus when applyChipText is called', async () => {
            fixture.detectChanges();
            const starterKey = 'artemisApp.iris.chat.suggestions.learnStarter';
            component.applyChipText(starterKey);
            expect(component.newMessageTextContent()).toBe(starterKey);

            await new Promise((resolve) => setTimeout(resolve, 0));

            const textarea = fixture.debugElement.query(By.css('textarea'));
            expect(textarea).toBeTruthy();
        });

        it('should show preview text on chip hover', () => {
            fixture.detectChanges();
            const starterKey = 'artemisApp.iris.chat.suggestions.learnStarter';
            component.onChipMouseEnter(starterKey);
            expect(component.chipPreviewText()).toBe(starterKey);
        });

        it('should clear preview text on chip mouse leave', () => {
            fixture.detectChanges();
            component.onChipMouseEnter('artemisApp.iris.chat.suggestions.learnStarter');
            component.onChipMouseLeave();
            expect(component.chipPreviewText()).toBe('');
        });

        it('should not clear applied text on mouse leave after click', () => {
            fixture.detectChanges();
            const starterKey = 'artemisApp.iris.chat.suggestions.learnStarter';
            component.applyChipText(starterKey);
            component.onChipMouseLeave();
            expect(component.newMessageTextContent()).toBe(starterKey);
            expect(component.chipPreviewText()).toBe('');
        });

        it('should not show preview on hover after chip click', () => {
            fixture.detectChanges();
            component.applyChipText('artemisApp.iris.chat.suggestions.learnStarter');
            component.onChipMouseEnter('artemisApp.iris.chat.suggestions.quizTopicStarter');
            expect(component.chipPreviewText()).toBe('');
        });

        it('should render all three chips in fixed order: learn, quiz, tips', () => {
            fixture.detectChanges();
            const chipKeys = COURSE_SUGGESTION_CHIPS.map((chip) => chip.translationKey);
            expect(chipKeys).toEqual(['artemisApp.iris.chat.suggestions.learn', 'artemisApp.iris.chat.suggestions.quiz', 'artemisApp.iris.chat.suggestions.tips']);
        });

        it('should not show chip preview overlay when input is disabled', () => {
            fixture.detectChanges();
            component.onChipMouseEnter('artemisApp.iris.chat.suggestions.learnStarter');
            component.isLoading.set(true);
            fixture.detectChanges();
            const overlay = fixture.nativeElement.querySelector('.chip-preview-overlay');
            expect(overlay).toBeNull();
        });

        const recreateFixtureForMode = (mode: ChatServiceMode) => {
            vi.spyOn(chatService, 'currentChatMode').mockReturnValue(of(mode));
            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.detectChanges();
        };

        it('should call applyChipText with lecture-specific quiz starter when Quiz chip is clicked in lecture mode', () => {
            recreateFixtureForMode(ChatServiceMode.LECTURE);
            const applyChipTextSpy = vi.spyOn(component, 'applyChipText');
            const chips = fixture.nativeElement.querySelectorAll('.prompt-suggestion-chip');
            chips[1].click();
            expect(applyChipTextSpy).toHaveBeenCalledWith('artemisApp.iris.chat.suggestions.quizLectureStarter');
        });

        it('should call applyChipText with exercise-specific quiz starter when Quiz chip is clicked in programming exercise mode', () => {
            recreateFixtureForMode(ChatServiceMode.PROGRAMMING_EXERCISE);
            const applyChipTextSpy = vi.spyOn(component, 'applyChipText');
            const chips = fixture.nativeElement.querySelectorAll('.prompt-suggestion-chip');
            chips[1].click();
            expect(applyChipTextSpy).toHaveBeenCalledWith('artemisApp.iris.chat.suggestions.quizExerciseStarter');
        });

        it('should call applyChipText with exercise-specific quiz starter when Quiz chip is clicked in text exercise mode', () => {
            recreateFixtureForMode(ChatServiceMode.TEXT_EXERCISE);
            const applyChipTextSpy = vi.spyOn(component, 'applyChipText');
            const chips = fixture.nativeElement.querySelectorAll('.prompt-suggestion-chip');
            chips[1].click();
            expect(applyChipTextSpy).toHaveBeenCalledWith('artemisApp.iris.chat.suggestions.quizExerciseStarter');
        });
    });

    describe('Cycling placeholder labels and ghost text', () => {
        const exerciseSession: IrisSessionDTO = {
            id: 10,
            title: 'Help with recursion',
            creationDate: new Date(),
            chatMode: ChatServiceMode.PROGRAMMING_EXERCISE,
            entityId: 42,
            entityName: 'Sorting Arrays',
        };
        const lectureSession: IrisSessionDTO = {
            id: 20,
            title: 'Lecture question',
            creationDate: new Date(),
            chatMode: ChatServiceMode.LECTURE,
            entityId: 55,
            entityName: 'Data Structures',
        };

        describe('exercise mode', () => {
            beforeEach(() => {
                vi.spyOn(chatService, 'availableChatSessions').mockReturnValue(of([exerciseSession]));
                vi.spyOn(chatService, 'currentChatMode').mockReturnValue(of(ChatServiceMode.PROGRAMMING_EXERCISE));
                vi.spyOn(chatService, 'currentRelatedEntityId').mockReturnValue(of(42));
                vi.spyOn(chatService, 'currentSessionId').mockReturnValue(of(10));
                vi.spyOn(chatService, 'currentMessages').mockReturnValue(of([]));

                fixture = TestBed.createComponent(IrisBaseChatbotComponent);
                component = fixture.componentInstance;
                fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
                fixture.detectChanges();
            });

            it('should detect exercise mode', () => {
                expect(component.isExerciseOrLectureMode()).toBeTruthy();
            });

            it('should interpolate exercise labels', () => {
                const labels = component.interpolatedLabels();
                expect(labels).toHaveLength(4);
                expect(labels).toContain('artemisApp.iris.chat.placeholders.exercise.whereToStart');
            });

            it('should provide a current placeholder', () => {
                const labels = component.interpolatedLabels();
                expect(labels).toContain(component.currentPlaceholder());
            });

            it('should show exercise suggestion chips on empty exercise screen in client layout', () => {
                const chips = fixture.nativeElement.querySelectorAll('.prompt-suggestion-chip');
                expect(chips).toHaveLength(3);
            });

            it('should hide suggestion chips on exercise screen in widget layout', () => {
                fixture.componentRef.setInput('layout', 'widget');
                fixture.detectChanges();
                const chips = fixture.nativeElement.querySelectorAll('.prompt-suggestion-chip');
                expect(chips).toHaveLength(0);
            });

            it('should not advance placeholder index immediately on blur with empty input', () => {
                expect(component.placeholderIndex()).toBe(0);

                // Simulate focus then blur; cycling should restart without immediate index change
                component.onTextareaFocus();
                component.onTextareaBlur();

                expect(component.placeholderIndex()).toBe(0);
            });

            it('should track focus state without immediate index change on blur', () => {
                component.onTextareaFocus();
                expect(component.isFocused()).toBeTruthy();
                const indexAtFocus = component.placeholderIndex();

                // Blur with empty input restarts cycling without jumping to next label
                component.onTextareaBlur();
                expect(component.isFocused()).toBeFalsy();
                expect(component.placeholderIndex()).toBe(indexAtFocus);
            });

            it('should not advance index on blur when input has text', () => {
                component.newMessageTextContent.set('some text');
                component.onTextareaFocus();
                const indexAtFocus = component.placeholderIndex();

                component.onTextareaBlur();
                expect(component.placeholderIndex()).toBe(indexAtFocus);
            });

            it('should cycle placeholder after interval', () => {
                vi.useFakeTimers();

                // Rotating placeholder only runs in widget/embedded layouts
                fixture.componentRef.setInput('layout', 'widget');
                fixture.detectChanges();

                // Trigger cycling by simulating blur with empty input
                component.onTextareaFocus();
                component.onTextareaBlur();

                const indexAfterBlur = component.placeholderIndex();

                // Advance past the cycle interval
                vi.advanceTimersByTime(PLACEHOLDER_CYCLE_INTERVAL_MS);
                expect(component.placeholderVisible()).toBe(false);

                // Advance past the fade duration to swap text
                vi.advanceTimersByTime(PLACEHOLDER_FADE_DURATION_MS);
                expect(component.placeholderIndex()).toBe((indexAfterBlur + 1) % component.interpolatedLabels().length);
                expect(component.placeholderVisible()).toBe(true);

                vi.useRealTimers();
            });
        });

        describe('exercise mode with existing messages', () => {
            beforeEach(() => {
                vi.spyOn(chatService, 'availableChatSessions').mockReturnValue(of([exerciseSession]));
                vi.spyOn(chatService, 'currentChatMode').mockReturnValue(of(ChatServiceMode.PROGRAMMING_EXERCISE));
                vi.spyOn(chatService, 'currentRelatedEntityId').mockReturnValue(of(42));
                vi.spyOn(chatService, 'currentSessionId').mockReturnValue(of(10));
                vi.spyOn(chatService, 'currentMessages').mockReturnValue(of([mockClientMessage]));

                fixture = TestBed.createComponent(IrisBaseChatbotComponent);
                component = fixture.componentInstance;
                fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
                fixture.detectChanges();
            });

            it('should show the default placeholder label', () => {
                expect(component.textareaPlaceholder()).toBe('artemisApp.exerciseChatbot.inputMessage');
            });

            it('should not cycle placeholder labels on blur', () => {
                vi.useFakeTimers();

                const indexAtStart = component.placeholderIndex();
                component.onTextareaFocus();
                component.onTextareaBlur();

                vi.advanceTimersByTime(PLACEHOLDER_CYCLE_INTERVAL_MS + PLACEHOLDER_FADE_DURATION_MS);
                expect(component.placeholderIndex()).toBe(indexAtStart);
                expect(component.placeholderVisible()).toBe(true);

                vi.useRealTimers();
            });
        });

        describe('lecture mode', () => {
            beforeEach(() => {
                vi.spyOn(chatService, 'availableChatSessions').mockReturnValue(of([lectureSession]));
                vi.spyOn(chatService, 'currentChatMode').mockReturnValue(of(ChatServiceMode.LECTURE));
                vi.spyOn(chatService, 'currentRelatedEntityId').mockReturnValue(of(55));
                vi.spyOn(chatService, 'currentSessionId').mockReturnValue(of(20));
                vi.spyOn(chatService, 'currentMessages').mockReturnValue(of([]));

                fixture = TestBed.createComponent(IrisBaseChatbotComponent);
                component = fixture.componentInstance;
                fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
                fixture.detectChanges();
            });

            it('should interpolate lecture labels', () => {
                const labels = component.interpolatedLabels();
                expect(labels).toHaveLength(2);
                expect(labels).toContain('artemisApp.iris.chat.placeholders.lecture.keyPoints');
            });

            it('should show lecture suggestion chips on empty lecture screen in client layout', () => {
                const chips = fixture.nativeElement.querySelectorAll('.prompt-suggestion-chip');
                expect(chips).toHaveLength(3);
            });

            it('should hide suggestion chips on lecture screen in widget layout', () => {
                fixture.componentRef.setInput('layout', 'widget');
                fixture.detectChanges();
                const chips = fixture.nativeElement.querySelectorAll('.prompt-suggestion-chip');
                expect(chips).toHaveLength(0);
            });
        });

        describe('lecture mode with existing messages', () => {
            beforeEach(() => {
                vi.spyOn(chatService, 'availableChatSessions').mockReturnValue(of([lectureSession]));
                vi.spyOn(chatService, 'currentChatMode').mockReturnValue(of(ChatServiceMode.LECTURE));
                vi.spyOn(chatService, 'currentRelatedEntityId').mockReturnValue(of(55));
                vi.spyOn(chatService, 'currentSessionId').mockReturnValue(of(20));
                vi.spyOn(chatService, 'currentMessages').mockReturnValue(of([mockClientMessage]));

                fixture = TestBed.createComponent(IrisBaseChatbotComponent);
                component = fixture.componentInstance;
                fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
                fixture.detectChanges();
            });

            it('should show the default placeholder label', () => {
                expect(component.textareaPlaceholder()).toBe('artemisApp.exerciseChatbot.inputMessage');
            });
        });

        describe('course mode (no cycling)', () => {
            beforeEach(() => {
                vi.spyOn(chatService, 'currentChatMode').mockReturnValue(of(ChatServiceMode.COURSE));
                vi.spyOn(chatService, 'currentMessages').mockReturnValue(of([]));
                fixture = TestBed.createComponent(IrisBaseChatbotComponent);
                component = fixture.componentInstance;
                fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
                fixture.detectChanges();
            });

            it('should not be in exercise or lecture mode', () => {
                expect(component.isExerciseOrLectureMode()).toBeFalsy();
            });

            it('should return empty interpolated labels', () => {
                expect(component.interpolatedLabels()).toHaveLength(0);
            });

            it('should show suggestion chips on course screen', () => {
                fixture.detectChanges();
                const chips = fixture.nativeElement.querySelectorAll('.prompt-suggestion-chip');
                expect(chips.length).toBeGreaterThan(0);
            });
        });

        describe('ghost text', () => {
            beforeEach(() => {
                vi.spyOn(chatService, 'availableChatSessions').mockReturnValue(of([exerciseSession]));
                vi.spyOn(chatService, 'currentChatMode').mockReturnValue(of(ChatServiceMode.PROGRAMMING_EXERCISE));
                vi.spyOn(chatService, 'currentRelatedEntityId').mockReturnValue(of(42));
                vi.spyOn(chatService, 'currentSessionId').mockReturnValue(of(10));
                vi.spyOn(chatService, 'currentMessages').mockReturnValue(of([]));

                fixture = TestBed.createComponent(IrisBaseChatbotComponent);
                component = fixture.componentInstance;
                fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
                fixture.detectChanges();
            });

            it('should show ghost text when input matches a label prefix', () => {
                // MockTranslateService returns the key, so labels are translation keys
                const label = component.interpolatedLabels()[0];
                const prefix = label.substring(0, 10);
                component.newMessageTextContent.set(prefix);
                fixture.detectChanges();
                expect(component.ghostText()).toBe(label.substring(10));
            });

            it('should clear ghost text when input does not match any label', () => {
                component.newMessageTextContent.set('Something random');
                fixture.detectChanges();
                expect(component.ghostText()).toBe('');
            });

            it('should be case insensitive', () => {
                const label = component.interpolatedLabels()[0];
                const prefix = label.substring(0, 10).toLowerCase();
                component.newMessageTextContent.set(prefix);
                fixture.detectChanges();
                expect(component.ghostText()).toBe(label.substring(10));
            });

            it('should clear ghost text when input is empty', () => {
                component.newMessageTextContent.set('');
                fixture.detectChanges();
                expect(component.ghostText()).toBe('');
            });

            it('should accept ghost text on Tab key', () => {
                const label = component.interpolatedLabels()[0];
                const prefix = label.substring(0, 10);
                component.newMessageTextContent.set(prefix);
                fixture.detectChanges();
                expect(component.ghostText()).toBe(label.substring(10));

                const event = new KeyboardEvent('keydown', { key: 'Tab' });
                vi.spyOn(event, 'preventDefault');
                component.handleKey(event);

                expect(event.preventDefault).toHaveBeenCalled();
                expect(component.newMessageTextContent()).toBe(label);
                expect(component.ghostText()).toBe('');
            });

            it('should accept ghost text on ArrowRight key when cursor is at end', () => {
                const label = component.interpolatedLabels()[1];
                const prefix = label.substring(0, 47);
                component.newMessageTextContent.set(prefix);
                fixture.detectChanges();
                expect(component.ghostText()).toBe(label.substring(47));

                // Place cursor at end of input (set value to sync JSDOM with Angular model)
                const textarea = fixture.debugElement.query(By.css('textarea')).nativeElement as HTMLTextAreaElement;
                textarea.value = prefix;
                textarea.setSelectionRange(prefix.length, prefix.length);

                const event = new KeyboardEvent('keydown', { key: 'ArrowRight' });
                vi.spyOn(event, 'preventDefault');
                component.handleKey(event);

                expect(event.preventDefault).toHaveBeenCalled();
                expect(component.newMessageTextContent()).toBe(label);
            });

            it('should not accept ghost text on ArrowRight key when cursor is in middle', () => {
                const label = component.interpolatedLabels()[1];
                const prefix = label.substring(0, 47);
                component.newMessageTextContent.set(prefix);
                fixture.detectChanges();
                expect(component.ghostText()).toBe(label.substring(47));

                // Place cursor in the middle of input (set value to sync JSDOM with Angular model)
                const textarea = fixture.debugElement.query(By.css('textarea')).nativeElement as HTMLTextAreaElement;
                textarea.value = prefix;
                textarea.setSelectionRange(5, 5);

                const event = new KeyboardEvent('keydown', { key: 'ArrowRight' });
                vi.spyOn(event, 'preventDefault');
                component.handleKey(event);

                expect(event.preventDefault).not.toHaveBeenCalled();
                expect(component.newMessageTextContent()).toBe(prefix);
            });

            it('should not show ghost text on course screen', () => {
                // Recreate with course mode
                vi.spyOn(chatService, 'currentChatMode').mockReturnValue(of(ChatServiceMode.COURSE));
                fixture = TestBed.createComponent(IrisBaseChatbotComponent);
                component = fixture.componentInstance;
                fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
                fixture.detectChanges();

                component.newMessageTextContent.set('Help me');
                fixture.detectChanges();
                expect(component.ghostText()).toBe('');
            });
        });
    });

    describe('shouldShowStatusBar', () => {
        function createComponentWithStages(stages: IrisStageDTO[]): IrisBaseChatbotComponent {
            vi.spyOn(chatService, 'currentStages').mockReturnValue(of(stages));

            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            const comp = fixture.componentInstance;
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.detectChanges();
            return comp;
        }

        it('should return false when stages are empty', () => {
            const comp = createComponentWithStages([]);
            expect(comp.shouldShowStatusBar()).toBe(false);
        });

        it('should return false when all stages are DONE or SKIPPED', () => {
            const stages: IrisStageDTO[] = [
                { name: 'Stage 1', weight: 1, state: IrisStageStateDTO.DONE, message: '', internal: false } as IrisStageDTO,
                { name: 'Stage 2', weight: 1, state: IrisStageStateDTO.SKIPPED, message: '', internal: false } as IrisStageDTO,
            ];
            const comp = createComponentWithStages(stages);
            expect(comp.shouldShowStatusBar()).toBe(false);
        });

        it('should return true when a non-internal stage is IN_PROGRESS', () => {
            const stages: IrisStageDTO[] = [
                { name: 'Stage 1', weight: 1, state: IrisStageStateDTO.DONE, message: '', internal: false } as IrisStageDTO,
                { name: 'Stage 2', weight: 1, state: IrisStageStateDTO.IN_PROGRESS, message: '', internal: false } as IrisStageDTO,
            ];
            const comp = createComponentWithStages(stages);
            expect(comp.shouldShowStatusBar()).toBe(true);
        });

        it('should return true when a non-internal stage is ERROR', () => {
            const stages: IrisStageDTO[] = [{ name: 'Stage 1', weight: 1, state: IrisStageStateDTO.ERROR, message: '', internal: false } as IrisStageDTO];
            const comp = createComponentWithStages(stages);
            expect(comp.shouldShowStatusBar()).toBe(true);
        });

        it('should return false when only internal stages are unfinished', () => {
            const stages: IrisStageDTO[] = [
                { name: 'Stage 1', weight: 1, state: IrisStageStateDTO.DONE, message: '', internal: false } as IrisStageDTO,
                { name: 'Internal Stage', weight: 1, state: IrisStageStateDTO.IN_PROGRESS, message: '', internal: true } as IrisStageDTO,
            ];
            const comp = createComponentWithStages(stages);
            expect(comp.shouldShowStatusBar()).toBe(false);
        });
    });
});
