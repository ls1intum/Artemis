import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { Overlay } from '@angular/cdk/overlay';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { AccountService } from 'app/core/auth/account.service';
import { Subject, of } from 'rxjs';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ActivatedRoute } from '@angular/router';
import { mockServerSessionHttpResponseWithId, mockWebsocketServerMessage } from 'test/helpers/sample/iris-sample-data';
import { IrisExerciseChatbotButtonComponent } from 'app/iris/overview/exercise-chatbot/exercise-chatbot-button.component';
import { IrisChatHttpService } from 'app/iris/overview/services/iris-chat-http.service';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { IrisLogoComponent } from 'app/iris/overview/iris-logo/iris-logo.component';
import { IrisWebsocketService } from 'app/iris/overview/services/iris-websocket.service';
import { IrisStatusService } from 'app/iris/overview/services/iris-status.service';
import { UserService } from 'app/core/user/shared/user.service';
import dayjs from 'dayjs/esm';
import { provideHttpClient } from '@angular/common/http';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { User } from 'app/core/user/user.model';

describe('ExerciseChatbotButtonComponent', () => {
    setupTestBed({ zoneless: true });

    let component: IrisExerciseChatbotButtonComponent;
    let fixture: ComponentFixture<IrisExerciseChatbotButtonComponent>;
    let chatService: IrisChatService;
    let chatHttpServiceMock: IrisChatHttpService;
    let wsServiceMock: IrisWebsocketService;
    let mockDialog: MatDialog;
    let mockOverlay: Overlay;
    let mockActivatedRoute: ActivatedRoute;
    let mockDialogClose: any;
    let mockDialogAfterClosed: Subject<void>;
    let mockParamsSubject: Subject<any>;
    let mockQueryParamsSubject: Subject<any>;
    let accountService: AccountService;

    const statusMock = {
        currentRatelimitInfo: vi.fn().mockReturnValue(of({})),
        handleRateLimitInfo: vi.fn(),
        setCurrentCourse: vi.fn(),
    };
    const accountMock = { selectedLLMUsageTimestamp: dayjs() } as User;

    const mockExerciseId = 123;
    const mockCourseId = 456;

    beforeEach(async () => {
        mockParamsSubject = new Subject();
        mockQueryParamsSubject = new Subject();
        mockActivatedRoute = {
            params: mockParamsSubject,
            queryParams: mockQueryParamsSubject,
        } as unknown as ActivatedRoute;

        mockDialogClose = vi.fn();
        mockDialogAfterClosed = new Subject<void>();

        mockDialog = {
            open: vi.fn().mockReturnValue({
                afterClosed: vi.fn().mockReturnValue(mockDialogAfterClosed.asObservable()),
                close: mockDialogClose,
            }),
            closeAll: vi.fn(),
        } as unknown as MatDialog;

        mockOverlay = {
            scrollStrategies: {
                noop: vi.fn().mockReturnValue({}),
            },
        } as unknown as Overlay;

        await TestBed.configureTestingModule({
            imports: [FontAwesomeModule, MockPipe(HtmlForMarkdownPipe), IrisExerciseChatbotButtonComponent, MockComponent(IrisLogoComponent)],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                IrisChatService,
                MockProvider(IrisChatHttpService),
                MockProvider(IrisWebsocketService),
                { provide: MatDialog, useValue: mockDialog },
                { provide: Overlay, useValue: mockOverlay },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ActivatedRoute, useValue: mockActivatedRoute },
                { provide: IrisStatusService, useValue: statusMock },
                { provide: UserService, useValue: {} },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(IrisExerciseChatbotButtonComponent);
        component = fixture.componentInstance;

        // Set required input BEFORE first detectChanges
        fixture.componentRef.setInput('mode', ChatServiceMode.PROGRAMMING_EXERCISE);

        chatService = TestBed.inject(IrisChatService);
        chatService.setCourseId(mockCourseId);
        chatHttpServiceMock = TestBed.inject(IrisChatHttpService);
        wsServiceMock = TestBed.inject(IrisWebsocketService);
        accountService = TestBed.inject(AccountService);

        accountService.userIdentity.set(accountMock);

        // Emit empty query params initially
        mockQueryParamsSubject.next({});

        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should subscribe to route.params and call chatService.switchTo with exercise mode', async () => {
        vi.spyOn(chatHttpServiceMock, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(mockExerciseId)));
        vi.spyOn(chatHttpServiceMock, 'getChatSessions').mockReturnValue(of([]));
        vi.spyOn(wsServiceMock, 'subscribeToSession').mockReturnValueOnce(of());
        const spy = vi.spyOn(chatService, 'switchTo');

        fixture.componentRef.setInput('mode', ChatServiceMode.PROGRAMMING_EXERCISE);
        fixture.changeDetectorRef.detectChanges();

        mockParamsSubject.next({
            exerciseId: mockExerciseId,
        });

        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();

        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith(ChatServiceMode.PROGRAMMING_EXERCISE, mockExerciseId);
    });

    it('should subscribe to route.params and call chatService.switchTo with text exercise mode', async () => {
        vi.spyOn(chatHttpServiceMock, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(mockExerciseId)));
        vi.spyOn(chatHttpServiceMock, 'getChatSessions').mockReturnValue(of([]));
        vi.spyOn(wsServiceMock, 'subscribeToSession').mockReturnValueOnce(of());
        const spy = vi.spyOn(chatService, 'switchTo');

        fixture.componentRef.setInput('mode', ChatServiceMode.TEXT_EXERCISE);
        fixture.changeDetectorRef.detectChanges();

        mockParamsSubject.next({
            exerciseId: mockExerciseId,
        });

        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();

        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith(ChatServiceMode.TEXT_EXERCISE, mockExerciseId);
    });

    it('should close the dialog when destroying the object', () => {
        // given
        component.openChat();

        // when - destroy the fixture (triggers destroyRef.onDestroy)
        fixture.destroy();

        // then
        expect(mockDialogClose).toHaveBeenCalled();
    });

    it('should not show new message indicator when chatbot is closed', async () => {
        // given
        vi.spyOn(chatHttpServiceMock, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(mockExerciseId)));
        vi.spyOn(chatHttpServiceMock, 'getChatSessions').mockReturnValue(of([]));
        vi.spyOn(wsServiceMock, 'subscribeToSession').mockReturnValueOnce(of(mockWebsocketServerMessage));
        mockParamsSubject.next({
            exerciseId: mockExerciseId,
        });
        chatService.switchTo(ChatServiceMode.PROGRAMMING_EXERCISE, mockExerciseId);

        // when
        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();

        // then
        const unreadIndicatorElement: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('.unread-indicator');
        expect(unreadIndicatorElement).toBeNull();
    });

    it('should not show new message indicator when chatbot is open', async () => {
        // given
        vi.spyOn(chatHttpServiceMock, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(mockExerciseId)));
        vi.spyOn(chatHttpServiceMock, 'getChatSessions').mockReturnValue(of([]));
        vi.spyOn(wsServiceMock, 'subscribeToSession').mockReturnValueOnce(of(mockWebsocketServerMessage));
        mockParamsSubject.next({
            exerciseId: mockExerciseId,
        });
        chatService.switchTo(ChatServiceMode.PROGRAMMING_EXERCISE, mockExerciseId);
        component.openChat();

        // when
        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();

        // then
        const unreadIndicatorElement: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('.unread-indicator');
        expect(unreadIndicatorElement).toBeNull();
    });

    it('should not open the chatbot if no irisQuestion is provided in the queryParams', async () => {
        // given
        const mockQueryParams = {};
        fixture.componentRef.setInput('mode', ChatServiceMode.PROGRAMMING_EXERCISE);

        // when
        mockQueryParamsSubject.next(mockQueryParams);
        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();

        // then - use signal getter
        expect(component.chatOpen()).toBe(false);
    });

    it('should open chat when shouldReopenChat emits true', async () => {
        const openChatSpy = vi.spyOn(component, 'openChat');
        const setShouldReopenChatSpy = vi.spyOn(chatService, 'setShouldReopenChat');

        chatService.setShouldReopenChat(true);
        await fixture.whenStable();

        expect(openChatSpy).toHaveBeenCalledOnce();
        expect(setShouldReopenChatSpy).toHaveBeenCalledWith(false);
    });

    it('should not open chat when shouldReopenChat emits false', async () => {
        const openChatSpy = vi.spyOn(component, 'openChat');

        chatService.setShouldReopenChat(false);
        await fixture.whenStable();

        expect(openChatSpy).not.toHaveBeenCalled();
    });
});
