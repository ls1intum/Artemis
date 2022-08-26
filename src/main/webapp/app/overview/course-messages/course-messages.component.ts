import { Component } from '@angular/core';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';
import { Post } from 'app/entities/metis/post.model';

@Component({
    selector: 'jhi-course-messages',
    templateUrl: './course-messages.component.html',
})
export class CourseMessagesComponent {
    selectedConversation: Conversation;
    postInThread: Post;

    constructor() {}

    selectConversation(conversation: Conversation) {
        this.selectedConversation = conversation;
    }
}
