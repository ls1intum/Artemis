import { Component } from '@angular/core';
import { ChatSession } from 'app/entities/metis/chat.session/chat-session.model';

@Component({
    selector: 'jhi-messages',
    styleUrls: ['./course-messages.component.scss', '../discussion-section/discussion-section.component.scss'],
    templateUrl: './course-messages.component.html',
})
export class CourseMessagesComponent {
    /**
     * Index of the currently selected chatSession.
     */
    selectedChatSession: ChatSession;

    constructor() {}

    selectChatSession(chatSession: ChatSession) {
        this.selectedChatSession = chatSession;
    }
}
