import { Component, OnChanges, SimpleChanges, input } from '@angular/core';
import { faShare } from '@fortawesome/free-solid-svg-icons';
import { Post } from 'app/entities/metis/post.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Posting } from 'app/entities/metis/posting.model';
import dayjs from 'dayjs';

@Component({
    selector: 'jhi-forwarded-message',
    templateUrl: './forwarded-message.component.html',
    styleUrls: ['./forwarded-message.component.scss'],
})
export class ForwardedMessageComponent implements OnChanges {
    readonly faShare = faShare;
    sourceName: string | undefined = '';
    originalPostDetails = input<Posting>();
    postingIsOfToday: boolean;
    todayFlag?: string;

    ngOnChanges(changes: SimpleChanges): void {
        if (changes['originalPostDetails'] && this.originalPostDetails) {
            this.updateSourceName(this.originalPostDetails()!);
            this.postingIsOfToday = dayjs().isSame(this.originalPostDetails()!.creationDate, 'day');
            this.todayFlag = this.getTodayFlag();
        }
    }

    /**
     * sets a flag that replaces the date by "Today" in the posting's header if applicable
     */
    getTodayFlag(): string | undefined {
        if (this.postingIsOfToday) {
            return 'artemisApp.metis.today';
        } else {
            return undefined;
        }
    }

    updateSourceName(post: Post | AnswerPost) {
        let conversation = null;
        if ('post' in post) {
            conversation = post.post?.conversation;
            if (conversation?.type?.valueOf() == 'channel') {
                this.sourceName = 'a thread in #' + (conversation as any)?.name;
            } else if (conversation?.type?.valueOf() == 'oneToOneChat') {
                this.sourceName = 'a direct message';
            } else {
                this.sourceName = 'a group message';
            }
        } else {
            conversation = (post as Post).conversation;
            if (conversation?.type?.valueOf() == 'channel') {
                this.sourceName = '#' + (conversation as any)?.name;
            } else if (conversation?.type?.valueOf() == 'oneToOneChat') {
                this.sourceName = 'a direct message';
            } else {
                this.sourceName = 'a group message';
            }
        }
    }
}
