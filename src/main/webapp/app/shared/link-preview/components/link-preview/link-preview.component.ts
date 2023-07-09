import { Component, Input, OnInit } from '@angular/core';
import { LinkPreview } from 'app/shared/link-preview/services/link-preview.service';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { MetisService } from 'app/shared/metis/metis.service';
import { Posting } from 'app/entities/metis/posting.model';

@Component({
    selector: 'jhi-link-preview',
    templateUrl: './link-preview.component.html',
    styleUrls: ['./link-preview.component.scss'],
})
export class LinkPreviewComponent implements OnInit {
    @Input() linkPreview: LinkPreview;
    @Input() showLoadingsProgress: boolean;
    @Input() loaded: boolean;
    @Input() hasError: boolean;
    @Input() posting?: Posting;
    @Input() isReply?: boolean;

    isAuthorOfOriginalPost: boolean;

    faTimes = faTimes;

    constructor(private metisService: MetisService) {}

    ngOnInit() {
        // determines if the current user is the author of the original post, that the answer belongs to
        this.isAuthorOfOriginalPost = this.metisService.metisUserIsAuthorOfPosting(this.posting!);
    }

    /**
     * Removes the link preview from the list of link previews
     *
     * @param {LinkPreview} linkPreview the link preview to be removed
     */
    removeLinkPreview(linkPreview: LinkPreview) {
        const urlToSearchFor = linkPreview.url;

        // Regular expression pattern to match URLs
        // eslint-disable-next-line no-useless-escape
        const urlRegex = /https?:\/\/[^\s/$.?#>][^\s>]*?(?=\s|[\]\)]|$)/g;

        // const urlRegex = /https?:\/\/[^\s/$.?#].[^\s]*?(?=\s|[\]\)]|$)(?!>)/g;

        if (this.posting) {
            // Find all URL matches in the text (in the content of the post)
            let match;
            let modifiedContent = this.posting.content!;
            while ((match = urlRegex.exec(modifiedContent)) !== null) {
                const url = match[0];
                const start = match.index;
                const end = start + url.length;

                if (url === urlToSearchFor || url.includes(urlToSearchFor)) {
                    // wrap the URL in <>
                    modifiedContent = modifiedContent.substring(0, start) + `<${url}>` + modifiedContent.substring(end);
                }
            }

            this.posting.content = modifiedContent;

            if (this.isReply) {
                this.metisService.updateAnswerPost(this.posting).subscribe({
                    next: () => {},
                });
            } else {
                this.metisService.updatePost(this.posting).subscribe({
                    next: () => {},
                });
            }
        }
    }
}
