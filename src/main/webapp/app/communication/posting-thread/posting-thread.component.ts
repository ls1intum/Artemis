import { ChangeDetectionStrategy, Component, ElementRef, EventEmitter, Input, Output, inject, input, output } from '@angular/core';
import { Post } from 'app/communication/shared/entities/post.model';
import dayjs from 'dayjs/esm';
import { Observable } from 'rxjs';
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
    @Input() lastReadDate?: dayjs.Dayjs;
    @Input() readOnlyMode = false;
    @Input() post: Post;
    @Input() showAnswers: boolean;
    @Input() isCommunicationPage: boolean;
    @Input() showChannelReference?: boolean;
    @Input() hasChannelModerationRights = false;
    @Output() openThread = new EventEmitter<Post>();
    @Input() isConsecutive: boolean | undefined = false;
    searchConfig = input<CourseWideSearchConfig | undefined>(undefined);
    forwardedPosts = input<(Post | undefined)[]>([]);
    forwardedAnswerPosts = input<(AnswerPost | undefined)[]>([]);

    readonly onNavigateToPost = output<Posting>();

    elementRef = inject(ElementRef);

    createAnswerOverride = input<((answerPost: AnswerPost) => Observable<AnswerPost>) | undefined>(undefined);

    onTriggerNavigateToPost(post: Posting) {
        this.onNavigateToPost.emit(post);
    }
}
