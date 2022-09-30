import { Component } from '@angular/core';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';
import { Post } from 'app/entities/metis/post.model';
import { MetisService } from 'app/shared/metis/metis.service';

@Component({
    selector: 'jhi-course-messages',
    templateUrl: './course-messages.component.html',
    providers: [MetisService],
})
export class CourseMessagesComponent {
    selectedConversation: Conversation;
    postInThread: Post;
    showPostThread = false;

    selectConversation(conversation: Conversation) {
        this.selectedConversation = conversation;
    }

    setPostInThread(post?: Post) {
        this.showPostThread = false;

        if (!!post) {
            this.postInThread = post;
            this.showPostThread = true;
        }
    }
}
