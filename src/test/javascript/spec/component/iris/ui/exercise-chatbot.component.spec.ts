import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { Overlay } from '@angular/cdk/overlay';
import { FormsModule } from '@angular/forms';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { AccountService } from 'app/core/auth/account.service';
import { Subject } from 'rxjs';
import { ExerciseChatbotComponent } from 'app/iris/exercise-chatbot/exercise-chatbot.component';
import { IrisSessionService } from 'app/iris/session.service';
import { IrisHttpSessionService } from 'app/iris/http-session.service';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { ActivatedRoute } from '@angular/router';
import { IrisStateStore } from 'app/iris/state-store.service';
import { ActiveConversationMessageLoadedAction, NumNewMessagesResetAction, SessionReceivedAction } from 'app/iris/state-store.model';
import { mockServerMessage } from './../../../helpers/sample/iris-sample-data';

describe('ExerciseChatbotComponent', () => {
    let component: ExerciseChatbotComponent;
    let fixture: ComponentFixture<ExerciseChatbotComponent>;
    let sessionService: IrisSessionService;
    let stateStore: IrisStateStore;
    let mockDialog: MatDialog;
    let mockOverlay: Overlay;
    let mockActivatedRoute: ActivatedRoute;
    let mockDialogClose: any;
    let mockParamsSubject: any;

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
            imports: [FormsModule, FontAwesomeModule, HttpClientTestingModule],
            declarations: [ExerciseChatbotComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                IrisHttpSessionService,
                { provide: MatDialog, useValue: mockDialog },
                { provide: Overlay, useValue: mockOverlay },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ActivatedRoute, useValue: mockActivatedRoute },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseChatbotComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();
                sessionService = fixture.debugElement.injector.get(IrisSessionService);
                stateStore = fixture.debugElement.injector.get(IrisStateStore);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should subscribe to route.params and call sessionService.getCurrentSessionOrCreate', waitForAsync(async () => {
        const mockExerciseId = 123;
        const spy = jest.spyOn(sessionService, 'getCurrentSessionOrCreate');

        mockParamsSubject.next(mockExerciseId);
        await fixture.whenStable();

        expect(spy).toHaveBeenCalled();
    }));

    it('should close the dialog when destroying the object', () => {
        // given
        component.openChat();

        // when
        component.ngOnDestroy();

        // then
        expect(mockDialogClose).toHaveBeenCalled();
    });

    it('should show new message indicator when chatbot is closed', () => {
        // given
        stateStore.dispatch(new SessionReceivedAction(0, []));

        // when
        stateStore.dispatch(new ActiveConversationMessageLoadedAction(mockServerMessage));

        // then
        fixture.detectChanges();
        const unreadIndicatorElement: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('.unread-indicator');
        expect(unreadIndicatorElement).not.toBeNull();
    });

    it('should not show new message indicator when chatbot is open', () => {
        // given
        stateStore.dispatch(new SessionReceivedAction(0, []));
        component.openChat();

        // when
        stateStore.dispatch(new ActiveConversationMessageLoadedAction(mockServerMessage));

        // then
        fixture.detectChanges();
        const unreadIndicatorElement: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('.unread-indicator');
        expect(unreadIndicatorElement).toBeNull();
    });

    it('should call action to reset number of new messages when close chat', () => {
        // given
        jest.spyOn(stateStore, 'dispatch');
        stateStore.dispatch(new SessionReceivedAction(0, []));
        component.openChat();

        // when
        component.handleButtonClick();
        fixture.detectChanges();
        // then
        expect(stateStore.dispatch).toHaveBeenCalledWith(new NumNewMessagesResetAction());
    });
});
