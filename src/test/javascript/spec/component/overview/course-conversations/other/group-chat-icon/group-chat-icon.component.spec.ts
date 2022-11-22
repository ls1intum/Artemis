import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { GroupChatIconComponent } from 'app/overview/course-conversations/other/group-chat-icon/group-chat-icon.component';

describe('GroupChatIconComponent', () => {
    let component: GroupChatIconComponent;
    let fixture: ComponentFixture<GroupChatIconComponent>;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({ declarations: [GroupChatIconComponent] }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(GroupChatIconComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it.todo('should create');
});
