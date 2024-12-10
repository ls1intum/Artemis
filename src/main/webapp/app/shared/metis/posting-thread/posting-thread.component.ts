import { ChangeDetectionStrategy, Component, ElementRef, EventEmitter, Input, Output, inject, output } from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import dayjs from 'dayjs/esm';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Posting } from 'app/entities/metis/posting.model';

@Component({
    selector: 'jhi-posting-thread',
    templateUrl: './posting-thread.component.html',
    styleUrls: ['../metis.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PostingThreadComponent {
    @Input() lastReadDate?: dayjs.Dayjs;
    @Input() readOnlyMode = false;
    @Input() post: Post;
    @Input() showAnswers: boolean;
    @Input() isCommunicationPage: boolean;
    @Input() showChannelReference?: boolean;
    @Input() hasChannelModerationRights = false;
    @Output() openThread = new EventEmitter<Post>();
    @Input() isConsecutive: boolean | undefined = false;
    @Input() forwardedPosts: Post[] | undefined = [];
    @Input() forwardedAnswerPosts: AnswerPost[] | undefined = [];
    readonly onNavigateToPost = output<Posting>();

    elementRef = inject(ElementRef);

    protected onTriggerNavigateToPost(post: Posting) {
        this.onNavigateToPost.emit(post);
    }
}
