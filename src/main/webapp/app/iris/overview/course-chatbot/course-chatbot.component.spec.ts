import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Component, input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseChatbotComponent } from 'app/iris/overview/course-chatbot/course-chatbot.component';
import { IrisBaseChatbotComponent } from 'app/iris/overview/base-chatbot/iris-base-chatbot.component';
import { ChatServiceMode } from 'app/iris/shared/entities/iris-chat-mode.model';
import { IrisChatControllerService } from 'app/iris/overview/services/iris-chat-controller.service';

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
    let controller: ReturnType<typeof createControllerMock>;

    const createControllerMock = () => ({
        setContext: vi.fn<(courseId: number | undefined, mode?: ChatServiceMode, entityId?: number) => void>(),
    });

    beforeEach(async () => {
        const mockController = createControllerMock();

        await TestBed.configureTestingModule({
            imports: [CourseChatbotComponent],
        })
            .overrideComponent(CourseChatbotComponent, {
                remove: { imports: [IrisBaseChatbotComponent], providers: [IrisChatControllerService] },
                add: { imports: [MockIrisBaseChatbotComponent], providers: [{ provide: IrisChatControllerService, useValue: mockController }] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(CourseChatbotComponent);
        component = fixture.componentInstance;
        controller = mockController;
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should call setContext when courseId changes', async () => {
        fixture.componentRef.setInput('courseId', 2);
        await fixture.whenStable();

        expect(controller.setContext).toHaveBeenCalledWith(2, ChatServiceMode.COURSE, 2);
    });

    it('should not call setContext when courseId is undefined', async () => {
        fixture.componentRef.setInput('courseId', undefined);
        await fixture.whenStable();

        expect(controller.setContext).not.toHaveBeenCalled();
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
