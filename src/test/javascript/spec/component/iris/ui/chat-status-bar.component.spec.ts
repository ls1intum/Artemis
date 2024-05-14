import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ChatStatusBarComponent } from 'app/iris/base-chatbot/chat-status-bar/chat-status-bar.component';

describe('ChatStatusBarComponent', () => {
    let component: ChatStatusBarComponent;
    let fixture: ComponentFixture<ChatStatusBarComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ChatStatusBarComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(ChatStatusBarComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
