import { Component } from '@angular/core';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';

@Component({
    selector: 'jhi-course-messages',
    templateUrl: './course-messages.component.html',
})
export class CourseMessagesComponent {
    /**
     * currently selected conversation.
     */
    selectedConversation: Conversation;

    constructor() {}

    selectConversation(conversation: Conversation) {
        this.selectedConversation = conversation;
    }
}
