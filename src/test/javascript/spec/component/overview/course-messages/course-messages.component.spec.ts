import { CourseMessagesComponent } from 'app/overview/course-messages/course-messages.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { getElement } from '../../../helpers/utils/general.utils';

import { ConversationSidebarComponent } from 'app/overview/course-messages/conversation-sidebar/conversation-sidebar.component';

describe('CourseMessagesComponent', () => {
    let fixture: ComponentFixture<CourseMessagesComponent>;
    let component: CourseMessagesComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [ConversationSidebarComponent],
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

    it('should trigger selectedConversation on selectConversation event', () => {
        const selectConversationSpy = jest.spyOn(component, 'selectConversation');

        const scrollableDiv = getElement(fixture.debugElement, 'jhi-conversation-sidebar');
        scrollableDiv.dispatchEvent(new Event('selectConversation'));

        fixture.detectChanges();

        expect(selectConversationSpy).toHaveBeenCalledOnce();
    });
});
