import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { ConversationHeaderComponent } from 'app/overview/course-conversations/layout/conversation-header/conversation-header.component';

describe('ConversationHeaderComponent', () => {
    let component: ConversationHeaderComponent;
    let fixture: ComponentFixture<ConversationHeaderComponent>;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({ declarations: [ConversationHeaderComponent] }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(ConversationHeaderComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it.todo('should create');
});
