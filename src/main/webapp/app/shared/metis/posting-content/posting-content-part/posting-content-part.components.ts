import { Component, EventEmitter, Input, Output } from '@angular/core';
import { PostingContentPart, ReferenceType } from '../../metis.util';
import { FileService } from 'app/shared/http/file.service';

import {
    faAt,
    faBan,
    faChalkboardUser,
    faCheckDouble,
    faFile,
    faFileUpload,
    faFont,
    faHashtag,
    faKeyboard,
    faMessage,
    faPaperclip,
    faProjectDiagram,
    faQuestion,
} from '@fortawesome/free-solid-svg-icons';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { EnlargeSlideImageComponent } from 'app/shared/metis/posting-content/enlarge-slide-image/enlarge-slide-image.component';
import { MatDialog } from '@angular/material/dialog';
import { AccountService } from 'app/core/auth/account.service';

@Component({
    selector: 'jhi-posting-content-part',
    templateUrl: './posting-content-part.component.html',
    styleUrls: ['./../../metis.component.scss'],
})
export class PostingContentPartComponent {
    @Input() postingContentPart: PostingContentPart;
    @Output() userReferenceClicked = new EventEmitter<string>();
    @Output() channelReferenceClicked = new EventEmitter<number>();

    imageNotFound = false;
    hasClickedUserReference = false;

    // Only allow certain html tags and attributes
    allowedHtmlTags: string[] = ['a', 'b', 'br', 'blockquote', 'code', 'del', 'em', 'i', 'ins', 'li', 'mark', 'ol', 'p', 'pre', 'small', 'span', 'strong', 'sub', 'sup', 'ul'];
    allowedHtmlAttributes: string[] = ['href'];

    // icons
    protected readonly faFile = faFile;
    protected readonly faBan = faBan;
    protected readonly faAt = faAt;
    protected readonly faHashtag = faHashtag;
    protected readonly faQuestion = faQuestion;

    protected readonly ReferenceType = ReferenceType;

    constructor(
        private fileService: FileService,
        private dialog: MatDialog,
        private accountService: AccountService,
    ) {}

    /**
     * Opens an attachment with the given URL in a new window
     *
     * @param attachmentUrl URL of the attachment to be displayed
     */
    openAttachment(attachmentUrl: string): void {
        this.fileService.downloadFile(attachmentUrl);
    }

    toggleImageNotFound(): void {
        this.imageNotFound = true;
    }

    /**
     * Opens a dialog to display the image in full size
     *
     * @param slideToReference {string} the reference to the slide
     */
    enlargeImage(slideToReference: string) {
        this.dialog.open(EnlargeSlideImageComponent, {
            data: { slideToReference },
            maxWidth: '95vw',
        });
    }

    /**
     * Get an icon for the type of the given exercise reference.
     * @param reference {ReferenceType}
     */
    referenceIcon(reference: ReferenceType): IconProp {
        switch (reference) {
            case ReferenceType.POST:
                return faMessage;
            case ReferenceType.LECTURE:
                return faChalkboardUser;
            case ReferenceType.PROGRAMMING:
                return faKeyboard;
            case ReferenceType.MODELING:
                return faProjectDiagram;
            case ReferenceType.QUIZ:
                return faCheckDouble;
            case ReferenceType.TEXT:
                return faFont;
            case ReferenceType.FILE_UPLOAD:
                return faFileUpload;
            case ReferenceType.SLIDE:
                return faFile;
            case ReferenceType.FAQ:
                return faQuestion;
            default:
                return faPaperclip;
        }
    }

    /**
     * Emit an event if the clicked user reference is different from the current user
     *
     * @param referenceUserLogin login of the referenced user
     */
    onClickUserReference(referenceUserLogin: string | undefined) {
        if (!this.hasClickedUserReference && referenceUserLogin && referenceUserLogin !== this.accountService.userIdentity?.login) {
            this.hasClickedUserReference = true;
            this.userReferenceClicked.emit(referenceUserLogin);
        }
    }

    /**
     * Emit an event if the clicked channel reference is clicked
     *
     * @param channelId login of the referenced user
     */
    onClickChannelReference(channelId: number | undefined) {
        if (channelId) {
            this.channelReferenceClicked.emit(channelId);
        }
    }
}
