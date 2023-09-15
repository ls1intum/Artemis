import { Component, Input } from '@angular/core';
import { PostingContentPart, ReferenceType } from '../../metis.util';
import { FileService } from 'app/shared/http/file.service';
import { faBan, faChalkboardUser, faCheckDouble, faFile, faFileUpload, faFont, faKeyboard, faMessage, faPaperclip, faProjectDiagram } from '@fortawesome/free-solid-svg-icons';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { EnlargeSlideImageComponent } from 'app/shared/metis/posting-content/enlarge-slide-image/enlarge-slide-image-component';
import { MatDialog } from '@angular/material/dialog';

@Component({
    selector: 'jhi-posting-content-part',
    templateUrl: './posting-content-part.component.html',
    styleUrls: ['./../../metis.component.scss'],
})
export class PostingContentPartComponent {
    @Input() postingContentPart: PostingContentPart;

    imageNotFound = false;

    // Only allow certain html tags and attributes
    allowedHtmlTags: string[] = ['a', 'b', 'br', 'blockquote', 'code', 'del', 'em', 'i', 'ins', 'li', 'mark', 'ol', 'p', 'pre', 'small', 'span', 'strong', 'sub', 'sup', 'ul'];
    allowedHtmlAttributes: string[] = ['href'];

    // icons
    faFile = faFile;
    faBan = faBan;

    constructor(
        private fileService: FileService,
        private dialog: MatDialog,
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
            default:
                return faPaperclip;
        }
    }
}
