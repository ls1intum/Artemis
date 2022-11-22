import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { ConversationThreadSidebarComponent } from 'app/overview/course-conversations/layout/conversation-thread-sidebar/conversation-thread-sidebar.component';

describe('ConversationThreadSidebarComponent', () => {
    let component: ConversationThreadSidebarComponent;
    let fixture: ComponentFixture<ConversationThreadSidebarComponent>;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({ declarations: [ConversationThreadSidebarComponent] }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(ConversationThreadSidebarComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it.todo('should create');
});
