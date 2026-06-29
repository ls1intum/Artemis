import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DialogService } from 'primeng/dynamicdialog';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { AccountService } from 'app/core/auth/account.service';
import { EMPTY, Subject, of } from 'rxjs';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ActivatedRoute } from '@angular/router';
import { mockServerSessionHttpResponseWithId, mockWebsocketServerMessage } from 'test/helpers/sample/iris-sample-data';
import { IrisExerciseChatbotButtonComponent } from 'app/iris/overview/exercise-chatbot/exercise-chatbot-button.component';
import { IrisChatbotWidgetComponent } from 'app/iris/overview/exercise-chatbot/widget/chatbot-widget.component';
import { IrisChatHttpService } from 'app/iris/overview/services/iris-chat-http.service';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { IrisStageStateDTO } from 'app/iris/shared/entities/iris-stage-dto.model';
import { IrisLogoComponent } from 'app/iris/overview/iris-logo/iris-logo.component';
import { IrisWebsocketService } from 'app/iris/overview/services/iris-websocket.service';
import { IrisStatusService } from 'app/iris/overview/services/iris-status.service';
import { UserService } from 'app/account/user/shared/user.service';
import dayjs from 'dayjs/esm';
import { provideHttpClient } from '@angular/common/http';
import { HtmlForMarkdownPipe } from 'app/foundation/pipes/html-for-markdown.pipe';
import { User } from 'app/account/user/user.model';
import { TranslateService } from '@ngx-translate/core';

describe('ExerciseChatbotButtonComponent', () => {
    setupTestBed({ zoneless: true });

    let component: IrisExerciseChatbotButtonComponent;
    let fixture: ComponentFixture<IrisExerciseChatbotButtonComponent>;
    let chatService: IrisChatService;
    let chatHttpServiceMock: IrisChatHttpService;
    let wsServiceMock: IrisWebsocketService;
    let mockDialogService: DialogService;
    let mockActivatedRoute: ActivatedRoute;
    let mockDialogClose: ReturnType<typeof vi.fn>;
    let mockDialogOnClose: Subject<void>;
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
        mockDialogOnClose = new Subject<void>();

        mockDialogService = {
            open: vi.fn().mockReturnValue({
                onClose: mockDialogOnClose.asObservable(),
                close: mockDialogClose,
            }),
        } as unknown as DialogService;

        await TestBed.configureTestingModule({
            imports: [FontAwesomeModule, MockPipe(HtmlForMarkdownPipe), IrisExerciseChatbotButtonComponent, MockComponent(IrisLogoComponent)],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                IrisChatService,
                MockProvider(IrisChatHttpService),
                MockProvider(IrisWebsocketService),
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
        })
            // DialogService is provided at the component level (providers: [DialogService]), so it must
            // be overridden on the component, not the module, for the mock to take effect.
            .overrideComponent(IrisExerciseChatbotButtonComponent, {
                set: { providers: [{ provide: DialogService, useValue: mockDialogService }] },
            })
            .compileComponents();

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

        // Prevent openChat's auto-triggered HTTP calls from interfering with individual tests.
        vi.spyOn(chatHttpServiceMock, 'getChatSessions').mockReturnValue(of([]));
        vi.spyOn(chatHttpServiceMock, 'createCourseSession').mockReturnValue(EMPTY);
        vi.spyOn(chatHttpServiceMock, 'getChatSessionById').mockReturnValue(EMPTY);
        vi.spyOn(wsServiceMock, 'subscribeToSession').mockReturnValue(of());

        // Emit empty query params initially
        mockQueryParamsSubject.next({});

        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should subscribe to route.params and call chatService.openChat with exercise mode', async () => {
        vi.spyOn(chatHttpServiceMock, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(mockExerciseId)));
        vi.spyOn(chatHttpServiceMock, 'getChatSessions').mockReturnValue(of([]));
        vi.spyOn(wsServiceMock, 'subscribeToSession').mockReturnValueOnce(of());
        const spy = vi.spyOn(chatService, 'openChat');

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

    it('should subscribe to route.params and call chatService.openChat with text exercise mode', async () => {
        vi.spyOn(chatHttpServiceMock, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(mockExerciseId)));
        vi.spyOn(chatHttpServiceMock, 'getChatSessions').mockReturnValue(of([]));
        vi.spyOn(wsServiceMock, 'subscribeToSession').mockReturnValueOnce(of());
        const spy = vi.spyOn(chatService, 'openChat');

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
        chatService.openChat(ChatServiceMode.PROGRAMMING_EXERCISE, mockExerciseId);

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
        chatService.openChat(ChatServiceMode.PROGRAMMING_EXERCISE, mockExerciseId);
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
            // chatOpen is reset via the onClose subscription
            mockDialogOnClose.next();
            await fixture.whenStable();
            expect(component.chatOpen()).toBe(false);
        });

        it('should open chat when chat is closed', () => {
            expect(component.chatOpen()).toBe(false);

            component.handleButtonClick();

            expect(component.chatOpen()).toBe(true);
            expect(mockDialogService.open).toHaveBeenCalled();
        });

        it('should open the chat widget dialog with the correct floating, non-modal config', () => {
            component.openChat();

            expect(mockDialogService.open).toHaveBeenCalledWith(
                IrisChatbotWidgetComponent,
                expect.objectContaining({
                    modal: false,
                    closable: false,
                    dismissableMask: false,
                    showHeader: false,
                    styleClass: 'iris-chat-widget-dialog',
                }),
            );
        });
    });

    describe('dialog close handling', () => {
        it('should reset state when dialog is closed externally', async () => {
            component.openChat();
            component.newIrisMessage.set('Some message');
            expect(component.chatOpen()).toBe(true);

            // Simulate dialog closing
            mockDialogOnClose.next();
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
        it('should subscribe to route.params and call chatService.openChat with lecture mode', async () => {
            const lectureId = 789;
            vi.spyOn(chatHttpServiceMock, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(lectureId)));
            vi.spyOn(chatHttpServiceMock, 'getChatSessions').mockReturnValue(of([]));
            vi.spyOn(wsServiceMock, 'subscribeToSession').mockReturnValueOnce(of());
            const spy = vi.spyOn(chatService, 'openChat');

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
