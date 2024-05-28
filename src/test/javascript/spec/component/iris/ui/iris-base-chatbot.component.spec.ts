import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { IrisBaseChatbotComponent } from 'app/iris/base-chatbot/iris-base-chatbot.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ChatStatusBarComponent } from 'app/iris/base-chatbot/chat-status-bar/chat-status-bar.component';
import { IrisLogoComponent } from 'app/iris/iris-logo/iris-logo.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateService } from '@ngx-translate/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { UserService } from 'app/core/user/user.service';
import { IrisStatusService } from 'app/iris/iris-status.service';
import { IrisChatHttpService } from 'app/iris/iris-chat-http.service';
import { ChatServiceMode, IrisChatService } from 'app/iris/iris-chat.service';
import { IrisWebsocketService } from 'app/iris/iris-websocket.service';
import { of } from 'rxjs';
import dayjs from 'dayjs/esm';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ButtonComponent } from 'app/shared/components/button.component';
import { FormsModule } from 'app/forms/forms.module';
import { mockConversation, mockUserMessageWithContent } from '../../../helpers/sample/iris-sample-data';

describe('IrisBaseChatbotComponent', () => {
    let component: IrisBaseChatbotComponent;
    let chatService: IrisChatService;
    let httpService: jest.Mocked<IrisChatHttpService>;
    let wsMock: jest.Mocked<IrisWebsocketService>;
    let fixture: ComponentFixture<IrisBaseChatbotComponent>;

    const statusMock = {
        currentRatelimitInfo: jest.fn().mockReturnValue(of({})),
        handleRateLimitInfo: jest.fn(),
        getActiveStatus: jest.fn().mockReturnValue(of({})),
    } as any;
    const mockUserService = {
        acceptIris: jest.fn(),
    } as any;
    let accountMock = {
        userIdentity: { irisAccepted: dayjs() },
    } as any;

    beforeEach(async () => {
        accountMock = {
            userIdentity: { irisAccepted: dayjs() },
        } as any;

        await TestBed.configureTestingModule({
            imports: [FormsModule, FontAwesomeModule, RouterModule],
            declarations: [
                IrisBaseChatbotComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(ChatStatusBarComponent),
                MockComponent(IrisLogoComponent),
                MockComponent(ButtonComponent),
            ],
            providers: [
                MockProvider(NgbModal),
                { provide: ActivatedRoute, useValue: {} },
                { provide: LocalStorageService, useValue: {} },
                { provide: TranslateService, useValue: {} },
                { provide: SessionStorageService, useValue: {} },
                { provide: HttpClient, useValue: {} },
                { provide: AccountService, useValue: accountMock },
                { provide: UserService, useValue: mockUserService },
                { provide: IrisStatusService, useValue: statusMock },
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
                httpService = TestBed.inject(IrisChatHttpService) as jest.Mocked<IrisChatHttpService>;
                wsMock = TestBed.inject(IrisWebsocketService) as jest.Mocked<IrisWebsocketService>;
                component = fixture.componentInstance;

                fixture.nativeElement.querySelector('.chat-body').scrollTo = jest.fn();
                fixture.detectChanges();
            });
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should set userAccepted to false if user has not accepted the policy', () => {
        accountMock.userIdentity.irisAccepted = undefined;
        component.ngOnInit();
        expect(component.userAccepted).toBeFalse();
    });

    it('should set userAccepted to true if user has accepted the policy', () => {
        component.ngOnInit();
        expect(component.userAccepted).toBeTrue();
    });

    it('should call API when user accept the policy', () => {
        const stub = jest.spyOn(mockUserService, 'acceptIris');
        stub.mockReturnValue(of(new HttpResponse<void>()));

        component.acceptPermission();

        expect(stub).toHaveBeenCalledOnce();
        expect(component.userAccepted).toBeTrue();
    });

    it('should add user message on send', waitForAsync(async () => {
        // given
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of({ body: { ...mockConversation } }));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());

        const content = 'Hello';
        const createdMessage = mockUserMessageWithContent(content);
        jest.spyOn(httpService, 'createMessage').mockReturnValueOnce(of({ body: createdMessage }));

        jest.spyOn(component, 'scrollToBottom').mockImplementation(() => {});

        const stub = jest.spyOn(chatService, 'sendMessage');
        component.newMessageTextContent = content;
        chatService.switchTo(ChatServiceMode.COURSE, 123);

        // when
        component.onSend();

        await fixture.whenStable();

        // then
        expect(component.messages).toContain(createdMessage);
        expect(stub).toHaveBeenCalledWith(content);
    }));

    it('should resend message', waitForAsync(async () => {
        // given
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of({ body: { ...mockConversation } }));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());

        const content = 'Hello';
        const createdMessage = mockUserMessageWithContent(content);
        createdMessage.id = 2;
        jest.spyOn(httpService, 'resendMessage').mockReturnValueOnce(of({ body: createdMessage }));
        jest.spyOn(component, 'scrollToBottom').mockImplementation(() => {});

        const stub = jest.spyOn(chatService, 'sendMessage');
        component.newMessageTextContent = content;
        chatService.switchTo(ChatServiceMode.COURSE, 123);

        // when
        component.resendMessage(createdMessage);

        await fixture.whenStable();

        // then
        expect(component.messages).toContain(createdMessage);
        expect(stub).toHaveBeenCalledWith(content);
    }));
});
