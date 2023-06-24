import { Component, Input } from '@angular/core';
import { LinkPreview } from 'app/shared/link-preview/services/link-preview.service';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { MetisService } from 'app/shared/metis/metis.service';
import { User } from 'app/core/user/user.model';

@Component({
    selector: 'jhi-link-preview',
    templateUrl: './link-preview.component.html',
    styleUrls: ['./link-preview.component.scss'],
})
export class LinkPreviewComponent {
    @Input() linkPreview: LinkPreview;

    // forwarded from the container
    @Input() color = 'primary'; // accent | warn
    @Input() showLoadingsProgress: boolean;

    @Input() loaded: boolean;
    @Input() hasError: boolean;
    @Input() author?: User;

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
        this.linkPreview.shouldPreviewBeShown = false; // TODO: cant do this because it removes all same in all messages because of the cache

        // TODO: add the logic to append <!-- link-preview-removed --> to the right hand side of the link intended to be removed
    }
}
