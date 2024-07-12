import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseChatbotComponent } from 'app/iris/course-chatbot/course-chatbot.component';
import { IrisBaseChatbotComponent } from 'app/iris/base-chatbot/iris-base-chatbot.component';
import { MockComponent, MockProvider } from 'ng-mocks';
import { ChatServiceMode, IrisChatService } from 'app/iris/iris-chat.service';
import { SimpleChange } from '@angular/core';

describe('CourseChatbotComponent', () => {
    let component: CourseChatbotComponent;
    let fixture: ComponentFixture<CourseChatbotComponent>;
    let chatService: IrisChatService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [CourseChatbotComponent, MockComponent(IrisBaseChatbotComponent)],
            providers: [MockProvider(IrisChatService)],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseChatbotComponent);
        component = fixture.componentInstance;
        chatService = TestBed.inject(IrisChatService);
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should call switchTo when courseId changes', () => {
        const switchToSpy = jest.spyOn(chatService, 'switchTo');
        component.courseId = 2;
        component.ngOnChanges({ courseId: new SimpleChange(null, component.courseId, true) });

        expect(switchToSpy).toHaveBeenCalledWith(ChatServiceMode.COURSE, component.courseId);
    });
});
