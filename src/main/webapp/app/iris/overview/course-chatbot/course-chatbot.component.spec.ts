import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Component, input, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { CourseChatbotComponent } from 'app/iris/overview/course-chatbot/course-chatbot.component';
import { IrisBaseChatbotComponent } from 'app/iris/overview/base-chatbot/iris-base-chatbot.component';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';

// Simple mock to avoid ng-mocks issues with signal-based viewChild
@Component({
    selector: 'jhi-iris-base-chatbot',
    template: '',
    standalone: true,
})
class MockIrisBaseChatbotComponent {
    // Provide signal inputs that the template binds to
    readonly showDeclineButton = input<boolean>();
    readonly isChatHistoryAvailable = input<boolean>();
    readonly hasAvailableExercises = input<boolean>();

    private readonly _isChatHistoryOpen = signal(true);

    isChatHistoryOpen(): boolean {
        return this._isChatHistoryOpen();
    }

    setChatHistoryVisibility(isOpen: boolean): void {
        this._isChatHistoryOpen.set(isOpen);
    }
}

describe('CourseChatbotComponent', () => {
    setupTestBed({ zoneless: true });

    let component: CourseChatbotComponent;
    let fixture: ComponentFixture<CourseChatbotComponent>;
    let chatService: ReturnType<typeof createChatServiceMock>;

    const createChatServiceMock = () => ({
        setCourseId: vi.fn<(courseId: number | undefined) => void>(),
        switchTo: vi.fn<(mode: ChatServiceMode, courseId: number) => void>(),
    });

    beforeEach(async () => {
        const mockChatService = createChatServiceMock();

        await TestBed.configureTestingModule({
            imports: [CourseChatbotComponent],
            providers: [{ provide: IrisChatService, useValue: mockChatService }],
        })
            .overrideComponent(CourseChatbotComponent, {
                remove: { imports: [IrisBaseChatbotComponent] },
                add: { imports: [MockIrisBaseChatbotComponent] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(CourseChatbotComponent);
        component = fixture.componentInstance;
        chatService = mockChatService;
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should call setCourseId and switchTo when courseId is set', async () => {
        fixture.componentRef.setInput('courseId', 2);
        await fixture.whenStable();

        expect(chatService.setCourseId).toHaveBeenCalledWith(2);
        expect(chatService.switchTo).toHaveBeenCalledWith(ChatServiceMode.COURSE, 2);
    });

    it('should not call setCourseId or switchTo when courseId is undefined', async () => {
        // courseId defaults to undefined; no service calls should have been made
        await fixture.whenStable();

        expect(chatService.setCourseId).not.toHaveBeenCalled();
        expect(chatService.switchTo).not.toHaveBeenCalled();
    });

    it('should call switchTo with the updated courseId when it changes multiple times', async () => {
        fixture.componentRef.setInput('courseId', 1);
        await fixture.whenStable();

        expect(chatService.setCourseId).toHaveBeenCalledWith(1);
        expect(chatService.switchTo).toHaveBeenCalledWith(ChatServiceMode.COURSE, 1);

        fixture.componentRef.setInput('courseId', 2);
        await fixture.whenStable();

        expect(chatService.setCourseId).toHaveBeenCalledWith(2);
        expect(chatService.switchTo).toHaveBeenCalledWith(ChatServiceMode.COURSE, 2);
        expect(chatService.setCourseId).toHaveBeenCalledTimes(2);
        expect(chatService.switchTo).toHaveBeenCalledTimes(2);
    });

    it('should not render the base chatbot when courseId is undefined', () => {
        // No courseId set — @if(courseId()) should hide the child
        const baseChatbot = fixture.debugElement.query(By.directive(MockIrisBaseChatbotComponent));
        expect(baseChatbot).toBeNull();
    });

    it('should render the base chatbot when courseId is set', async () => {
        fixture.componentRef.setInput('courseId', 5);
        await fixture.whenStable();
        fixture.detectChanges();

        const baseChatbot = fixture.debugElement.query(By.directive(MockIrisBaseChatbotComponent));
        expect(baseChatbot).not.toBeNull();
    });

    it('should pass showDeclineButton=false and isChatHistoryAvailable=true to the base chatbot', async () => {
        fixture.componentRef.setInput('courseId', 5);
        await fixture.whenStable();
        fixture.detectChanges();

        const baseChatbot = fixture.debugElement.query(By.directive(MockIrisBaseChatbotComponent)).componentInstance as MockIrisBaseChatbotComponent;
        expect(baseChatbot.showDeclineButton()).toBe(false);
        expect(baseChatbot.isChatHistoryAvailable()).toBe(true);
    });

    it('should forward hasAvailableExercises to the base chatbot', async () => {
        fixture.componentRef.setInput('courseId', 2);
        fixture.componentRef.setInput('hasAvailableExercises', false);
        await fixture.whenStable();
        fixture.detectChanges();

        const baseChatbot = fixture.debugElement.query(By.directive(MockIrisBaseChatbotComponent)).componentInstance as MockIrisBaseChatbotComponent;
        expect(baseChatbot.hasAvailableExercises()).toBe(false);
    });

    it('should default hasAvailableExercises to true', async () => {
        fixture.componentRef.setInput('courseId', 2);
        await fixture.whenStable();
        fixture.detectChanges();

        const baseChatbot = fixture.debugElement.query(By.directive(MockIrisBaseChatbotComponent)).componentInstance as MockIrisBaseChatbotComponent;
        expect(baseChatbot.hasAvailableExercises()).toBe(true);
    });

    describe('toggleChatHistory', () => {
        it('should do nothing when the base chatbot is not rendered', () => {
            // courseId not set → no base chatbot → should not throw
            expect(() => component.toggleChatHistory()).not.toThrow();
        });

        it('should call setChatHistoryVisibility(false) when chat history is open', () => {
            const fakeChatbot = {
                isChatHistoryOpen: vi.fn().mockReturnValue(true),
                setChatHistoryVisibility: vi.fn(),
            } as unknown as IrisBaseChatbotComponent;
            // Inject the fake instance into the private viewChild signal
            (component as any)['irisBaseChatbot'] = vi.fn().mockReturnValue(fakeChatbot);

            component.toggleChatHistory();

            expect(fakeChatbot.setChatHistoryVisibility).toHaveBeenCalledWith(false);
        });

        it('should call setChatHistoryVisibility(true) when chat history is closed', () => {
            const fakeChatbot = {
                isChatHistoryOpen: vi.fn().mockReturnValue(false),
                setChatHistoryVisibility: vi.fn(),
            } as unknown as IrisBaseChatbotComponent;
            (component as any)['irisBaseChatbot'] = vi.fn().mockReturnValue(fakeChatbot);

            component.toggleChatHistory();

            expect(fakeChatbot.setChatHistoryVisibility).toHaveBeenCalledWith(true);
        });

        it('should toggle correctly across multiple calls', () => {
            let isOpen = true;
            const fakeChatbot = {
                isChatHistoryOpen: vi.fn().mockImplementation(() => isOpen),
                setChatHistoryVisibility: vi.fn().mockImplementation((value: boolean) => {
                    isOpen = value;
                }),
            } as unknown as IrisBaseChatbotComponent;
            (component as any)['irisBaseChatbot'] = vi.fn().mockReturnValue(fakeChatbot);

            component.toggleChatHistory(); // open → closed
            expect(fakeChatbot.setChatHistoryVisibility).toHaveBeenLastCalledWith(false);

            component.toggleChatHistory(); // closed → open
            expect(fakeChatbot.setChatHistoryVisibility).toHaveBeenLastCalledWith(true);

            component.toggleChatHistory(); // open → closed
            expect(fakeChatbot.setChatHistoryVisibility).toHaveBeenLastCalledWith(false);

            expect(fakeChatbot.setChatHistoryVisibility).toHaveBeenCalledTimes(3);
        });
    });
});
