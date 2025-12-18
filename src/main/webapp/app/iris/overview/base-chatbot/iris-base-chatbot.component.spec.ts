import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { IrisBaseChatbotComponent } from 'app/iris/overview/base-chatbot/iris-base-chatbot.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ChatStatusBarComponent } from 'app/iris/overview/base-chatbot/chat-status-bar/chat-status-bar.component';
import { IrisLogoComponent } from 'app/iris/overview/iris-logo/iris-logo.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { UserService } from 'app/core/user/shared/user.service';
import { IrisStatusService } from 'app/iris/overview/services/iris-status.service';
import { IrisChatHttpService } from 'app/iris/overview/services/iris-chat-http.service';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { IrisWebsocketService } from 'app/iris/overview/services/iris-websocket.service';
import { IrisAssistantMessage, IrisMessage, IrisSender } from 'app/iris/shared/entities/iris-message.model';
import { of } from 'rxjs';
import dayjs from 'dayjs/esm';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { mockClientMessage, mockServerMessage } from 'test/helpers/sample/iris-sample-data';
import { By } from '@angular/platform-browser';
import { IrisErrorMessageKey } from 'app/iris/shared/entities/iris-errors.model';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { IrisSessionDTO } from 'app/iris/shared/entities/iris-session-dto.model';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { User } from 'app/core/user/user.model';
import { LLMSelectionDecision } from 'app/core/user/shared/dto/updateLLMSelectionDecision.dto';

describe('IrisBaseChatbotComponent', () => {
    let component: IrisBaseChatbotComponent;
    let fixture: ComponentFixture<IrisBaseChatbotComponent>;

    let chatService: IrisChatService;
    let accountService: AccountService;

    const statusMock = {
        currentRatelimitInfo: jest.fn().mockReturnValue(of({})),
        handleRateLimitInfo: jest.fn(),
        getActiveStatus: jest.fn().mockReturnValue(of({})),
        setCurrentCourse: jest.fn(),
    } as any;
    const mockUserService = {
        updateLLMSelectionDecision: jest.fn(),
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
            imports: [FontAwesomeModule, RouterModule],
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
                accountService = TestBed.inject(AccountService);
                component = fixture.componentInstance;

                accountService.userIdentity.set({ selectedLLMUsageTimestamp: dayjs() } as User);
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

    it('should set userAccepted to NO_AI when user explicitly chose to reject AI usage', () => {
        accountService.userIdentity.set({ selectedLLMUsage: LLMSelectionDecision.NO_AI } as User);
        component.ngOnInit();
        expect(component.userAccepted).toBe(LLMSelectionDecision.NO_AI);
    });

    it('should call API when user accept the policy', () => {
        const stub = jest.spyOn(mockUserService, 'updateLLMSelectionDecision');
        stub.mockReturnValue(of(new HttpResponse<void>()));

        component.acceptPermission(LLMSelectionDecision.LOCAL_AI);

        expect(stub).toHaveBeenCalledOnce();
    });

    it('should disable enter key if isLoading and active', () => {
        component.active = true;
        component.isLoading = true;
        const event = new KeyboardEvent('keyup', { key: 'Enter', shiftKey: false });
        jest.spyOn(component, 'onSend');

        component.handleKey(event);

        expect(component.onSend).not.toHaveBeenCalled();
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

    it('should not render submit button if userAccepted is NO_AI', () => {
        component.userAccepted = LLMSelectionDecision.NO_AI;
        component.isLoading = false;
        component.error = undefined;
        fixture.changeDetectorRef.detectChanges();
        const sendButton = fixture.debugElement.query(By.css('#irisSendButton'));

        expect(sendButton).toBeNull();
    });

    it('should not disable submit button if isLoading is false and no error exists', () => {
        component.userAccepted = LLMSelectionDecision.CLOUD_AI;
        component.isLoading = false;
        component.error = undefined;
        fixture.changeDetectorRef.detectChanges();
        const sendButton = fixture.debugElement.query(By.css('#irisSendButton')).componentInstance;

        expect(sendButton.disabled).toBeFalsy();
    });

    it('should not disable submit button if isLoading is false and error is not fatal', () => {
        component.userAccepted = LLMSelectionDecision.CLOUD_AI;
        component.isLoading = false;
        component.error = IrisErrorMessageKey.SEND_MESSAGE_FAILED;
        fixture.changeDetectorRef.detectChanges();
        const sendButton = fixture.debugElement.query(By.css('#irisSendButton')).componentInstance;

        expect(sendButton.disabled).toBeFalsy();
    });

    it('should set suggestions correctly', () => {
        const expectedSuggestions = ['suggestion1', 'suggestion2', 'suggestion3'];
        jest.spyOn(chatService, 'currentSuggestions').mockReturnValue(of(expectedSuggestions));

        component.ngOnInit();

        expect(component.suggestions).toEqual(expectedSuggestions);
    });

    it('should not render suggestions when suggestions array is empty', () => {
        // Arrange
        const expectedSuggestions: string[] = [];
        const mockMessages = [mockClientMessage, mockServerMessage];

        jest.spyOn(chatService, 'currentSuggestions').mockReturnValue(of(expectedSuggestions));
        jest.spyOn(chatService, 'currentMessages').mockReturnValue(of(mockMessages));

        // Act
        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();

        // Assert
        const suggestionButtons = fixture.nativeElement.querySelectorAll('.suggestion-button');
        expect(suggestionButtons).toHaveLength(0);
    });

    it('should not render suggestions if userAccepted is NO_AI', () => {
        // Arrange
        const expectedSuggestions = ['suggestion1', 'suggestion2'];
        const mockMessages = [mockClientMessage, mockServerMessage];

        jest.spyOn(chatService, 'currentSuggestions').mockReturnValue(of(expectedSuggestions));
        jest.spyOn(chatService, 'currentMessages').mockReturnValue(of(mockMessages));

        // Act
        component.ngOnInit();
        component.userAccepted = LLMSelectionDecision.NO_AI;
        fixture.changeDetectorRef.detectChanges();

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
        fixture.changeDetectorRef.detectChanges();

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
        fixture.changeDetectorRef.detectChanges();

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
        fixture.changeDetectorRef.detectChanges();

        // Assert
        const suggestionButtons = fixture.nativeElement.querySelectorAll('.suggestion-button');
        expect(suggestionButtons).toHaveLength(0);
    });

    describe('clear chat session', () => {
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
            fixture.changeDetectorRef.detectChanges();
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
    describe('showAISelectionModal', () => {
        it('should handle cloud choice from modal', async () => {
            const acceptPermissionSpy = jest.spyOn(component, 'acceptPermission');
            const updateConsentSpy = jest.spyOn(chatService, 'updateLLMUsageConsent');

            jest.spyOn(component['llmModalService'], 'open').mockResolvedValue('cloud');

            await component.showAISelectionModal();

            expect(acceptPermissionSpy).toHaveBeenCalledWith(LLMSelectionDecision.CLOUD_AI);
            expect(updateConsentSpy).toHaveBeenCalledWith(LLMSelectionDecision.CLOUD_AI);
        });

        it('should handle local choice from modal', async () => {
            const acceptPermissionSpy = jest.spyOn(component, 'acceptPermission');
            const updateConsentSpy = jest.spyOn(chatService, 'updateLLMUsageConsent');

            jest.spyOn(component['llmModalService'], 'open').mockResolvedValue('local');

            await component.showAISelectionModal();

            expect(acceptPermissionSpy).toHaveBeenCalledWith(LLMSelectionDecision.LOCAL_AI);
            expect(updateConsentSpy).toHaveBeenCalledWith(LLMSelectionDecision.LOCAL_AI);
        });

        it('should handle no_ai choice from modal', async () => {
            const updateConsentSpy = jest.spyOn(chatService, 'updateLLMUsageConsent');
            const closeChatSpy = jest.spyOn(component, 'closeChat');

            jest.spyOn(component['llmModalService'], 'open').mockResolvedValue('no_ai');

            await component.showAISelectionModal();

            expect(updateConsentSpy).toHaveBeenCalledWith(LLMSelectionDecision.NO_AI);
            expect(closeChatSpy).toHaveBeenCalledOnce();
        });

        it('should handle none choice from modal', async () => {
            const closeChatSpy = jest.spyOn(component, 'closeChat');

            jest.spyOn(component['llmModalService'], 'open').mockResolvedValue('none');

            await component.showAISelectionModal();

            expect(closeChatSpy).toHaveBeenCalledOnce();
        });

        it('should call showAISelectionModal when user has not accepted', async () => {
            accountService.userIdentity.set({ selectedLLMUsage: undefined } as User);
            const showModalSpy = jest.spyOn(component, 'showAISelectionModal').mockResolvedValue();

            component.ngOnInit();

            expect(showModalSpy).toHaveBeenCalledOnce();
        });
    });

    describe('checkIfUserAcceptedLLMUsage', () => {
        it('should set userAccepted from accountService', () => {
            accountService.userIdentity.set({ selectedLLMUsage: LLMSelectionDecision.LOCAL_AI } as User);

            component.checkIfUserAcceptedLLMUsage();

            expect(component.userAccepted).toBe(LLMSelectionDecision.LOCAL_AI);
        });

        it('should set userAccepted to undefined when user has no selection', () => {
            accountService.userIdentity.set({ selectedLLMUsage: undefined } as User);

            component.checkIfUserAcceptedLLMUsage();

            expect(component.userAccepted).toBeUndefined();
        });
    });

    describe('onSuggestionClick', () => {
        it('should set newMessageTextContent to suggestion', () => {
            const suggestion = 'This is a test suggestion';
            jest.spyOn(component, 'onSend').mockImplementation();

            component.onSuggestionClick(suggestion);

            expect(component.newMessageTextContent).toBe(suggestion);
        });

        it('should call onSend after setting suggestion', () => {
            const suggestion = 'Another suggestion';
            const onSendSpy = jest.spyOn(component, 'onSend').mockImplementation();

            component.onSuggestionClick(suggestion);

            expect(onSendSpy).toHaveBeenCalledOnce();
        });
    });

    describe('closeChat', () => {
        it('should call chatService.messagesRead', () => {
            const readSpy = jest.spyOn(chatService, 'messagesRead');

            component.closeChat();

            expect(readSpy).toHaveBeenCalledOnce();
        });

        it('should emit closeClicked event', () => {
            const emitSpy = jest.spyOn(component.closeClicked, 'emit');

            component.closeChat();

            expect(emitSpy).toHaveBeenCalledOnce();
        });
    });

    describe('onModelChange', () => {
        beforeEach(fakeAsync(() => {
            component.userAccepted = LLMSelectionDecision.CLOUD_AI;
            fixture.changeDetectorRef.detectChanges();
            tick();
            fixture.changeDetectorRef.detectChanges();
        }));

        it('should update rows when newRows is less than current rows', () => {
            component.rows = 3;
            component.messageTextarea.nativeElement.value = 'Line 1\nLine 2';
            const adjustSpy = jest.spyOn(component, 'adjustScrollButtonPosition');

            component.onModelChange();

            expect(component.rows).toBe(2);
            expect(adjustSpy).toHaveBeenCalledWith(2);
        });

        it('should update rows when newRows is greater than current rows', () => {
            component.rows = 1;
            component.messageTextarea.nativeElement.value = 'Line 1\nLine 2\nLine 3';
            const adjustSpy = jest.spyOn(component, 'adjustScrollButtonPosition');

            component.onModelChange();

            expect(component.rows).toBe(3);
            expect(adjustSpy).toHaveBeenCalledWith(3);
        });

        it('should not update rows when newRows equals current rows', () => {
            component.rows = 2;
            component.messageTextarea.nativeElement.value = 'Line 1\nLine 2';
            const adjustSpy = jest.spyOn(component, 'adjustScrollButtonPosition');

            component.onModelChange();

            expect(adjustSpy).not.toHaveBeenCalled();
        });

        it('should not update rows when newRows exceeds 3', () => {
            component.rows = 3;
            component.messageTextarea.nativeElement.value = 'Line 1\nLine 2\nLine 3\nLine 4';
            const adjustSpy = jest.spyOn(component, 'adjustScrollButtonPosition');

            component.onModelChange();

            expect(component.rows).toBe(3);
            expect(adjustSpy).not.toHaveBeenCalled();
        });

        it('should set rows to 1 when textarea has single line', () => {
            component.rows = 2;
            component.messageTextarea.nativeElement.value = 'Single line';
            const adjustSpy = jest.spyOn(component, 'adjustScrollButtonPosition');

            component.onModelChange();

            expect(component.rows).toBe(1);
            expect(adjustSpy).toHaveBeenCalledWith(1);
        });

        it('should cap rows at 3 when textarea has more than 3 lines', () => {
            component.rows = 2;
            component.messageTextarea.nativeElement.value = 'Line 1\nLine 2\nLine 3\nLine 4\nLine 5';

            component.onModelChange();

            expect(component.rows).toBe(2); // Should not update beyond current when > 3
        });
    });

    describe('checkChatScroll', () => {
        beforeEach(() => {
            component.userAccepted = LLMSelectionDecision.CLOUD_AI;

            const mockMessages = [mockClientMessage, mockServerMessage];
            jest.spyOn(chatService, 'currentMessages').mockReturnValue(of(mockMessages));

            component.ngOnInit();
            fixture.changeDetectorRef.detectChanges();
        });

        it('should set isScrolledToBottom to true when scrollTop is 0', () => {
            const messagesElement = component.messagesElement.nativeElement;
            Object.defineProperty(messagesElement, 'scrollTop', { value: 0, writable: true, configurable: true });

            component.checkChatScroll();

            expect(component.isScrolledToBottom).toBeTrue();
        });

        it('should set isScrolledToBottom to true when scrollTop is less than 50', () => {
            const messagesElement = component.messagesElement.nativeElement;
            Object.defineProperty(messagesElement, 'scrollTop', { value: 30, writable: true, configurable: true });

            component.checkChatScroll();

            expect(component.isScrolledToBottom).toBeTrue();
        });

        it('should set isScrolledToBottom to true when scrollTop is exactly 49', () => {
            const messagesElement = component.messagesElement.nativeElement;
            Object.defineProperty(messagesElement, 'scrollTop', { value: 49, writable: true, configurable: true });

            component.checkChatScroll();

            expect(component.isScrolledToBottom).toBeTrue();
        });

        it('should set isScrolledToBottom to false when scrollTop is exactly 50', () => {
            const messagesElement = component.messagesElement.nativeElement;
            Object.defineProperty(messagesElement, 'scrollTop', { value: 50, writable: true, configurable: true });

            component.checkChatScroll();

            expect(component.isScrolledToBottom).toBeFalse();
        });

        it('should set isScrolledToBottom to false when scrollTop is greater than 50', () => {
            const messagesElement = component.messagesElement.nativeElement;
            Object.defineProperty(messagesElement, 'scrollTop', { value: 100, writable: true, configurable: true });

            component.checkChatScroll();

            expect(component.isScrolledToBottom).toBeFalse();
        });
    });

    describe('computeRelatedEntityRoute', () => {
        it('should return undefined when chatMode is undefined', () => {
            const result = component['computeRelatedEntityRoute'](undefined, 123);
            expect(result).toBeUndefined();
        });

        it('should return undefined when relatedEntityId is undefined', () => {
            const result = component['computeRelatedEntityRoute'](ChatServiceMode.PROGRAMMING_EXERCISE, undefined);
            expect(result).toBeUndefined();
        });

        it('should return undefined when both chatMode and relatedEntityId are undefined', () => {
            const result = component['computeRelatedEntityRoute'](undefined, undefined);
            expect(result).toBeUndefined();
        });

        it('should return correct route for PROGRAMMING_EXERCISE', () => {
            const result = component['computeRelatedEntityRoute'](ChatServiceMode.PROGRAMMING_EXERCISE, 456);
            expect(result).toBe('../exercises/456');
        });

        it('should return correct route for LECTURE', () => {
            const result = component['computeRelatedEntityRoute'](ChatServiceMode.LECTURE, 789);
            expect(result).toBe('../lectures/789');
        });

        it('should return undefined for COURSE mode', () => {
            const result = component['computeRelatedEntityRoute'](ChatServiceMode.COURSE, 123);
            expect(result).toBeUndefined();
        });

        it('should return undefined for unknown chat mode', () => {
            const result = component['computeRelatedEntityRoute']('UNKNOWN_MODE' as any, 123);
            expect(result).toBeUndefined();
        });
    });

    describe('computeRelatedEntityLinkButtonLabel', () => {
        it('should return undefined when chatMode is undefined', () => {
            const result = component['computeRelatedEntityLinkButtonLabel'](undefined);
            expect(result).toBeUndefined();
        });

        it('should return correct label for PROGRAMMING_EXERCISE', () => {
            const result = component['computeRelatedEntityLinkButtonLabel'](ChatServiceMode.PROGRAMMING_EXERCISE);
            expect(result).toBe('artemisApp.exerciseChatbot.goToRelatedEntityButton.exerciseLabel');
        });

        it('should return correct label for LECTURE', () => {
            const result = component['computeRelatedEntityLinkButtonLabel'](ChatServiceMode.LECTURE);
            expect(result).toBe('artemisApp.exerciseChatbot.goToRelatedEntityButton.lectureLabel');
        });

        it('should return undefined for COURSE mode', () => {
            const result = component['computeRelatedEntityLinkButtonLabel'](ChatServiceMode.COURSE);
            expect(result).toBeUndefined();
        });

        it('should return undefined for unknown chat mode', () => {
            const result = component['computeRelatedEntityLinkButtonLabel']('UNKNOWN_MODE' as any);
            expect(result).toBeUndefined();
        });
    });

    describe('resetChatBodyHeight', () => {
        beforeEach(() => {
            component.userAccepted = LLMSelectionDecision.CLOUD_AI;
            fixture.changeDetectorRef.detectChanges();
        });

        it('should reset textarea rows to 1', () => {
            const textarea = component.messageTextarea.nativeElement;
            textarea.rows = 3;

            component.resetChatBodyHeight();

            expect(textarea.rows).toBe(1);
        });

        it('should reset textarea height style to empty string', () => {
            const textarea = component.messageTextarea.nativeElement;
            textarea.style.height = '100px';

            component.resetChatBodyHeight();

            expect(textarea.style.height).toBe('');
        });

        it('should reset scrollArrow bottom style to empty string', () => {
            const scrollArrow = component.scrollArrow.nativeElement;
            scrollArrow.style.bottom = '50px';

            component.resetChatBodyHeight();

            expect(scrollArrow.style.bottom).toBe('');
        });

        it('should reset all properties at once', () => {
            const textarea = component.messageTextarea.nativeElement;
            const scrollArrow = component.scrollArrow.nativeElement;
            textarea.rows = 5;
            textarea.style.height = '200px';
            scrollArrow.style.bottom = '100px';

            component.resetChatBodyHeight();

            expect(textarea.rows).toBe(1);
            expect(textarea.style.height).toBe('');
            expect(scrollArrow.style.bottom).toBe('');
        });
    });

    describe('adjustTextareaRows', () => {
        beforeEach(() => {
            component.userAccepted = LLMSelectionDecision.CLOUD_AI;
            fixture.changeDetectorRef.detectChanges();
        });

        it('should set textarea height to auto initially', () => {
            const textarea = component.messageTextarea.nativeElement;
            const adjustSpy = jest.spyOn(component, 'adjustScrollButtonPosition');

            component.adjustTextareaRows();

            expect(textarea.style.height).toContain('auto');
            expect(adjustSpy).toHaveBeenCalled();
        });

        it('should calculate height based on scrollHeight when less than maxHeight', () => {
            const textarea = component.messageTextarea.nativeElement;
            Object.defineProperty(textarea, 'scrollHeight', { value: 50, writable: true, configurable: true });
            jest.spyOn(window, 'getComputedStyle').mockReturnValue({ lineHeight: '20px' } as CSSStyleDeclaration);

            component.adjustTextareaRows();

            expect(textarea.style.height).toBe('50px');
        });

        it('should cap height at maxHeight when scrollHeight exceeds it', () => {
            const textarea = component.messageTextarea.nativeElement;
            Object.defineProperty(textarea, 'scrollHeight', { value: 500, writable: true, configurable: true });
            jest.spyOn(window, 'getComputedStyle').mockReturnValue({ lineHeight: '20px' } as CSSStyleDeclaration);

            component.adjustTextareaRows();

            // maxHeight = (20 + 4) * 3 = 72px
            expect(textarea.style.height).toBe('72px');
        });

        it('should call adjustScrollButtonPosition with correct value', () => {
            const textarea = component.messageTextarea.nativeElement;
            Object.defineProperty(textarea, 'scrollHeight', { value: 60, writable: true, configurable: true });
            jest.spyOn(window, 'getComputedStyle').mockReturnValue({ lineHeight: '20px' } as CSSStyleDeclaration);
            const adjustSpy = jest.spyOn(component, 'adjustScrollButtonPosition');

            component.adjustTextareaRows();

            // 60 / (20 + 4) = 2.5
            expect(adjustSpy).toHaveBeenCalledWith(2.5);
        });

        it('should call adjustScrollButtonPosition with maxHeight ratio when exceeded', () => {
            const textarea = component.messageTextarea.nativeElement;
            Object.defineProperty(textarea, 'scrollHeight', { value: 500, writable: true, configurable: true });
            jest.spyOn(window, 'getComputedStyle').mockReturnValue({ lineHeight: '20px' } as CSSStyleDeclaration);
            const adjustSpy = jest.spyOn(component, 'adjustScrollButtonPosition');

            component.adjustTextareaRows();

            // maxHeight = 72, lineHeight = 24, ratio = 72/24 = 3
            expect(adjustSpy).toHaveBeenCalledWith(3);
        });
    });

    describe('rateMessage', () => {
        beforeEach(() => {
            component.userAccepted = LLMSelectionDecision.CLOUD_AI;
            fixture.changeDetectorRef.detectChanges();
        });

        it('should not rate if message sender is not LLM', () => {
            const userMessage = { ...mockClientMessage, sender: IrisSender.USER } as IrisMessage;
            const rateSpy = jest.spyOn(chatService, 'rateMessage');

            component.rateMessage(userMessage, true);

            expect(rateSpy).not.toHaveBeenCalled();
        });

        it('should set message.helpful to true when rated helpful', () => {
            const llmMessage = { ...mockServerMessage, sender: IrisSender.LLM, helpful: undefined } as IrisAssistantMessage;
            jest.spyOn(chatService, 'rateMessage').mockReturnValue(of(undefined as any));

            component.rateMessage(llmMessage, true);

            expect(llmMessage.helpful).toBeTrue();
        });

        it('should set message.helpful to false when rated unhelpful', () => {
            const llmMessage = { ...mockServerMessage, sender: IrisSender.LLM, helpful: undefined } as IrisAssistantMessage;
            jest.spyOn(chatService, 'rateMessage').mockReturnValue(of(undefined as any));

            component.rateMessage(llmMessage, false);

            expect(llmMessage.helpful).toBeFalse();
        });

        it('should set message.helpful to false when rating is undefined', () => {
            const llmMessage = { ...mockServerMessage, sender: IrisSender.LLM, helpful: undefined } as IrisAssistantMessage;
            jest.spyOn(chatService, 'rateMessage').mockReturnValue(of(undefined as any));

            component.rateMessage(llmMessage, undefined);

            expect(llmMessage.helpful).toBeFalse();
        });

        it('should call chatService.rateMessage with message and rating', () => {
            const llmMessage = { ...mockServerMessage, sender: IrisSender.LLM } as IrisMessage;
            const rateSpy = jest.spyOn(chatService, 'rateMessage').mockReturnValue(of(undefined as any));

            component.rateMessage(llmMessage, true);

            expect(rateSpy).toHaveBeenCalledWith(llmMessage, true);
        });

        it('should subscribe to rateMessage observable', () => {
            const llmMessage = { ...mockServerMessage, sender: IrisSender.LLM } as IrisMessage;
            const mockObservable = of(undefined as any);
            const subscribeSpy = jest.spyOn(mockObservable, 'subscribe');
            jest.spyOn(chatService, 'rateMessage').mockReturnValue(mockObservable);

            component.rateMessage(llmMessage, false);

            expect(subscribeSpy).toHaveBeenCalled();
        });
    });

    describe('onSend', () => {
        beforeEach(() => {
            component.userAccepted = LLMSelectionDecision.CLOUD_AI;
            fixture.changeDetectorRef.detectChanges();
        });

        it('should call messagesRead', () => {
            const messagesReadSpy = jest.spyOn(chatService, 'messagesRead');
            component.newMessageTextContent = 'Test message';
            jest.spyOn(chatService, 'sendMessage').mockReturnValue(of(undefined as any));

            component.onSend();

            expect(messagesReadSpy).toHaveBeenCalled();
        });

        it('should set isLoading to false when sending message', () => {
            component.newMessageTextContent = 'Test message';
            jest.spyOn(chatService, 'sendMessage').mockReturnValue(of(undefined as any));

            component.onSend();

            expect(component.isLoading).toBeFalse();
        });

        it('should call sendMessage when newMessageTextContent is not empty', () => {
            const testMessage = 'Test message content';
            component.newMessageTextContent = testMessage;
            const sendSpy = jest.spyOn(chatService, 'sendMessage').mockReturnValue(of(undefined as any));

            component.onSend();

            expect(sendSpy).toHaveBeenCalledWith(testMessage);
        });

        it('should not call sendMessage when newMessageTextContent is empty', () => {
            component.newMessageTextContent = '';
            const sendSpy = jest.spyOn(chatService, 'sendMessage');

            component.onSend();

            expect(sendSpy).not.toHaveBeenCalled();
        });

        it('should clear newMessageTextContent after sending', () => {
            component.newMessageTextContent = 'Test message';
            jest.spyOn(chatService, 'sendMessage').mockReturnValue(of(undefined as any));

            component.onSend();

            expect(component.newMessageTextContent).toBe('');
        });

        it('should set isLoading to false after message is sent', fakeAsync(() => {
            component.newMessageTextContent = 'Test message';
            jest.spyOn(chatService, 'sendMessage').mockReturnValue(of(undefined as any));

            component.onSend();
            tick();

            expect(component.isLoading).toBeFalse();
        }));

        it('should call resetChatBodyHeight', () => {
            const resetSpy = jest.spyOn(component, 'resetChatBodyHeight');
            component.newMessageTextContent = '';

            component.onSend();

            expect(resetSpy).toHaveBeenCalled();
        });

        it('should call resetChatBodyHeight even when message is empty', () => {
            const resetSpy = jest.spyOn(component, 'resetChatBodyHeight');
            component.newMessageTextContent = '';

            component.onSend();

            expect(resetSpy).toHaveBeenCalled();
        });

        it('should handle complete flow: read, send, clear, reset', fakeAsync(() => {
            const messagesReadSpy = jest.spyOn(chatService, 'messagesRead');
            const sendSpy = jest.spyOn(chatService, 'sendMessage').mockReturnValue(of(undefined as any));
            const resetSpy = jest.spyOn(component, 'resetChatBodyHeight');
            component.newMessageTextContent = 'Complete test';

            component.onSend();
            tick();

            expect(messagesReadSpy).toHaveBeenCalled();
            expect(sendSpy).toHaveBeenCalledWith('Complete test');
            expect(component.newMessageTextContent).toBe('');
            expect(component.isLoading).toBeFalse();
            expect(resetSpy).toHaveBeenCalled();
        }));
    });

    describe('resendMessage', () => {
        beforeEach(() => {
            component.userAccepted = LLMSelectionDecision.CLOUD_AI;
            fixture.changeDetectorRef.detectChanges();
        });

        it('should not resend if message sender is not USER', () => {
            const llmMessage = { ...mockServerMessage, sender: IrisSender.LLM } as IrisMessage;
            const resendSpy = jest.spyOn(chatService, 'resendMessage');
            const sendSpy = jest.spyOn(chatService, 'sendMessage');

            component.resendMessage(llmMessage);

            expect(resendSpy).not.toHaveBeenCalled();
            expect(sendSpy).not.toHaveBeenCalled();
        });

        it('should set resendAnimationActive to false after resending with id', () => {
            const userMessage = { ...mockClientMessage, id: 123, sender: IrisSender.USER } as IrisMessage;
            jest.spyOn(chatService, 'resendMessage').mockReturnValue(of(userMessage as any));

            component.resendMessage(userMessage);

            expect(component.resendAnimationActive).toBeFalse();
        });

        it('should call resendMessage when message has id', () => {
            const userMessage = { ...mockClientMessage, id: 123, sender: IrisSender.USER } as IrisMessage;
            const resendSpy = jest.spyOn(chatService, 'resendMessage').mockReturnValue(of(userMessage as any));

            component.resendMessage(userMessage);

            expect(resendSpy).toHaveBeenCalledWith(userMessage);
        });

        it('should set isLoading to false after resending message with id', () => {
            const userMessage = { ...mockClientMessage, id: 123, sender: IrisSender.USER } as IrisMessage;
            jest.spyOn(chatService, 'resendMessage').mockReturnValue(of(userMessage as any));

            component.resendMessage(userMessage);

            expect(component.isLoading).toBeFalse();
        });

        it('should call sendMessage when message has no id but has content', () => {
            const userMessage = {
                ...mockClientMessage,
                id: undefined,
                sender: IrisSender.USER,
                content: [{ textContent: 'Test message', type: 'text' } as any],
            } as IrisMessage;
            const sendSpy = jest.spyOn(chatService, 'sendMessage').mockReturnValue(of(undefined as any));

            component.resendMessage(userMessage);

            expect(sendSpy).toHaveBeenCalledWith('Test message');
        });

        it('should set isLoading to false after sending message without id', () => {
            const userMessage = {
                ...mockClientMessage,
                id: undefined,
                sender: IrisSender.USER,
                content: [{ textContent: 'Test message', type: 'text' } as any],
            } as IrisMessage;
            jest.spyOn(chatService, 'sendMessage').mockReturnValue(of(undefined as any));

            component.resendMessage(userMessage);

            expect(component.isLoading).toBeFalse();
        });

        it('should set resendAnimationActive to false and return when no id and no content', () => {
            const userMessage = {
                ...mockClientMessage,
                id: undefined,
                sender: IrisSender.USER,
                content: [],
            } as IrisMessage;
            const sendSpy = jest.spyOn(chatService, 'sendMessage');
            const resendSpy = jest.spyOn(chatService, 'resendMessage');

            component.resendMessage(userMessage);

            expect(component.resendAnimationActive).toBeFalse();
            expect(sendSpy).not.toHaveBeenCalled();
            expect(resendSpy).not.toHaveBeenCalled();
        });

        it('should return early when message has no id and content has no textContent', () => {
            const userMessage = {
                ...mockClientMessage,
                id: undefined,
                sender: IrisSender.USER,
                content: [{ type: 'text' } as any],
            } as IrisMessage;
            const sendSpy = jest.spyOn(chatService, 'sendMessage');

            component.resendMessage(userMessage);

            expect(component.resendAnimationActive).toBeFalse();
            expect(sendSpy).not.toHaveBeenCalled();
        });

        it('should set resendAnimationActive to false after resend completes', fakeAsync(() => {
            const userMessage = { ...mockClientMessage, id: 123, sender: IrisSender.USER } as IrisMessage;
            jest.spyOn(chatService, 'resendMessage').mockReturnValue(of(userMessage as any));

            component.resendMessage(userMessage);
            tick();

            expect(component.resendAnimationActive).toBeFalse();
        }));

        it('should set isLoading to false after resend completes', fakeAsync(() => {
            const userMessage = { ...mockClientMessage, id: 123, sender: IrisSender.USER } as IrisMessage;
            jest.spyOn(chatService, 'resendMessage').mockReturnValue(of(userMessage as any));

            component.resendMessage(userMessage);
            tick();

            expect(component.isLoading).toBeFalse();
        }));

        it('should call messagesRead after resend completes', fakeAsync(() => {
            const userMessage = { ...mockClientMessage, id: 123, sender: IrisSender.USER } as IrisMessage;
            jest.spyOn(chatService, 'resendMessage').mockReturnValue(of(userMessage as any));
            const messagesReadSpy = jest.spyOn(chatService, 'messagesRead');

            component.resendMessage(userMessage);
            tick();

            expect(messagesReadSpy).toHaveBeenCalled();
        }));

        it('should set resendAnimationActive to false after send completes for message without id', fakeAsync(() => {
            const userMessage = {
                ...mockClientMessage,
                id: undefined,
                sender: IrisSender.USER,
                content: [{ textContent: 'Test', type: 'text' } as any],
            } as IrisMessage;
            jest.spyOn(chatService, 'sendMessage').mockReturnValue(of(undefined as any));

            component.resendMessage(userMessage);
            tick();

            expect(component.resendAnimationActive).toBeFalse();
        }));

        it('should set isLoading to false after send completes for message without id', fakeAsync(() => {
            const userMessage = {
                ...mockClientMessage,
                id: undefined,
                sender: IrisSender.USER,
                content: [{ textContent: 'Test', type: 'text' } as any],
            } as IrisMessage;
            jest.spyOn(chatService, 'sendMessage').mockReturnValue(of(undefined as any));

            component.resendMessage(userMessage);
            tick();

            expect(component.isLoading).toBeFalse();
        }));
    });

    describe('onInput', () => {
        beforeEach(() => {
            component.userAccepted = LLMSelectionDecision.CLOUD_AI;
            fixture.changeDetectorRef.detectChanges();
        });

        it('should call adjustTextareaRows', () => {
            const adjustSpy = jest.spyOn(component, 'adjustTextareaRows');

            component.onInput();

            expect(adjustSpy).toHaveBeenCalledOnce();
        });
    });

    describe('onPaste', () => {
        beforeEach(() => {
            component.userAccepted = LLMSelectionDecision.CLOUD_AI;
            fixture.changeDetectorRef.detectChanges();
        });

        it('should call adjustTextareaRows after timeout', fakeAsync(() => {
            const adjustSpy = jest.spyOn(component, 'adjustTextareaRows');

            component.onPaste();
            tick(0);

            expect(adjustSpy).toHaveBeenCalledOnce();
        }));

        it('should delay adjustTextareaRows call', fakeAsync(() => {
            const adjustSpy = jest.spyOn(component, 'adjustTextareaRows');

            component.onPaste();

            expect(adjustSpy).not.toHaveBeenCalled();

            tick(0);

            expect(adjustSpy).toHaveBeenCalled();
        }));
    });

    describe('scrollToBottom', () => {
        beforeEach(() => {
            component.userAccepted = LLMSelectionDecision.CLOUD_AI;

            const mockMessages = [mockClientMessage, mockServerMessage];
            jest.spyOn(chatService, 'currentMessages').mockReturnValue(of(mockMessages));

            component.ngOnInit();
            fixture.changeDetectorRef.detectChanges();
        });

        it('should call scrollTo with smooth behavior', fakeAsync(() => {
            const scrollToSpy = jest.spyOn(component.messagesElement.nativeElement, 'scrollTo');

            component.scrollToBottom('smooth');
            tick();

            expect(scrollToSpy).toHaveBeenCalledWith({
                top: 0,
                behavior: 'smooth',
            });
        }));

        it('should call scrollTo with auto behavior', fakeAsync(() => {
            const scrollToSpy = jest.spyOn(component.messagesElement.nativeElement, 'scrollTo');

            component.scrollToBottom('auto');
            tick();

            expect(scrollToSpy).toHaveBeenCalledWith({
                top: 0,
                behavior: 'auto',
            });
        }));

        it('should handle null messagesElement gracefully', fakeAsync(() => {
            component.messagesElement = null as any;

            expect(() => {
                component.scrollToBottom('smooth');
                tick();
            }).not.toThrow();
        }));

        it('should scroll to top position 0', fakeAsync(() => {
            const scrollToSpy = jest.spyOn(component.messagesElement.nativeElement, 'scrollTo');

            component.scrollToBottom('auto');
            tick();

            expect(scrollToSpy).toHaveBeenCalledWith(
                expect.objectContaining({
                    top: 0,
                }),
            );
        }));
    });

    describe('onClearSession', () => {
        let modalRef: any;
        let modalService: NgbModal;

        beforeEach(() => {
            component.userAccepted = LLMSelectionDecision.CLOUD_AI;
            fixture.changeDetectorRef.detectChanges();

            modalService = TestBed.inject(NgbModal);

            modalRef = {
                result: Promise.resolve('confirm'),
            };
        });

        it('should open modal with provided content', () => {
            const openSpy = jest.spyOn(modalService, 'open').mockReturnValue(modalRef);
            const content = { template: 'test' };

            component.onClearSession(content);

            expect(openSpy).toHaveBeenCalledWith(content);
        });

        it('should not call clearChat when result is not confirm', fakeAsync(() => {
            modalRef.result = Promise.resolve('cancel');
            jest.spyOn(modalService, 'open').mockReturnValue(modalRef);
            const clearChatSpy = jest.spyOn(chatService, 'clearChat');

            component.onClearSession({});
            tick();

            expect(clearChatSpy).not.toHaveBeenCalled();
        }));

        it('should not set isLoading to false when result is not confirm', fakeAsync(() => {
            modalRef.result = Promise.resolve('cancel');
            jest.spyOn(modalService, 'open').mockReturnValue(modalRef);
            component.isLoading = true;

            component.onClearSession({});
            tick();

            expect(component.isLoading).toBeTrue();
        }));
    });
});
