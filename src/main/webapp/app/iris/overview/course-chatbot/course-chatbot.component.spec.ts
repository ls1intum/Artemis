import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Component, input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
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

    it('should call switchTo when courseId changes', async () => {
        fixture.componentRef.setInput('courseId', 2);
        await fixture.whenStable();

        expect(chatService.switchTo).toHaveBeenCalledWith(ChatServiceMode.COURSE, 2);
    });

    it('should not call switchTo when courseId is undefined', async () => {
        fixture.componentRef.setInput('courseId', undefined);
        await fixture.whenStable();

        expect(chatService.switchTo).not.toHaveBeenCalled();
    });

    it('should return early in toggleChatHistory when baseChatbot is not available', () => {
        expect(() => component.toggleChatHistory()).not.toThrow();
    });

    it('should open chat history when it is currently closed', () => {
        const mockBaseChatbot = {
            isChatHistoryOpen: vi.fn().mockReturnValue(false),
            setChatHistoryVisibility: vi.fn(),
        };
        vi.spyOn(component as any, 'irisBaseChatbot').mockReturnValue(mockBaseChatbot);

        component.toggleChatHistory();

        expect(mockBaseChatbot.setChatHistoryVisibility).toHaveBeenCalledWith(true);
    });

    it('should close chat history when it is currently open', () => {
        const mockBaseChatbot = {
            isChatHistoryOpen: vi.fn().mockReturnValue(true),
            setChatHistoryVisibility: vi.fn(),
        };
        vi.spyOn(component as any, 'irisBaseChatbot').mockReturnValue(mockBaseChatbot);

        component.toggleChatHistory();

        expect(mockBaseChatbot.setChatHistoryVisibility).toHaveBeenCalledWith(false);
    });
});
