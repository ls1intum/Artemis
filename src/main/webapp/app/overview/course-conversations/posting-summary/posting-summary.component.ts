import { Component, effect, input, output } from '@angular/core';
import { Posting, PostingType, SavedPostStatus } from 'app/entities/metis/posting.model';
import { faBookmark, faBoxArchive, faCheckSquare, faEllipsis, faHashtag, faLock } from '@fortawesome/free-solid-svg-icons';
import { ConversationType } from 'app/entities/metis/conversation/conversation.model';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-posting-summary',
    templateUrl: './posting-summary.component.html',
    styleUrls: ['./posting-summary.component.scss'],
})
export class PostingSummaryComponent {
    readonly post = input<Posting>();
    readonly isShowSavedPostOptions = input<boolean>(false);

    readonly onChangeSavedPostStatus = output<SavedPostStatus>();

    protected readonly ConversationType = ConversationType;
    protected readonly SavedPostStatus = SavedPostStatus;

    protected isAnswerPost = false;
    protected postingIsOfToday = false;

    // Icons
    readonly faLock = faLock;
    readonly faHashtag = faHashtag;
    readonly faCheckSquare = faCheckSquare;
    readonly faBookmark = faBookmark;
    readonly faBoxArchive = faBoxArchive;
    readonly faEllipsis = faEllipsis;

    constructor() {
        effect(() => {
            this.isAnswerPost = this.post()?.postingType === PostingType.ANSWER.toString();
            if (this.post()) {
                this.postingIsOfToday = dayjs().isSame(this.post()!.creationDate, 'day');
            }
        });
    }

    protected onStatusChangeClick(status: SavedPostStatus) {
        this.onChangeSavedPostStatus.emit(status);
    }

    protected readonly PostingType = PostingType;
}
