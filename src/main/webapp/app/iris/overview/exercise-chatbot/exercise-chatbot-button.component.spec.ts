import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
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
    let component: IrisExerciseChatbotButtonComponent;
    let fixture: ComponentFixture<IrisExerciseChatbotButtonComponent>;
    let chatService: IrisChatService;
    let chatHttpServiceMock: jest.Mocked<IrisChatHttpService>;
    let wsServiceMock: jest.Mocked<IrisWebsocketService>;
    let mockDialog: MatDialog;
    let mockOverlay: Overlay;
    let mockActivatedRoute: ActivatedRoute;
    let mockDialogClose: any;
    let mockParamsSubject: any;
    let accountService: AccountService;

    const statusMock = {
        currentRatelimitInfo: jest.fn().mockReturnValue(of({})),
        handleRateLimitInfo: jest.fn(),
        setCurrentCourse: jest.fn(),
    };
    const accountMock = { selectedLLMUsageTimestamp: dayjs() } as User;

    const mockExerciseId = 123;
    const mockCourseId = 456;

    beforeEach(async () => {
        mockParamsSubject = new Subject();
        mockActivatedRoute = {
            params: mockParamsSubject,
        } as unknown as ActivatedRoute;

        mockDialogClose = jest.fn();

        mockDialog = {
            open: jest.fn().mockReturnValue({
                afterClosed: jest.fn().mockReturnValue({
                    subscribe: jest.fn(),
                }),
                close: mockDialogClose,
            }),
            closeAll: jest.fn(),
        } as unknown as MatDialog;

        mockOverlay = {
            scrollStrategies: {
                noop: jest.fn().mockReturnValue({}),
            },
        } as unknown as Overlay;

        await TestBed.configureTestingModule({
            imports: [FontAwesomeModule, MockPipe(HtmlForMarkdownPipe)],
            declarations: [IrisExerciseChatbotButtonComponent, MockComponent(IrisLogoComponent)],
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
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(IrisExerciseChatbotButtonComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();
                chatService = TestBed.inject(IrisChatService);
                chatService.setCourseId(mockCourseId);
                chatHttpServiceMock = TestBed.inject(IrisChatHttpService) as jest.Mocked<IrisChatHttpService>;
                wsServiceMock = TestBed.inject(IrisWebsocketService) as jest.Mocked<IrisWebsocketService>;
                accountService = TestBed.inject(AccountService);

                accountService.userIdentity.set(accountMock);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should subscribe to route.params and call chatService.switchTo with exercise mode', fakeAsync(() => {
        jest.spyOn(chatHttpServiceMock, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(mockExerciseId)));
        jest.spyOn(chatHttpServiceMock, 'getChatSessions').mockReturnValue(of([]));
        jest.spyOn(wsServiceMock, 'subscribeToSession').mockReturnValueOnce(of());
        const spy = jest.spyOn(chatService, 'switchTo');

        component.mode = ChatServiceMode.PROGRAMMING_EXERCISE;
        fixture.changeDetectorRef.detectChanges();

        mockParamsSubject.next({
            exerciseId: mockExerciseId,
        });
        fixture.whenStable();
        tick();

        expect(spy).toHaveBeenCalledExactlyOnceWith(ChatServiceMode.PROGRAMMING_EXERCISE, mockExerciseId);
    }));

    it('should subscribe to route.params and call chatService.switchTo with text exercise mode', fakeAsync(() => {
        jest.spyOn(chatHttpServiceMock, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(mockExerciseId)));
        jest.spyOn(chatHttpServiceMock, 'getChatSessions').mockReturnValue(of([]));
        jest.spyOn(wsServiceMock, 'subscribeToSession').mockReturnValueOnce(of());
        const spy = jest.spyOn(chatService, 'switchTo');

        component.mode = ChatServiceMode.TEXT_EXERCISE;
        fixture.changeDetectorRef.detectChanges();

        mockParamsSubject.next({
            exerciseId: mockExerciseId,
        });
        fixture.whenStable();
        tick();

        expect(spy).toHaveBeenCalledExactlyOnceWith(ChatServiceMode.TEXT_EXERCISE, mockExerciseId);
    }));

    it('should close the dialog when destroying the object', () => {
        // given
        component.openChat();

        // when
        component.ngOnDestroy();

        // then
        expect(mockDialogClose).toHaveBeenCalled();
    });

    it('should not show new message indicator when chatbot is closed', fakeAsync(() => {
        // given
        jest.spyOn(chatHttpServiceMock, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(mockExerciseId)));
        jest.spyOn(chatHttpServiceMock, 'getChatSessions').mockReturnValue(of([]));
        jest.spyOn(wsServiceMock, 'subscribeToSession').mockReturnValueOnce(of(mockWebsocketServerMessage));
        mockParamsSubject.next({
            exerciseId: mockExerciseId,
        });
        chatService.switchTo(ChatServiceMode.PROGRAMMING_EXERCISE, mockExerciseId);

        // when
        tick();
        fixture.changeDetectorRef.detectChanges();

        // then
        const unreadIndicatorElement: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('.unread-indicator');
        expect(unreadIndicatorElement).toBeNull();
        flush();
    }));

    it('should not show new message indicator when chatbot is open', fakeAsync(() => {
        // given
        jest.spyOn(chatHttpServiceMock, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(mockExerciseId)));
        jest.spyOn(chatHttpServiceMock, 'getChatSessions').mockReturnValue(of([]));
        jest.spyOn(wsServiceMock, 'subscribeToSession').mockReturnValueOnce(of(mockWebsocketServerMessage));
        mockParamsSubject.next({
            exerciseId: mockExerciseId,
        });
        chatService.switchTo(ChatServiceMode.PROGRAMMING_EXERCISE, mockExerciseId);
        component.openChat();

        // when
        tick();
        fixture.changeDetectorRef.detectChanges();

        // then
        const unreadIndicatorElement: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('.unread-indicator');
        expect(unreadIndicatorElement).toBeNull();
        flush();
    }));

    it('should open chatbot if irisQuestion is provided in the queryParams', () => {
        const mockQueryParams = { irisQuestion: 'Can you explain me the error I got?' };
        const activatedRoute = TestBed.inject(ActivatedRoute);

        (activatedRoute.queryParams as any) = of(mockQueryParams);

        component.ngOnInit();

        expect(component.chatOpen).toBeTrue();
    });

    it('should not open the chatbot if no irisQuestion is provided in the queryParams', () => {
        const mockQueryParams = {};
        const activatedRoute = TestBed.inject(ActivatedRoute);

        (activatedRoute.queryParams as any) = of(mockQueryParams);

        component.ngOnInit();

        expect(component.chatOpen).toBeFalse();
    });
});
