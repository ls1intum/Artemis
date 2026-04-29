import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { Overlay } from '@angular/cdk/overlay';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { MockComponent, MockPipe } from 'ng-mocks';
import { AccountService } from 'app/core/auth/account.service';
import { BehaviorSubject, Subject, of } from 'rxjs';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ActivatedRoute } from '@angular/router';
import { IrisExerciseChatbotButtonComponent } from 'app/iris/overview/exercise-chatbot/exercise-chatbot-button.component';
import { ChatServiceMode } from 'app/iris/shared/entities/iris-chat-mode.model';
import { IrisChatControllerService } from 'app/iris/overview/services/iris-chat-controller.service';
import { IrisStageDTO, IrisStageStateDTO } from 'app/iris/shared/entities/iris-stage-dto.model';
import { IrisLogoComponent } from 'app/iris/overview/iris-logo/iris-logo.component';
import { IrisMessage } from 'app/iris/shared/entities/iris-message.model';
import dayjs from 'dayjs/esm';
import { provideHttpClient } from '@angular/common/http';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { User } from 'app/core/user/user.model';
import { TranslateService } from '@ngx-translate/core';

describe('ExerciseChatbotButtonComponent', () => {
    setupTestBed({ zoneless: true });

    let component: IrisExerciseChatbotButtonComponent;
    let fixture: ComponentFixture<IrisExerciseChatbotButtonComponent>;
    let mockDialog: MatDialog;
    let mockOverlay: Overlay;
    let mockActivatedRoute: ActivatedRoute;
    let mockDialogClose: ReturnType<typeof vi.fn>;
    let mockDialogAfterClosed: Subject<void>;
    let mockParamsSubject: Subject<any>;
    let mockQueryParamsSubject: Subject<any>;
    let accountService: AccountService;
    let mockController: ReturnType<typeof createControllerMock>;

    const accountMock = { selectedLLMUsageTimestamp: dayjs() } as User;

    const mockExerciseId = 123;
    const mockCourseId = 456;

    const createControllerMock = () => ({
        setContext: vi.fn<(courseId: number | undefined, mode?: ChatServiceMode, entityId?: number) => void>(),
        setShouldReopenChat: vi.fn(),
        numNewMessages: new BehaviorSubject<number>(0),
        stages: new BehaviorSubject<IrisStageDTO[]>([]),
        newIrisMessage: new BehaviorSubject<IrisMessage | undefined>(undefined),
        shouldReopenChat$: new BehaviorSubject<boolean>(false).asObservable(),
    });

    beforeEach(async () => {
        mockController = createControllerMock();
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
                { provide: MatDialog, useValue: mockDialog },
                { provide: Overlay, useValue: mockOverlay },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ActivatedRoute, useValue: mockActivatedRoute },
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
            .overrideComponent(IrisExerciseChatbotButtonComponent, {
                remove: { providers: [IrisChatControllerService] },
                add: { providers: [{ provide: IrisChatControllerService, useValue: mockController }] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(IrisExerciseChatbotButtonComponent);
        component = fixture.componentInstance;

        // Set required inputs BEFORE first detectChanges
        fixture.componentRef.setInput('mode', ChatServiceMode.PROGRAMMING_EXERCISE);
        fixture.componentRef.setInput('courseId', mockCourseId);

        accountService = TestBed.inject(AccountService);
        accountService.userIdentity.set(accountMock);

        mockQueryParamsSubject.next({});

        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should subscribe to route.params and call controller.setContext with exercise mode', async () => {
        fixture.componentRef.setInput('mode', ChatServiceMode.PROGRAMMING_EXERCISE);
        fixture.changeDetectorRef.detectChanges();

        mockParamsSubject.next({ exerciseId: mockExerciseId });

        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();

        expect(mockController.setContext).toHaveBeenCalledWith(mockCourseId, ChatServiceMode.PROGRAMMING_EXERCISE, mockExerciseId);
    });

    it('should subscribe to route.params and call controller.setContext with text exercise mode', async () => {
        fixture.componentRef.setInput('mode', ChatServiceMode.TEXT_EXERCISE);
        fixture.changeDetectorRef.detectChanges();

        mockParamsSubject.next({ exerciseId: mockExerciseId });

        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();

        expect(mockController.setContext).toHaveBeenCalledWith(mockCourseId, ChatServiceMode.TEXT_EXERCISE, mockExerciseId);
    });

    it('should pass viewContainerRef to MatDialog.open so the popup widget inherits the host injector', () => {
        component.openChat();

        const openCall = (mockDialog.open as unknown as ReturnType<typeof vi.fn>).mock.calls[0];
        const config = openCall[1];
        expect(config.viewContainerRef).toBeDefined();
    });

    it('should close the dialog when destroying the object', () => {
        component.openChat();
        fixture.destroy();
        expect(mockDialogClose).toHaveBeenCalled();
    });

    it('should not show new message indicator when chatbot is closed', async () => {
        mockParamsSubject.next({ exerciseId: mockExerciseId });

        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();

        const unreadIndicatorElement: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('.unread-indicator');
        expect(unreadIndicatorElement).toBeNull();
    });

    it('should not show new message indicator when chatbot is open', async () => {
        mockParamsSubject.next({ exerciseId: mockExerciseId });
        component.openChat();

        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();

        const unreadIndicatorElement: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('.unread-indicator');
        expect(unreadIndicatorElement).toBeNull();
    });

    it('should not open the chatbot if no irisQuestion is provided in the queryParams', async () => {
        const mockQueryParams = {};
        fixture.componentRef.setInput('mode', ChatServiceMode.PROGRAMMING_EXERCISE);

        mockQueryParamsSubject.next(mockQueryParams);
        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();

        expect(component.chatOpen()).toBe(false);
    });

    describe('handleButtonClick', () => {
        it('should close dialog and set chatOpen to false when chat is open', async () => {
            component.openChat();
            expect(component.chatOpen()).toBe(true);

            component.handleButtonClick();

            expect(mockDialogClose).toHaveBeenCalled();
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

            mockDialogAfterClosed.next();
            await fixture.whenStable();

            expect(component.chatOpen()).toBe(false);
            expect(component.newIrisMessage()).toBeUndefined();
        });
    });

    describe('stage display name', () => {
        it('should show rotation label when stage message is empty', async () => {
            mockController.stages.next([{ name: 'Executing pipeline', state: IrisStageStateDTO.IN_PROGRESS, weight: 10, message: '', internal: false }]);
            await fixture.whenStable();

            expect(component.displayName()).toBe('artemisApp.iris.stages.thinking');
            expect(component.isProcessing()).toBe(true);
        });

        it('should show stage message when provided', async () => {
            mockController.stages.next([{ name: 'Executing pipeline', state: IrisStageStateDTO.IN_PROGRESS, weight: 10, message: 'Checking info', internal: false }]);
            await fixture.whenStable();

            expect(component.displayName()).toBe('Checking info');
            expect(component.isProcessing()).toBe(true);
        });

        it('should return empty string when no active stage', async () => {
            mockController.stages.next([{ name: 'Done Stage', state: IrisStageStateDTO.DONE, weight: 10, message: '', internal: false }]);
            await fixture.whenStable();

            expect(component.displayName()).toBe('');
            expect(component.isProcessing()).toBe(false);
        });
    });

    describe('lecture mode', () => {
        it('should subscribe to route.params and call controller.setContext with lecture mode', async () => {
            const lectureId = 789;

            fixture.componentRef.setInput('mode', ChatServiceMode.LECTURE);
            fixture.changeDetectorRef.detectChanges();

            mockParamsSubject.next({ lectureId });
            await fixture.whenStable();

            expect(mockController.setContext).toHaveBeenCalledWith(mockCourseId, ChatServiceMode.LECTURE, lectureId);
        });
    });
});
