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
import { IrisStageStateDTO } from 'app/iris/shared/entities/iris-stage-dto.model';
import { IrisLogoComponent } from 'app/iris/overview/iris-logo/iris-logo.component';
import { IrisWebsocketService } from 'app/iris/overview/services/iris-websocket.service';
import { IrisStatusService } from 'app/iris/overview/services/iris-status.service';
import { UserService } from 'app/core/user/shared/user.service';
import dayjs from 'dayjs/esm';
import { provideHttpClient } from '@angular/common/http';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { User } from 'app/core/user/user.model';
import { TranslateService } from '@ngx-translate/core';

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
    let mockDialogClose: ReturnType<typeof vi.fn>;
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
                {
                    provide: TranslateService,
                    useValue: {
                        get: vi.fn().mockReturnValue(of('')),
                        instant: vi.fn((key: string) => key),
                        getCurrentLang: vi.fn().mockReturnValue('en'),
                        onTranslationChange: new Subject(),
                        onLangChange: new Subject(),
                        onDefaultLangChange: new Subject(),
                    },
                },
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

    describe('handleButtonClick', () => {
        it('should close dialog and set chatOpen to false when chat is open', async () => {
            component.openChat();
            expect(component.chatOpen()).toBe(true);

            component.handleButtonClick();

            expect(mockDialogClose).toHaveBeenCalled();
            // chatOpen is reset via afterClosed subscription
            mockDialogAfterClosed.next();
            await fixture.whenStable();
            expect(component.chatOpen()).toBe(false);
        });

        it('should open chat when chat is closed', () => {
            expect(component.chatOpen()).toBe(false);

            component.handleButtonClick();

            expect(component.chatOpen()).toBe(true);
            expect(mockDialog.open).toHaveBeenCalled();
        });
    });

    describe('dialog close handling', () => {
        it('should reset state when dialog is closed externally', async () => {
            component.openChat();
            component.newIrisMessage.set('Some message');
            expect(component.chatOpen()).toBe(true);

            // Simulate dialog closing
            mockDialogAfterClosed.next();
            await fixture.whenStable();

            expect(component.chatOpen()).toBe(false);
            expect(component.newIrisMessage()).toBeUndefined();
        });
    });

    describe('stage display name', () => {
        it('should show rotation label when stage message is empty', async () => {
            chatService.stages.next([{ name: 'Executing pipeline', state: IrisStageStateDTO.IN_PROGRESS, weight: 10, message: '', internal: false }]);
            await fixture.whenStable();

            expect(component.displayName()).toBe('artemisApp.iris.stages.thinking');
            expect(component.isProcessing()).toBe(true);
        });

        it('should show stage message when provided', async () => {
            chatService.stages.next([{ name: 'Executing pipeline', state: IrisStageStateDTO.IN_PROGRESS, weight: 10, message: 'Checking info', internal: false }]);
            await fixture.whenStable();

            expect(component.displayName()).toBe('Checking info');
            expect(component.isProcessing()).toBe(true);
        });

        it('should return empty string when no active stage', async () => {
            chatService.stages.next([{ name: 'Done Stage', state: IrisStageStateDTO.DONE, weight: 10, message: '', internal: false }]);
            await fixture.whenStable();

            expect(component.displayName()).toBe('');
            expect(component.isProcessing()).toBe(false);
        });
    });

    describe('lecture mode', () => {
        it('should subscribe to route.params and call chatService.switchTo with lecture mode', async () => {
            const lectureId = 789;
            vi.spyOn(chatHttpServiceMock, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(lectureId)));
            vi.spyOn(chatHttpServiceMock, 'getChatSessions').mockReturnValue(of([]));
            vi.spyOn(wsServiceMock, 'subscribeToSession').mockReturnValueOnce(of());
            const spy = vi.spyOn(chatService, 'switchTo');

            fixture.componentRef.setInput('mode', ChatServiceMode.LECTURE);
            fixture.changeDetectorRef.detectChanges();

            mockParamsSubject.next({
                lectureId: lectureId,
            });
            await fixture.whenStable();

            expect(spy).toHaveBeenCalledExactlyOnceWith(ChatServiceMode.LECTURE, lectureId);
        });
    });
});
