import { Component, inject, input, output, signal } from '@angular/core';
import { PostingContentPart, ReferenceType } from '../../communication.util';
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
import { DialogService } from 'primeng/dynamicdialog';
import { AccountService } from 'app/core/auth/account.service';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { FileService } from 'app/foundation/service/file.service';

/** Renders one reference (user, channel, post, attachment, ...) of a posting at the spot where the posting's Markdown placed it. */
@Component({
    selector: 'jhi-posting-content-part',
    templateUrl: './posting-content-part.component.html',
    styleUrls: ['../../communication.component.scss'],
    imports: [RouterLink, FaIconComponent, TranslateDirective],
    providers: [DialogService],
})
export class PostingContentPartComponent {
    private fileService = inject(FileService);
    private dialogService = inject(DialogService);
    private accountService = inject(AccountService);
    private translateService = inject(TranslateService);

    postingContentPart = input<PostingContentPart>();
    userReferenceClicked = output<string>();
    channelReferenceClicked = output<number>();

    readonly imageNotFound = signal(false);
    hasClickedUserReference = false;

    // icons
    protected readonly faFile = faFile;
    protected readonly faBan = faBan;
    protected readonly faAt = faAt;
    protected readonly faHashtag = faHashtag;
    protected readonly faQuestion = faQuestion;

    protected readonly ReferenceType = ReferenceType;

    /**
     * Opens an attachment with the given URL in a new window
     *
     * @param attachmentUrl URL of the attachment to be displayed
     */
    openAttachment(attachmentUrl: string): void {
        this.fileService.downloadFile(attachmentUrl);
    }

    toggleImageNotFound(): void {
        this.imageNotFound.set(true);
    }

    /**
     * Opens a dialog to display the image in full size
     *
     * @param slideToReference {string} the reference to the slide
     * @param imageAlt {string} optional description of the image used as its alt text (falls back to a localized label)
     */
    enlargeImage(slideToReference: string, imageAlt?: string) {
        this.dialogService.open(EnlargeSlideImageComponent, {
            // A translated header gives the dialog an accessible name and renders PrimeNG's themed close button, so the preview can always be dismissed.
            header: this.translateService.instant('artemisApp.communication.imagePreviewTitle'),
            // Prefer the image's own description (markdown alt text) so assistive technologies can announce the preview; fall back to a generic localized label.
            data: { slideToReference, imageAlt: imageAlt || this.translateService.instant('artemisApp.communication.imagePreviewAlt') },
            modal: true,
            // Without closable the DynamicDialog header renders no close button (PrimeNG defaults it to undefined), which left the preview stuck open.
            closable: true,
            dismissableMask: true,
            closeOnEscape: true,
            style: { 'max-width': '95vw' },
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

    protected canActivateUserReference(referenceUserLogin: string | undefined): boolean {
        return !this.hasClickedUserReference && !!referenceUserLogin && referenceUserLogin !== this.accountService.userIdentity()?.login;
    }

    /**
     * Emit an event if the clicked user reference is different from the current user
     *
     * @param referenceUserLogin login of the referenced user
     */
    onClickUserReference(referenceUserLogin: string | undefined) {
        if (referenceUserLogin && this.canActivateUserReference(referenceUserLogin)) {
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
