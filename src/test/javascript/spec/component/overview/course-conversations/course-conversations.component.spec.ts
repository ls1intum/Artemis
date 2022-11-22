import { CourseConversationsComponent } from 'app/overview/course-conversations/course-conversations.component';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';

describe('CourseConversationComponent', () => {
    let component: CourseConversationsComponent;
    let fixture: ComponentFixture<CourseConversationsComponent>;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({ declarations: [CourseConversationsComponent] }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(CourseConversationsComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it.todo('should create');
});
