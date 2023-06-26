import { Component, Input, OnInit } from '@angular/core';
import { LinkPreview } from 'app/shared/link-preview/services/link-preview.service';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { MetisService } from 'app/shared/metis/metis.service';
import { User } from 'app/core/user/user.model';
import { Post } from 'app/entities/metis/post.model';

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
    @Input() author?: User;
    @Input() posting?: Post;

    isAuthorOfOriginalPost: boolean;

    faTimes = faTimes;

    constructor(private metisService: MetisService) {}

    ngOnInit() {
        // determines if the current user is the author of the original post, that the answer belongs to
        this.isAuthorOfOriginalPost = this.metisService.metisUserIsAuthor(this.author!);
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
        const urlRegex = /https?:\/\/[^\s/$.?#].[^\s]*?(?=\s|[\]\)]|$)/g;

        if (this.posting) {
            // Find all URL matches in the text (in the content of the post)
            let match;
            let modifiedContent = this.posting.content!;
            while ((match = urlRegex.exec(modifiedContent)) !== null) {
                const url = match[0];
                const start = match.index;
                const end = start + url.length;

                if (url === urlToSearchFor) {
                    // Append <!-- LinkPreviewRemoved --> after the URL ends, plus 3 more characters
                    const modifiedUrl = '<!--LinkPreviewRemoved-->';
                    modifiedContent = modifiedContent.substring(0, end) + ' ' + modifiedUrl + modifiedContent.substring(end + url.length);
                }
            }

            // Update the posting content with modified content
            this.posting.content = modifiedContent;

            // Call the service to update the posting
            this.metisService.updatePost(this.posting).subscribe({
                next: () => {
                    // Update any necessary UI states or variables
                    // ...
                    console.log('Link preview removed successfully');
                    this.linkPreview.shouldPreviewBeShown = false;
                    //todo: somehow reload the link preview container component or communicate througth websocket
                },
            });
        }
    }
}
