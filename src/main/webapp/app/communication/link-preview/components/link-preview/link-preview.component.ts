import { Component, computed, inject, input } from '@angular/core';
import { ConfirmIconComponent } from 'app/shared/confirm-icon/confirm-icon.component';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { MetisService } from 'app/communication/service/metis.service';
import { Posting } from 'app/communication/shared/entities/posting.model';
import { NgClass } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { LinkPreview } from 'app/communication/link-preview/services/link-preview.service';
import { urlRegex } from 'app/communication/link-preview/services/linkify.service';

@Component({
    selector: 'jhi-link-preview',
    templateUrl: './link-preview.component.html',
    styleUrls: ['./link-preview.component.scss'],
    imports: [ConfirmIconComponent, NgClass, ArtemisTranslatePipe],
})
export class LinkPreviewComponent {
    private metisService = inject(MetisService);

    linkPreview = input<LinkPreview>();
    posting = input<Posting>();
    showLoadingsProgress = input(false);
    loaded = input(false);
    hasError = input(false);
    isReply = input(false);
    multiple = input(false);

    readonly isAuthorOfOriginalPost = computed(() => {
        const posting = this.posting();
        return posting ? this.metisService.metisUserIsAuthorOfPosting(posting) : false;
    });

    faTimes = faTimes;

    /**
     * Removes the link preview from the list of link previews
     *
     * @param {LinkPreview} linkPreview the link preview to be removed
     */
    removeLinkPreview(linkPreview: LinkPreview) {
        const urlToSearchFor = linkPreview.url;
        const posting = this.posting();

        if (posting) {
            // Find all URL matches in the text (in the content of the post)
            let match;
            let modifiedContent = posting.content!;
            while ((match = urlRegex.exec(modifiedContent)) !== null) {
                const url = match[0];
                const start = match.index;
                const end = start + url.length;

                if (url === urlToSearchFor || url.includes(urlToSearchFor)) {
                    // wrap the URL in <>
                    modifiedContent = modifiedContent.substring(0, start) + `<${url}>` + modifiedContent.substring(end);
                }
            }

            posting.content = modifiedContent;

            if (this.isReply()) {
                this.metisService.updateAnswerPost(posting).subscribe({
                    next: () => {},
                });
            } else {
                this.metisService.updatePost(posting).subscribe({
                    next: () => {},
                });
            }
        }
    }
}
