import { ConversationMessagesComponent } from 'app/overview/course-conversations/layout/conversation-messages/conversation-messages.component';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';

describe('ConversationMessagesComponent', () => {
    let component: ConversationMessagesComponent;
    let fixture: ComponentFixture<ConversationMessagesComponent>;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({ declarations: [ConversationMessagesComponent] }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(ConversationMessagesComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it.todo('should create');
});
