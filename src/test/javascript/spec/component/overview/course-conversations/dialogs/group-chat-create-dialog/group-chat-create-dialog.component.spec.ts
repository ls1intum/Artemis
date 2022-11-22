import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { GroupChatCreateDialogComponent } from 'app/overview/course-conversations/dialogs/group-chat-create-dialog/group-chat-create-dialog.component';

describe('GroupChatCreateDialogComponent', () => {
    let component: GroupChatCreateDialogComponent;
    let fixture: ComponentFixture<GroupChatCreateDialogComponent>;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({ declarations: [GroupChatCreateDialogComponent] }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(GroupChatCreateDialogComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it.todo('should create');
});
