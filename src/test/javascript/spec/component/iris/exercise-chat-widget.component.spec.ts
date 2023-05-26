import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExerciseChatWidgetComponent } from 'app/overview/exercise-chatbot/exercise-chatwidget/exercise-chat-widget.component';
import { ChatbotPopupComponent } from 'app/overview/exercise-chatbot/chatbot-popup/chatbot-popup.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FormsModule } from '@angular/forms';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { MockPipe } from 'ng-mocks';
import { MatDialogModule } from '@angular/material/dialog';

describe('ExerciseChatWidgetComponent', () => {
    let component: ExerciseChatWidgetComponent;
    let fixture: ComponentFixture<ExerciseChatWidgetComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FormsModule, FontAwesomeModule, MatDialogModule],
            declarations: [ExerciseChatWidgetComponent, ChatbotPopupComponent, MockPipe(ArtemisTranslatePipe)],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(ExerciseChatWidgetComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create the component', () => {
        expect(component).toBeTruthy();
    });

    it('should add user message on send', () => {
        component.newMessage = 'Hello';

        component.onSend();

        expect(component.userMessages).toContain('Hello');
    });

    it('should clear newMessage on send', () => {
        component.newMessage = 'Hello';

        component.onSend();

        expect(component.newMessage).toBe('');
    });
});
