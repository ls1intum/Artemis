import { ChangeDetectionStrategy, Component, ElementRef, inject, input, output } from '@angular/core';
import { Post } from 'app/communication/shared/entities/post.model';
import dayjs from 'dayjs/esm';
import { PostComponent } from '../post/post.component';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { Posting } from 'app/communication/shared/entities/posting.model';
import { CourseWideSearchConfig } from 'app/communication/course-conversations-components/course-wide-search/course-wide-search.component';

@Component({
    selector: 'jhi-posting-thread',
    templateUrl: './posting-thread.component.html',
    styleUrls: ['../metis.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [PostComponent],
})
export class PostingThreadComponent {
    readonly lastReadDate = input<dayjs.Dayjs>();
    readonly readOnlyMode = input(false);
    readonly post = input.required<Post>();
    readonly showAnswers = input.required<boolean>();
    readonly isCommunicationPage = input<boolean | undefined>();
    readonly showChannelReference = input<boolean>();
    readonly hasChannelModerationRights = input(false);
    readonly openThread = output<Post>();
    readonly isConsecutive = input<boolean | undefined>(false);
    searchConfig = input<CourseWideSearchConfig | undefined>(undefined);
    forwardedPosts = input<(Post | undefined)[]>([]);
    forwardedAnswerPosts = input<(AnswerPost | undefined)[]>([]);

    readonly onNavigateToPost = output<Posting>();

    elementRef = inject(ElementRef);

    onTriggerNavigateToPost(post: Posting) {
        this.onNavigateToPost.emit(post);
    }
}
