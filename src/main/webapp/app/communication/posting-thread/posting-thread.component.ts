import { ChangeDetectionStrategy, Component, ElementRef, EventEmitter, Input, Output, inject, input, output } from '@angular/core';
import { Post } from 'app/communication/shared/entities/post.model';
import dayjs from 'dayjs/esm';
import { PostComponent } from '../post/post.component';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { Posting } from 'app/communication/shared/entities/posting.model';

@Component({
    selector: 'jhi-posting-thread',
    templateUrl: './posting-thread.component.html',
    styleUrls: ['../metis.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [PostComponent],
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
    forwardedPosts = input<(Post | null)[]>([]);
    forwardedAnswerPosts = input<(AnswerPost | null)[]>([]);
    readonly onNavigateToPost = output<Posting>();

    elementRef = inject(ElementRef);

    onTriggerNavigateToPost(post: Posting) {
        this.onNavigateToPost.emit(post);
    }
}
