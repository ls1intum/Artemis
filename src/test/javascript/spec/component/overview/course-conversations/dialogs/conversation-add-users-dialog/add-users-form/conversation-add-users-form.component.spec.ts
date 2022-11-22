import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { ConversationAddUsersFormComponent } from 'app/overview/course-conversations/dialogs/conversation-add-users-dialog/add-users-form/conversation-add-users-form.component';

describe('ConversationAddUsersFormComponent', () => {
    let component: ConversationAddUsersFormComponent;
    let fixture: ComponentFixture<ConversationAddUsersFormComponent>;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({ declarations: [ConversationAddUsersFormComponent] }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(ConversationAddUsersFormComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it.todo('should create');
});
