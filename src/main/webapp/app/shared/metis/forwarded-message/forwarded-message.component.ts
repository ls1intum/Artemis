import { AfterViewInit, ChangeDetectorRef, Component, ElementRef, OnChanges, SimpleChanges, ViewChild, inject, input, output } from '@angular/core';
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
export class ForwardedMessageComponent implements OnChanges, AfterViewInit {
    readonly faShare = faShare;
    sourceName: string | undefined = '';
    originalPostDetails = input<Posting>();
    postingIsOfToday: boolean;
    todayFlag?: string;
    readonly onNavigateToPost = output<Posting>();
    @ViewChild('messageContent') messageContent!: ElementRef;
    isContentLong: boolean = false;
    showFullForwardedMessage: boolean = false;
    maxLines: number = 5;
    private cdr = inject(ChangeDetectorRef);

    ngOnChanges(changes: SimpleChanges): void {
        if (changes['originalPostDetails'] && this.originalPostDetails) {
            this.updateSourceName(this.originalPostDetails()!);
            this.postingIsOfToday = dayjs().isSame(this.originalPostDetails()!.creationDate, 'day');
            this.todayFlag = this.getTodayFlag();
        }
    }

    ngAfterViewInit(): void {
        setTimeout(() => {
            this.checkIfContentOverflows();
        }, 0);
    }

    toggleShowFullForwardedMessage(): void {
        this.showFullForwardedMessage = !this.showFullForwardedMessage;
    }

    displayedForwardedContent(): string {
        if (!this.originalPostDetails() || !this.originalPostDetails()?.content) {
            return '';
        }

        if (this.showFullForwardedMessage || !this.isContentLong) {
            return this.originalPostDetails()?.content || '';
        } else {
            const lines = this.originalPostDetails()?.content?.split('\n');
            return lines?.slice(0, this.maxLines).join('\n') + '...';
        }
    }

    checkIfContentOverflows(): void {
        if (this.messageContent) {
            const nativeElement = this.messageContent.nativeElement;
            this.isContentLong = nativeElement.scrollHeight > nativeElement.clientHeight;
            this.cdr.detectChanges();
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

    protected onTriggerNavigateToPost() {
        if (this.originalPostDetails() === undefined) {
            return;
        }
        this.onNavigateToPost.emit(this.originalPostDetails()!);
    }

    updateSourceName(post: Post | AnswerPost) {
        const conversation = 'post' in post ? post.post?.conversation : (post as Post).conversation;

        if (conversation?.type?.valueOf() === 'channel') {
            this.sourceName = (conversation as any)?.name ? `#${(conversation as any)?.name} |` : 'a thread in #unknown |';
        } else if (conversation?.type?.valueOf() === 'oneToOneChat') {
            this.sourceName = 'a direct message |';
        } else {
            this.sourceName = 'a group message |';
        }
    }
}
