import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { LinkPreview, LinkPreviewService } from 'app/shared/link-preview/services/link-preview.service';
import { Link, LinkifyService } from 'app/shared/link-preview/services/linkify.service';
import { User } from 'app/core/user/user.model';
import { Posting } from 'app/entities/metis/posting.model';

@Component({
    selector: 'jhi-link-preview-container',
    templateUrl: './link-preview-container.component.html',
    styleUrls: ['./link-preview-container.component.scss'],
})
export class LinkPreviewContainerComponent implements OnInit, OnChanges {
    @Input() data: string | undefined;
    @Input() author?: User;
    @Input() posting?: Posting;
    @Input() isEdited?: boolean;
    @Input() isReply?: boolean;

    linkPreviews: LinkPreview[] = [];
    hasError: boolean;
    loaded = false;
    showLoadingsProgress = true;
    multiple = false;

    constructor(
        public linkPreviewService: LinkPreviewService,
        public linkifyService: LinkifyService,
    ) {}

    ngOnInit() {
        this.data = this.data ?? '';
        this.findPreviews();
    }

    ngOnChanges() {
        this.reloadLinkPreviews();
    }

    private reloadLinkPreviews() {
        this.loaded = false;
        this.showLoadingsProgress = true;
        this.linkPreviews = []; // Clear the existing link previews
        this.findPreviews();
    }

    private findPreviews() {
        const links: Link[] = this.linkifyService.find(this.data!);
        // TODO: The limit of 5 link previews should be configurable (maybe in course level)
        links
            .filter((link) => !link.isLinkPreviewRemoved)
            .slice(0, 5)
            .forEach((link) => {
                this.linkPreviewService.fetchLink(link.href).subscribe({
                    next: (linkPreview) => {
                        linkPreview.shouldPreviewBeShown = !!(linkPreview.url && linkPreview.title && linkPreview.description && linkPreview.image);

                        const existingLinkPreview = this.linkPreviews.find((preview) => preview.url === linkPreview.url);
                        if (existingLinkPreview) {
                            Object.assign(existingLinkPreview, linkPreview);
                        } else {
                            this.linkPreviews.push(linkPreview);
                        }

                        this.hasError = false;
                        this.loaded = true;
                        this.showLoadingsProgress = false;
                        this.multiple = this.linkPreviews.length > 1;
                    },
                });
            });
    }

    trackLinks(index: number, preview: LinkPreview) {
        return preview?.url;
    }
}
