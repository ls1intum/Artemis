import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { ConversationSelectionSidebarComponent } from 'app/overview/course-conversations/layout/conversation-selection-sidebar/conversation-selection-sidebar.component';

describe('ConversationSelectionSidebarComponent', () => {
    let component: ConversationSelectionSidebarComponent;
    let fixture: ComponentFixture<ConversationSelectionSidebarComponent>;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({ declarations: [ConversationSelectionSidebarComponent] }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(ConversationSelectionSidebarComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it.todo('should create');
});
