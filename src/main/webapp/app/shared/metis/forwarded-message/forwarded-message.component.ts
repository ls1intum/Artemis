import { AfterViewInit, ChangeDetectorRef, Component, ElementRef, OnChanges, OnInit, SimpleChanges, ViewChild, inject, input, output } from '@angular/core';
import { faShare } from '@fortawesome/free-solid-svg-icons';
import { Post } from 'app/entities/metis/post.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Posting } from 'app/entities/metis/posting.model';
import dayjs from 'dayjs';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';

@Component({
    selector: 'jhi-forwarded-message',
    templateUrl: './forwarded-message.component.html',
    styleUrls: ['./forwarded-message.component.scss'],
})
export class ForwardedMessageComponent implements OnChanges, AfterViewInit, OnInit {
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
    private conversation: Conversation | undefined;
    private isAnswerPost: boolean = false;
    protected viewButtonVisible: boolean = false;

    ngOnInit() {
        this.isAnswerPost = 'post' in this.originalPostDetails()!;
        this.conversation = this.isAnswerPost ? (this.originalPostDetails() as AnswerPost).post?.conversation : (this.originalPostDetails() as Post).conversation;
        this.updateSourceName();
        this.isChannel();
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes['originalPostDetails'] && this.originalPostDetails) {
            this.updateSourceName();
            this.postingIsOfToday = dayjs().isSame(this.originalPostDetails()!.creationDate, 'day');
            this.todayFlag = this.getTodayFlag();
            this.isChannel();
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

    isChannel() {
        if (this.conversation?.type?.valueOf() === 'channel') {
            this.viewButtonVisible = true;
        }
    }

    onTriggerNavigateToPost() {
        if (this.originalPostDetails() === undefined) {
            return;
        }
        this.onNavigateToPost.emit(this.originalPostDetails()!);
    }

    updateSourceName() {
        if (this.conversation?.type?.valueOf() === 'channel') {
            if (this.isAnswerPost) {
                this.sourceName = (this.conversation as any)?.name ? `a thread in #${(this.conversation as any)?.name} |` : 'a thread in #unknown |';
            } else {
                this.sourceName = (this.conversation as any)?.name ? `#${(this.conversation as any)?.name} |` : '#unknown |';
            }
        } else if (this.conversation?.type?.valueOf() === 'oneToOneChat') {
            this.sourceName = this.isAnswerPost ? 'a thread in a direct message ' : 'a direct message ';
        } else {
            this.sourceName = this.isAnswerPost ? 'a thread in a group message ' : 'a group message ';
        }
    }
}
