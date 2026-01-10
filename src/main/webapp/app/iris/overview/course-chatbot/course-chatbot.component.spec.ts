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
    let component: CourseChatbotComponent;
    let fixture: ComponentFixture<CourseChatbotComponent>;
    let chatService: jest.Mocked<IrisChatService>;

    beforeEach(async () => {
        const mockChatService = {
            setCourseId: jest.fn(),
            switchTo: jest.fn(),
        };

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
        chatService = TestBed.inject(IrisChatService) as jest.Mocked<IrisChatService>;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should call switchTo when courseId changes', async () => {
        fixture.componentRef.setInput('courseId', 2);
        await fixture.whenStable();

        expect(chatService.switchTo).toHaveBeenCalledWith(ChatServiceMode.COURSE, 2);
    });
});
