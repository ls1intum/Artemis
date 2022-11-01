import { CourseMessagesComponent } from 'app/overview/course-messages/course-messages.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { getElement } from '../../../helpers/utils/general.utils';

import { ConversationSidebarComponent } from 'app/overview/course-messages/conversation-sidebar/conversation-sidebar.component';
import { conversationBetweenUser1User2, directMessageUser1 } from '../../../helpers/sample/metis-sample-data';
import { MessagesComponent } from 'app/overview/course-messages/messages/messages.component';
import { ThreadSidebarComponent } from 'app/overview/course-messages/thread-sidebar/thread-sidebar.component';
import { MockComponent } from 'ng-mocks';

describe('CourseMessagesComponent', () => {
    let fixture: ComponentFixture<CourseMessagesComponent>;
    let component: CourseMessagesComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [CourseMessagesComponent, MockComponent(ConversationSidebarComponent), MockComponent(MessagesComponent), MockComponent(ThreadSidebarComponent)],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseMessagesComponent);
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
        const conversationSidebarComponent = getElement(fixture.debugElement, 'jhi-thread-sidebar') as ThreadSidebarComponent;
        expect(conversationSidebarComponent).toBeNull();
    });

    it('should display threadSidebarComponent', () => {
        component.onConversationSelected(conversationBetweenUser1User2);
        component.setPostInThread(directMessageUser1);

        fixture.detectChanges();

        expect(component.showPostThread).toBeTrue();

        fixture.detectChanges();

        const conversationSidebarComponent = getElement(fixture.debugElement, 'jhi-thread-sidebar') as ThreadSidebarComponent;
        expect(conversationSidebarComponent).not.toBeNull();
    });

    it('should close threadSidebarComponent', () => {
        component.onConversationSelected(conversationBetweenUser1User2);
        component.setPostInThread(directMessageUser1);

        fixture.detectChanges();

        component.setPostInThread(undefined);
        expect(component.showPostThread).toBeFalse();

        fixture.detectChanges();

        const conversationSidebarComponent = getElement(fixture.debugElement, 'jhi-thread-sidebar') as ThreadSidebarComponent;
        expect(conversationSidebarComponent).toBeNull();
    });
});
