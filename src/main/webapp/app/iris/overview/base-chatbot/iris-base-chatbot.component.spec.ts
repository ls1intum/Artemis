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
import { TranslateService } from '@ngx-translate/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { UserService } from 'app/core/user/shared/user.service';
import { IrisStatusService } from 'app/iris/overview/services/iris-status.service';
import { IrisChatHttpService } from 'app/iris/overview/services/iris-chat-http.service';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { IrisWebsocketService } from 'app/iris/overview/services/iris-websocket.service';
import { of } from 'rxjs';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import {
    mockClientMessage,
    mockServerMessage,
    mockServerSessionHttpResponse,
    mockServerSessionHttpResponseWithEmptyConversation,
    mockServerSessionHttpResponseWithId,
    mockUserMessageWithContent,
    mockWebsocketServerMessage,
} from 'test/helpers/sample/iris-sample-data';
import { By } from '@angular/platform-browser';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { IrisAssistantMessage, IrisMessage, IrisSender, IrisUserMessage } from 'app/iris/shared/entities/iris-message.model';
import { IrisMessageContentType, IrisTextMessageContent } from 'app/iris/shared/entities/iris-content-type.model';
import dayjs from 'dayjs/esm';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { IrisSessionDTO } from 'app/iris/shared/entities/iris-session-dto.model';
import { IrisStageDTO, IrisStageStateDTO } from 'app/iris/shared/entities/iris-stage-dto.model';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { User } from 'app/core/user/user.model';
import { LLMSelectionDecision, LLM_MODAL_DISMISSED } from 'app/core/user/shared/dto/updateLLMSelectionDecision.dto';
import { LLMSelectionModalService } from 'app/logos/llm-selection-popup.service';
import { DialogService } from 'primeng/dynamicdialog';
import { IrisOnboardingService } from 'app/iris/overview/iris-onboarding-modal/iris-onboarding.service';

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
    const mockOnboardingService = {
        showOnboardingIfNeeded: vi.fn().mockResolvedValue(undefined),
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
                MockProvider(DialogService),
                { provide: IrisOnboardingService, useValue: mockOnboardingService },
                MockProvider(ActivatedRoute),
                MockProvider(IrisChatHttpService),
                MockProvider(IrisWebsocketService),
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
        vi.spyOn(httpService, 'createMessage').mockReturnValueOnce(of({ body: createdMessage } as HttpResponse<IrisUserMessage>));

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
        vi.spyOn(httpService, 'resendMessage').mockReturnValueOnce(of({ body: createdMessage } as HttpResponse<IrisUserMessage>));
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
        vi.spyOn(httpService, 'rateMessage').mockReturnValueOnce(of({} as HttpResponse<IrisMessage>));
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
        vi.spyOn(httpService, 'createMessage').mockReturnValueOnce(of({ body: createdMessage } as HttpResponse<IrisUserMessage>));

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
            const adjustScrollButtonPositionSpy = vi.spyOn(component, 'adjustScrollButtonPosition');

            component.adjustTextareaRows();

            expect(textarea.style.height).toBe('');
            expect(adjustScrollButtonPositionSpy).toHaveBeenCalledWith(1);
        });

        it('should reset height and return early when textarea has only whitespace', () => {
            component.userAccepted.set(LLMSelectionDecision.CLOUD_AI);
            fixture.detectChanges();

            const textarea = fixture.nativeElement.querySelector('textarea');
            textarea.value = '   ';
            const adjustScrollButtonPositionSpy = vi.spyOn(component, 'adjustScrollButtonPosition');

            component.adjustTextareaRows();

            expect(textarea.style.height).toBe('');
            expect(adjustScrollButtonPositionSpy).toHaveBeenCalledWith(1);
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
    });

    describe('Onboarding', () => {
        let onboardingService: IrisOnboardingService;

        beforeEach(() => {
            onboardingService = TestBed.inject(IrisOnboardingService);
        });

        it('should call showOnboardingIfNeeded on ngAfterViewInit', () => {
            mockOnboardingService.showOnboardingIfNeeded.mockClear();
            const spy = vi.spyOn(onboardingService, 'showOnboardingIfNeeded').mockResolvedValue(undefined);

            component.ngAfterViewInit();

            expect(spy).toHaveBeenCalledOnce();
        });

        it('should set userAccepted to undefined when user has no LLM selection', async () => {
            accountService.userIdentity.set({ selectedLLMUsage: undefined } as User);

            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.detectChanges();

            expect(component.userAccepted()).toBeUndefined();
        });

        it('should apply prompt starter when onboarding returns promptSelected', async () => {
            const promptKey = 'artemisApp.iris.onboarding.step4.prompts.explainConceptStarter';
            mockOnboardingService.showOnboardingIfNeeded.mockResolvedValue({ action: 'promptSelected', promptKey });

            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.detectChanges();

            component.ngAfterViewInit();
            await fixture.whenStable();
            // Wait for the setTimeout inside applyPromptStarter
            await new Promise((resolve) => setTimeout(resolve, 50));

            expect(component.newMessageTextContent()).toBe(promptKey);

            // Reset mock for other tests
            mockOnboardingService.showOnboardingIfNeeded.mockResolvedValue(undefined);
        });

        it('should not apply prompt starter when onboarding returns finish', async () => {
            mockOnboardingService.showOnboardingIfNeeded.mockResolvedValue({ action: 'finish' });

            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            fixture.nativeElement.querySelector('.chat-body').scrollTo = vi.fn();
            fixture.detectChanges();

            component.ngAfterViewInit();
            await fixture.whenStable();

            expect(component.newMessageTextContent()).toBe('');

            // Reset mock for other tests
            mockOnboardingService.showOnboardingIfNeeded.mockResolvedValue(undefined);
        });

        it('should not apply prompt starter when onboarding returns undefined', async () => {
            mockOnboardingService.showOnboardingIfNeeded.mockResolvedValue(undefined);

            component.ngAfterViewInit();
            await fixture.whenStable();

            expect(component.newMessageTextContent()).toBe('');
        });

        it('should not show onboarding modal when userAccepted is already set', async () => {
            const mockUser = { selectedLLMUsage: LLMSelectionDecision.CLOUD_AI } as User;
            accountService.userIdentity.set(mockUser);
            vi.spyOn(accountService, 'userIdentity').mockReturnValue(mockUser as any);

            mockOnboardingService.showOnboardingIfNeeded.mockClear();

            fixture = TestBed.createComponent(IrisBaseChatbotComponent);
            component = fixture.componentInstance;
            const chatBody = fixture.nativeElement.querySelector('.chat-body');
            if (chatBody) {
                chatBody.scrollTo = vi.fn();
            }
            fixture.detectChanges();

            await fixture.whenStable();

            expect(component.userAccepted()).toBe(LLMSelectionDecision.CLOUD_AI);
        });

        it('should set userAccepted to CLOUD_AI when acceptPermission is called with CLOUD_AI', () => {
            vi.spyOn(chatService, 'updateLLMUsageConsent').mockImplementation(() => {});

            component.acceptPermission(LLMSelectionDecision.CLOUD_AI);

            expect(component.userAccepted()).toBe(LLMSelectionDecision.CLOUD_AI);
            expect(chatService.updateLLMUsageConsent).toHaveBeenCalledWith(LLMSelectionDecision.CLOUD_AI);
        });

        it('should set userAccepted to LOCAL_AI when acceptPermission is called with LOCAL_AI', () => {
            vi.spyOn(chatService, 'updateLLMUsageConsent').mockImplementation(() => {});

            component.acceptPermission(LLMSelectionDecision.LOCAL_AI);

            expect(component.userAccepted()).toBe(LLMSelectionDecision.LOCAL_AI);
            expect(chatService.updateLLMUsageConsent).toHaveBeenCalledWith(LLMSelectionDecision.LOCAL_AI);
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
});
