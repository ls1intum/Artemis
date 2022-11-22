import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { ConversationAddUsersDialogComponent } from 'app/overview/course-conversations/dialogs/conversation-add-users-dialog/conversation-add-users-dialog.component';

describe('ConversationAddUsersDialogComponent', () => {
    let component: ConversationAddUsersDialogComponent;
    let fixture: ComponentFixture<ConversationAddUsersDialogComponent>;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({ declarations: [ConversationAddUsersDialogComponent] }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(ConversationAddUsersDialogComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it.todo('should create');
});
