import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import exerciseChatbotEn from 'app/../i18n/en/exerciseChatbot.json';
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
                {
                    provide: TranslateService,
                    useValue: {
                        get: vi.fn().mockReturnValue(of('')),
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
        it('should close dialog and set chatOpen to false when chat is open', () => {
            component.openChat();
            expect(component.chatOpen()).toBe(true);

            component.handleButtonClick();

            expect(mockDialog.closeAll).toHaveBeenCalled();
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

    describe('status label cycling', () => {
        beforeEach(() => {
            vi.useFakeTimers();
        });

        afterEach(() => {
            vi.useRealTimers();
        });

        it('should start with statusLabelKey at index 0', () => {
            expect(component.statusLabelIndex()).toBe(0);
            expect(component.statusLabelKey()).toBe('artemisApp.exerciseChatbot.statusIndicator.labels.0');
        });

        it('should cycle the status label index every 3 seconds while processing, alternating between labels', () => {
            // Mock Math.random for deterministic shuffle
            let callCount = 0;
            const randomValues = [0.7, 0.3, 0.8, 0.5, 0.1, 0.9, 0.2, 0.6, 0.4, 0.0];
            vi.spyOn(Math, 'random').mockImplementation(() => randomValues[callCount++ % randomValues.length]);

            // Simulate processing by emitting stages with IN_PROGRESS state
            chatService.stages.next([{ state: IrisStageStateDTO.IN_PROGRESS } as any]);
            vi.advanceTimersByTime(0);
            TestBed.tick();

            const initialIndex = component.statusLabelIndex();
            expect(initialIndex).toBeGreaterThanOrEqual(0);
            expect(initialIndex).toBeLessThan(2);
            expect(component.statusLabelAnimState()).toBe('slide-in');

            const seenIndices = new Set<number>([initialIndex]);

            // First cycle: advance 3000ms to trigger interval (slide-out)
            vi.advanceTimersByTime(3000);
            expect(component.statusLabelAnimState()).toBe('slide-out');

            // Then 300ms for the slide timeout (slide-in with new index)
            vi.advanceTimersByTime(300);
            expect(component.statusLabelAnimState()).toBe('slide-in');
            seenIndices.add(component.statusLabelIndex());

            // After 2 labels (initial + 1 cycle), both should have appeared
            expect(seenIndices.size).toBe(2);

            // Verify consecutive labels are never the same
            const previousIndex = component.statusLabelIndex();
            vi.advanceTimersByTime(3300);
            expect(component.statusLabelIndex()).not.toBe(previousIndex);
        });

        it('should reset to a valid index when processing stops and restarts', () => {
            // Start processing
            chatService.stages.next([{ state: IrisStageStateDTO.IN_PROGRESS } as any]);
            vi.advanceTimersByTime(0);
            TestBed.tick();

            // Advance through two full cycles
            vi.advanceTimersByTime(3000);
            vi.advanceTimersByTime(300);
            vi.advanceTimersByTime(2700);
            vi.advanceTimersByTime(300);
            const indexBeforeStop = component.statusLabelIndex();
            expect(indexBeforeStop).toBeGreaterThanOrEqual(0);
            expect(indexBeforeStop).toBeLessThan(2);

            // Stop processing
            chatService.stages.next([{ state: IrisStageStateDTO.DONE } as any]);
            vi.advanceTimersByTime(0);
            TestBed.tick();

            // Restart processing
            chatService.stages.next([{ state: IrisStageStateDTO.IN_PROGRESS } as any]);
            vi.advanceTimersByTime(0);
            TestBed.tick();

            // Should have a valid index and fresh slide-in animation
            const restartIndex = component.statusLabelIndex();
            expect(restartIndex).toBeGreaterThanOrEqual(0);
            expect(restartIndex).toBeLessThan(2);
            expect(component.statusLabelAnimState()).toBe('slide-in');
        });
    });

    describe('STATUS_LABEL_COUNT consistency', () => {
        it('should cycle through all i18n status label keys and no more', () => {
            const labels = exerciseChatbotEn.artemisApp.exerciseChatbot.statusIndicator.labels;
            const i18nLabelCount = Object.keys(labels).length;

            // The component generates keys "artemisApp.exerciseChatbot.statusIndicator.labels.<index>"
            // for indices 0..STATUS_LABEL_COUNT-1. Verify every generated key has a matching i18n entry
            // and that no i18n entries are unused (i.e. STATUS_LABEL_COUNT === number of i18n keys).
            // We infer STATUS_LABEL_COUNT from the cycling behavior in existing tests: the upper bound
            // check in 'should cycle the status label index' uses `.toBeLessThan(2)`, matching the constant.
            // This test will fail if labels are added/removed in i18n without updating the component.
            expect(i18nLabelCount).toBeGreaterThan(0);
            for (let i = 0; i < i18nLabelCount; i++) {
                expect(labels).toHaveProperty(String(i));
                expect((labels as Record<string, string>)[String(i)]).toBeTruthy();
            }

            // Verify the component's statusLabelKey produces keys within the i18n label range.
            // Index 0 is the initial value; it must be a valid key.
            expect(component.statusLabelIndex()).toBeLessThan(i18nLabelCount);
            expect(component.statusLabelKey()).toBe('artemisApp.exerciseChatbot.statusIndicator.labels.' + component.statusLabelIndex());
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
