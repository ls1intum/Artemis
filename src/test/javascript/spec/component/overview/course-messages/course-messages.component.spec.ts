import { CourseConversationsComponent } from 'app/overview/course-conversations/course-conversations.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { getElement } from '../../../helpers/utils/general.utils';

import { ConversationSelectionSidebarComponent } from 'app/overview/course-conversations/layout/conversation-selection-sidebar/conversation-selection-sidebar.component';
import { conversationBetweenUser1User2, directMessageUser1 } from '../../../helpers/sample/metis-sample-data';
import { ConversationMessagesComponent } from 'app/overview/course-conversations/layout/conversation-messages/conversation-messages.component';
import { ConversationThreadSidebarComponent } from 'app/overview/course-conversations/layout/conversation-thread-sidebar/conversation-thread-sidebar.component';
import { MockComponent } from 'ng-mocks';

describe('CourseMessagesComponent', () => {
    let fixture: ComponentFixture<CourseConversationsComponent>;
    let component: CourseConversationsComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [
                CourseConversationsComponent,
                MockComponent(ConversationSelectionSidebarComponent),
                MockComponent(ConversationMessagesComponent),
                MockComponent(ConversationThreadSidebarComponent),
            ],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseConversationsComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should trigger selectConversation function on selectConversation event', () => {
        const selectConversationSpy = jest.spyOn(component, 'onConversationSelected');

        const scrollableDiv = getElement(fixture.debugElement, 'jhi-conversation-sidebar');
        scrollableDiv.dispatchEvent(new Event('selectConversation'));

        expect(selectConversationSpy).toHaveBeenCalledOnce();
    });

    it('should update selected conversation correctly', () => {
        component.onConversationSelected(conversationBetweenUser1User2);
        expect(component.selectedConversation).toEqual(conversationBetweenUser1User2);
    });

    it('should not open thread sidebar on page load', () => {
        expect(component.showPostThread).toBeFalse();
        fixture.detectChanges();
        const conversationSidebarComponent = getElement(fixture.debugElement, 'jhi-thread-sidebar') as ConversationThreadSidebarComponent;
        expect(conversationSidebarComponent).toBeNull();
    });

    it('should display threadSidebarComponent', () => {
        component.onConversationSelected(conversationBetweenUser1User2);
        component.setPostInThread(directMessageUser1);

        fixture.detectChanges();

        expect(component.showPostThread).toBeTrue();

        fixture.detectChanges();

        const conversationSidebarComponent = getElement(fixture.debugElement, 'jhi-thread-sidebar') as ConversationThreadSidebarComponent;
        expect(conversationSidebarComponent).not.toBeNull();
    });

    it('should close threadSidebarComponent', () => {
        component.onConversationSelected(conversationBetweenUser1User2);
        component.setPostInThread(directMessageUser1);

        fixture.detectChanges();

        component.setPostInThread(undefined);
        expect(component.showPostThread).toBeFalse();

        fixture.detectChanges();

        const conversationSidebarComponent = getElement(fixture.debugElement, 'jhi-thread-sidebar') as ConversationThreadSidebarComponent;
        expect(conversationSidebarComponent).toBeNull();
    });
});
