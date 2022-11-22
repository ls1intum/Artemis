import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { ConversationMembersComponent } from 'app/overview/course-conversations/dialogs/conversation-detail-dialog/tabs/conversation-members/conversation-members.component';

describe('ConversationMembersComponent', () => {
    let component: ConversationMembersComponent;
    let fixture: ComponentFixture<ConversationMembersComponent>;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({ declarations: [ConversationMembersComponent] }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(ConversationMembersComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it.todo('should create');
});
