import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-posting-thread',
    templateUrl: './posting-thread.component.html',
    styleUrls: ['../metis.component.scss'],
})
export class PostingThreadComponent {
    @Input() lastReadDate?: dayjs.Dayjs;
    @Input() readOnlyMode = false;
    @Input() post: Post;
    @Input() showAnswers: boolean;
    @Input() isCourseMessagesPage: boolean;
    @Input() isCommunicationPage?: boolean;
    @Input() hasChannelModerationRights = false;
    @Output() openThread = new EventEmitter<Post>();
}
