import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { Overlay } from '@angular/cdk/overlay';
import { FormsModule } from '@angular/forms';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { AccountService } from 'app/core/auth/account.service';
import { Subject, of } from 'rxjs';
import { ChatbotPopupComponent } from 'app/iris/exercise-chatbot/chatbot-popup/chatbot-popup.component';
import { ExerciseChatbotComponent } from 'app/iris/exercise-chatbot/exercise-chatbot.component';
import { ExerciseChatWidgetComponent } from 'app/iris/exercise-chatbot/exercise-chatwidget/exercise-chat-widget.component';
import { IrisSessionService } from 'app/iris/session.service';
import { IrisHttpSessionService } from 'app/iris/http-session.service';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { ActivatedRoute } from '@angular/router';
import { IrisStateStore } from 'app/iris/state-store.service';

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
                afterClosed: jest.fn().mockReturnValue(of('true')),
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
            declarations: [ExerciseChatbotComponent, ChatbotPopupComponent, MockPipe(ArtemisTranslatePipe)],
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

    it('should open dialog when chat not accepted', () => {
        jest.spyOn(component, 'openDialog');

        component.chatAccepted = false;
        component.handleButtonClick();

        expect(component.openDialog).toHaveBeenCalled();
    });

    it('should open chat when chat accepted', () => {
        jest.spyOn(component, 'openChat');

        component.chatAccepted = true;
        component.handleButtonClick();

        expect(component.openChat).toHaveBeenCalled();
    });

    it('should open chat and set buttonDisabled and chatOpen flags', () => {
        jest.spyOn(mockDialog, 'open');
        component.buttonDisabled = false;

        component.openChat();

        expect(mockDialog.open).toHaveBeenCalledWith(ExerciseChatWidgetComponent, {
            hasBackdrop: false,
            position: { bottom: '0px', right: '0px' },
            data: expect.objectContaining({
                stateStore: stateStore,
            }),
        });
        expect(component.buttonDisabled).toBeTrue();
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
        component.openDialog();

        // when
        component.ngOnDestroy();

        // then
        expect(mockDialogClose).toHaveBeenCalled();
    });
});
