import { Component, effect, input, output, signal, untracked } from '@angular/core';
import { Posting, PostingType, SavedPostStatus } from 'app/communication/shared/entities/posting.model';
import { faBarsProgress, faBookmark, faBoxArchive, faCheckSquare, faEllipsis, faHashtag, faLock } from '@fortawesome/free-solid-svg-icons';
import { ConversationType } from 'app/communication/shared/entities/conversation/conversation.model';
import dayjs from 'dayjs/esm';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { NgClass } from '@angular/common';
import { ProfilePictureComponent } from 'app/shared-ui/profile-picture/profile-picture.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { PostingContentComponent } from 'app/communication/posting-content/posting-content.components';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { addPublicFilePrefix } from 'app/app.constants';

@Component({
    selector: 'jhi-posting-summary',
    templateUrl: './posting-summary.component.html',
    styleUrls: ['./posting-summary.component.scss'],
    imports: [FaIconComponent, TranslateDirective, NgClass, ProfilePictureComponent, NgbTooltip, PostingContentComponent, ArtemisDatePipe, ArtemisTranslatePipe],
})
export class PostingSummaryComponent {
    post = input<Posting>();
    isShowSavedPostOptions = input<boolean>(false);

    readonly onChangeSavedPostStatus = output<SavedPostStatus>();
    readonly onRemoveBookmark = output<Posting>();
    readonly onNavigateToPost = output<Posting>();

    protected readonly ConversationType = ConversationType;
    protected readonly SavedPostStatus = SavedPostStatus;

    protected readonly isAnswerPost = signal(false);
    protected readonly postingIsOfToday = signal(false);
    protected readonly isShowPosting = signal(false);
    protected readonly isShowSummary = signal(false);
    protected readonly isShowContent = signal(false);

    // Icons
    readonly faLock = faLock;
    readonly faHashtag = faHashtag;
    readonly faCheckSquare = faCheckSquare;
    readonly faBookmark = faBookmark;
    readonly faBoxArchive = faBoxArchive;
    readonly faEllipsis = faEllipsis;
    readonly faBarsProgress = faBarsProgress;

    constructor() {
        effect(() => {
            const post = this.post();
            untracked(() => {
                const isShowPosting = post !== undefined;
                this.isShowPosting.set(isShowPosting);
                this.isShowSummary.set(isShowPosting && post!.conversation !== undefined && post!.conversation!.type !== undefined && post!.conversation!.title !== undefined);
                this.isShowContent.set(isShowPosting && post!.author !== undefined && post!.content !== undefined && post!.postingType !== undefined);
                this.isAnswerPost.set(post?.postingType === PostingType.ANSWER.valueOf());
                if (post) {
                    this.postingIsOfToday.set(dayjs().isSame(post!.creationDate, 'day'));
                }
            });
        });
    }

    protected onStatusChangeClick(event: MouseEvent, status: SavedPostStatus) {
        event.stopPropagation();
        this.onChangeSavedPostStatus.emit(status);
    }

    protected onRemoveBookmarkClick(event: MouseEvent) {
        event.stopPropagation();
        if (this.post() === undefined) {
            return;
        }
        this.onRemoveBookmark.emit(this.post()!);
    }

    protected onTriggerNavigateToPost() {
        if (this.post() === undefined) {
            return;
        }
        this.onNavigateToPost.emit(this.post()!);
    }

    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}
