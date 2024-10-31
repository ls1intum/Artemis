import { Posting } from 'app/entities/metis/posting.model';
import { ChangeDetectorRef, Directive, Input, OnDestroy, OnInit, inject } from '@angular/core';
import { MetisService } from 'app/shared/metis/metis.service';

@Directive()
export abstract class PostingDirective<T extends Posting> implements OnInit, OnDestroy {
    @Input() posting: T;
    @Input() isCommunicationPage: boolean;
    @Input() showChannelReference?: boolean;

    @Input() hasChannelModerationRights = false;
    @Input() isThreadSidebar: boolean;

    isAnswerPost = false;
    isDeleted = false;
    readonly timeToDeleteInSeconds = 6;
    deleteTimerInSeconds = 6;
    deleteTimer: NodeJS.Timeout | undefined;
    deleteInterval: NodeJS.Timeout | undefined;

    content?: string;

    protected metisService = inject(MetisService);
    protected changeDetector = inject(ChangeDetectorRef);

    ngOnInit(): void {
        this.content = this.posting.content;
    }

    ngOnDestroy(): void {
        if (this.deleteTimer !== undefined) {
            clearTimeout(this.deleteTimer);
        }

        if (this.deleteInterval !== undefined) {
            clearInterval(this.deleteInterval);
        }
    }

    onDeleteEvent(isDelete: boolean) {
        this.isDeleted = isDelete;

        if (this.deleteTimer !== undefined) {
            clearTimeout(this.deleteTimer);
        }

        if (this.deleteInterval !== undefined) {
            clearInterval(this.deleteInterval);
        }

        if (isDelete) {
            this.deleteTimerInSeconds = this.timeToDeleteInSeconds;

            this.deleteTimer = setTimeout(
                () => {
                    if (this.isAnswerPost) {
                        this.metisService.deleteAnswerPost(this.posting);
                    } else {
                        this.metisService.deletePost(this.posting);
                    }
                },
                // We add a tiny buffer to make it possible for the user to react a bit longer than the ui displays (+1000)
                this.deleteTimerInSeconds * 1000 + 1000,
            );

            this.deleteInterval = setInterval(() => {
                this.deleteTimerInSeconds = Math.max(0, this.deleteTimerInSeconds - 1);
                this.changeDetector.detectChanges();
            }, 1000);
        }
    }
}
