import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { Overlay } from '@angular/cdk/overlay';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { AccountService } from 'app/core/auth/account.service';
import { Subject, of } from 'rxjs';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { ActivatedRoute } from '@angular/router';
import { mockServerSessionHttpResponseWithId, mockWebsocketServerMessage } from '../../../helpers/sample/iris-sample-data';
import { IrisExerciseChatbotButtonComponent } from 'app/iris/exercise-chatbot/exercise-chatbot-button.component';
import { IrisChatHttpService } from 'app/iris/iris-chat-http.service';
import { ChatServiceMode, IrisChatService } from 'app/iris/iris-chat.service';
import { IrisLogoComponent } from 'app/iris/iris-logo/iris-logo.component';
import { IrisWebsocketService } from 'app/iris/iris-websocket.service';
import { IrisStatusService } from 'app/iris/iris-status.service';
import { UserService } from 'app/core/user/user.service';
import dayjs from 'dayjs/esm';
import { provideHttpClient } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { HtmlForMarkdownPipe } from '../../../../../../main/webapp/app/shared/pipes/html-for-markdown.pipe';

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

    const statusMock = {
        currentRatelimitInfo: jest.fn().mockReturnValue(of({})),
        handleRateLimitInfo: jest.fn(),
    };
    const userMock = {
        acceptExternalLLMUsage: jest.fn(),
    };
    const accountMock = {
        userIdentity: { externalLLMUsageAccepted: dayjs() },
    };

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
            imports: [FontAwesomeModule, NoopAnimationsModule, MockPipe(HtmlForMarkdownPipe)],
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
                { provide: UserService, useValue: userMock },
                { provide: AccountService, useValue: accountMock },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(IrisExerciseChatbotButtonComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();
                chatService = fixture.debugElement.injector.get(IrisChatService);
                chatHttpServiceMock = TestBed.inject(IrisChatHttpService) as jest.Mocked<IrisChatHttpService>;
                wsServiceMock = TestBed.inject(IrisWebsocketService) as jest.Mocked<IrisWebsocketService>;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should subscribe to route.params and call chatService.switchTo with exercise mode', fakeAsync(() => {
        jest.spyOn(chatHttpServiceMock, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(123)));
        jest.spyOn(wsServiceMock, 'subscribeToSession').mockReturnValueOnce(of());
        const mockExerciseId = 123;
        const spy = jest.spyOn(chatService, 'switchTo');

        component.mode = ChatServiceMode.EXERCISE;
        fixture.detectChanges();

        mockParamsSubject.next({
            exerciseId: mockExerciseId,
        });
        fixture.whenStable();
        tick();

        expect(spy).toHaveBeenCalledExactlyOnceWith(ChatServiceMode.EXERCISE, mockExerciseId);
    }));

    it('should subscribe to route.params and call chatService.switchTo with text exercise mode', fakeAsync(() => {
        jest.spyOn(chatHttpServiceMock, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(123)));
        jest.spyOn(wsServiceMock, 'subscribeToSession').mockReturnValueOnce(of());
        const mockExerciseId = 123;
        const spy = jest.spyOn(chatService, 'switchTo');

        component.mode = ChatServiceMode.TEXT_EXERCISE;
        fixture.detectChanges();

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

    it('should show new message indicator when chatbot is closed', fakeAsync(() => {
        // given
        jest.spyOn(chatHttpServiceMock, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(123)));
        jest.spyOn(wsServiceMock, 'subscribeToSession').mockReturnValueOnce(of(mockWebsocketServerMessage));
        chatService.switchTo(ChatServiceMode.EXERCISE, 123);

        // when
        fixture.detectChanges();
        tick();

        // then
        const unreadIndicatorElement: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('.unread-indicator');
        expect(unreadIndicatorElement).not.toBeNull();
        flush();
    }));

    it('should not show new message indicator when chatbot is open', fakeAsync(() => {
        // given
        jest.spyOn(chatHttpServiceMock, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(123)));
        jest.spyOn(wsServiceMock, 'subscribeToSession').mockReturnValueOnce(of(mockWebsocketServerMessage));
        chatService.switchTo(ChatServiceMode.EXERCISE, 123);
        component.openChat();

        // when
        fixture.detectChanges();
        tick();

        // then
        const unreadIndicatorElement: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('.unread-indicator');
        expect(unreadIndicatorElement).toBeNull();
        flush();
    }));

    it('should set irisQuestion onInit when provided in the queryParams', () => {
        const mockQueryParams = { irisQuestion: 'Can you explain me the error I got?' };
        const activatedRoute = TestBed.inject(ActivatedRoute);

        (activatedRoute.queryParams as any) = of(mockQueryParams);

        component.ngOnInit();

        expect(component.irisQuestion).toBe(mockQueryParams.irisQuestion);
    });

    it('should open chatbot if irisQuestion is provided in the queryParams', () => {
        const mockQueryParams = { irisQuestion: 'Can you explain me the error I got?' };
        const activatedRoute = TestBed.inject(ActivatedRoute);

        (activatedRoute.queryParams as any) = of(mockQueryParams);

        component.ngOnInit();

        expect(component.chatOpen).toBe(true);
    });
});
