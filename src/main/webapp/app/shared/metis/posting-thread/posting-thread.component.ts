import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Post } from 'app/entities/metis/post.model';

@Component({
    selector: 'jhi-posting-thread',
    templateUrl: './posting-thread.component.html',
    styleUrls: ['../metis.component.scss'],
})
export class PostingThreadComponent {
    @Input() post: Post;
    @Input() showAnswers: boolean;
    @Input() isCourseMessagesPage: boolean;
    @Output() isModalOpen = new EventEmitter<void>();
}
