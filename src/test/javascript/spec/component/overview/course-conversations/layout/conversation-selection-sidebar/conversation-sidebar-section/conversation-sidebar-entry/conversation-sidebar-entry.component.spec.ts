import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { ConversationSidebarEntryComponent } from 'app/overview/course-conversations/layout/conversation-selection-sidebar/conversation-sidebar-section/conversation-sidebar-entry/conversation-sidebar-entry.component';

describe('ConversationSidebarEntryComponent', () => {
    let component: ConversationSidebarEntryComponent;
    let fixture: ComponentFixture<ConversationSidebarEntryComponent>;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({ declarations: [ConversationSidebarEntryComponent] }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(ConversationSidebarEntryComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it.todo('should create');
});
