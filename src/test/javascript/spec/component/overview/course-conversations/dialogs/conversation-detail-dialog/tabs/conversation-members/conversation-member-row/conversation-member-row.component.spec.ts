import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { ConversationMemberRowComponent } from 'app/overview/course-conversations/dialogs/conversation-detail-dialog/tabs/conversation-members/conversation-member-row/conversation-member-row.component';

describe('ConversationMemberRowComponent', () => {
    let component: ConversationMemberRowComponent;
    let fixture: ComponentFixture<ConversationMemberRowComponent>;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({ declarations: [ConversationMemberRowComponent] }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(ConversationMemberRowComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it.todo('should create');
});
