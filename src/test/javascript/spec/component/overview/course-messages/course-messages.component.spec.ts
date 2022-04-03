import { CourseMessagesComponent } from 'app/overview/course-messages/course-messages.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { getElement } from '../../../helpers/utils/general.utils';

import { ChatSessionSidebarComponent } from 'app/overview/course-messages/chat-sessions-sidebar/chat-session-sidebar.component';

describe('CourseMessagesComponent', () => {
    let fixture: ComponentFixture<CourseMessagesComponent>;
    let component: CourseMessagesComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [ChatSessionSidebarComponent],
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

    it('should initialize', () => {
        fixture.detectChanges();

        expect(component).not.toBeNull();
        expect(component.selectedChatSession).toBeUndefined();

        const postOverviewComponent = getElement(fixture.debugElement, 'jhi-post-overview');
        expect(postOverviewComponent.courseMessagesPageFlag).toBeTrue();
        expect(postOverviewComponent.activeChatSession).toBe(component.selectedChatSession);
    });

    it('should trigger selectedChatSession on selectChatSession event', () => {
        const saveExerciseWithoutReevaluationSpy = jest.spyOn(component, 'selectChatSession');

        const scrollableDiv = getElement(fixture.debugElement, 'jhi-chat-session-sidebar');
        scrollableDiv.dispatchEvent(new Event('selectChatSession'));

        fixture.detectChanges();

        expect(saveExerciseWithoutReevaluationSpy).toBeCalledTimes(1);
    });
});
