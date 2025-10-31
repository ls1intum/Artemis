import { Component, OnChanges, OnInit, inject, input, output } from '@angular/core';
import { PostingContentPart, ReferenceType } from '../../metis.util';
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
import { EnlargeSlideImageComponent } from 'app/communication/posting-content/enlarge-slide-image/enlarge-slide-image.component';
import { MatDialog } from '@angular/material/dialog';
import { AccountService } from 'app/core/auth/account.service';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { HtmlForPostingMarkdownPipe } from 'app/shared/pipes/html-for-posting-markdown.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FileService } from 'app/shared/service/file.service';

@Component({
    selector: 'jhi-posting-content-part',
    templateUrl: './posting-content-part.component.html',
    styleUrls: ['../../metis.component.scss'],
    imports: [RouterLink, FaIconComponent, HtmlForPostingMarkdownPipe, TranslateDirective],
})
export class PostingContentPartComponent implements OnInit, OnChanges {
    private fileService = inject(FileService);
    private dialog = inject(MatDialog);
    private accountService = inject(AccountService);

    postingContentPart = input<PostingContentPart>();
    userReferenceClicked = output<string>();
    channelReferenceClicked = output<number>();

    imageNotFound = false;
    hasClickedUserReference = false;

    // Only allow certain html tags and attributes

    allowedHtmlTags: string[] = [
        'a',
        'b',
        'br',
        'blockquote',
        'code',
        'del',
        'em',
        'hr',
        'h1',
        'h2',
        'h3',
        'h4',
        'h5',
        'h6',
        'i',
        'ins',
        'li',
        'mark',
        'p',
        'pre',
        'small',
        's',
        'span',
        'strong',
        'sub',
        'sup',
        'ul',
        'ol',
    ];
    allowedHtmlAttributes: string[] = ['href'];

    // icons
    protected readonly faFile = faFile;
    protected readonly faBan = faBan;
    protected readonly faAt = faAt;
    protected readonly faHashtag = faHashtag;
    protected readonly faQuestion = faQuestion;

    protected readonly ReferenceType = ReferenceType;
    processedContentBeforeReference: string;
    processedContentAfterReference: string;

    ngOnInit() {
        this.processContent();
    }

    ngOnChanges() {
        this.processContent();
    }

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
     * Processes content before and after any reference (user/channel/etc.)
     */
    processContent() {
        if (this.postingContentPart()?.contentBeforeReference) {
            this.processedContentBeforeReference = this.normalizeSpacing(this.postingContentPart()?.contentBeforeReference || '');
        }

        if (this.postingContentPart()?.contentAfterReference) {
            this.processedContentAfterReference = this.normalizeSpacing(this.postingContentPart()?.contentAfterReference || '');
        }
    }

    normalizeSpacing(content: string): string {
        return content.replace(/\n{3,}/g, '\n\n');
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
        if (!this.hasClickedUserReference && referenceUserLogin && referenceUserLogin !== this.accountService.userIdentity()?.login) {
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
