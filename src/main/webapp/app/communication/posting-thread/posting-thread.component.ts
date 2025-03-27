import { ChangeDetectionStrategy, Component, ElementRef, inject, input, output } from '@angular/core';
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
    lastReadDate = input<dayjs.Dayjs | undefined>(undefined);
    readOnlyMode = input<boolean>(false);
    post = input.required<Post>();
    showAnswers = input.required<boolean>();
    isCommunicationPage = input.required<boolean>();
    showChannelReference = input<boolean | undefined>(undefined);
    hasChannelModerationRights = input<boolean>(false);
    readonly openThread = output<Post>();
    isConsecutive = input<boolean | undefined>(false);
    searchQuery = input<string | undefined>(undefined);
    forwardedPosts = input<Post[]>([]);
    forwardedAnswerPosts = input<AnswerPost[]>([]);
    readonly onNavigateToPost = output<Posting>();

    elementRef = inject(ElementRef);

    onTriggerNavigateToPost(post: Posting) {
        this.onNavigateToPost.emit(post);
    }
}
