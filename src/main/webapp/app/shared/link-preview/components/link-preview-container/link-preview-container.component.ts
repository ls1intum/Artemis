import { Component, Input, OnInit } from '@angular/core';
import { LinkPreview, LinkPreviewService } from 'app/shared/link-preview/service/link-preview.service';
import { Link } from 'app/shared/link-preview/linkify/interfaces/linkify.interface';
import { LinkifyService } from 'app/shared/link-preview/linkify/services/linkify.service';

@Component({
    selector: 'jhi-link-preview-container',
    templateUrl: './link-preview-container.component.html',
    styleUrls: ['./link-preview-container.component.scss'],
})
export class LinkPreviewContainerComponent implements OnInit {
    // to forward
    @Input() color = 'primary'; // accent | warn
    @Input() multiple: boolean;
    @Input() data: string | undefined;

    linkPreviews: LinkPreview[] = [];
    hasError: boolean;
    loaded = false;
    showLoadingsProgress = true;

    constructor(public linkPreviewService: LinkPreviewService, public linkifyService: LinkifyService) {}

    ngOnInit() {
        this.data = this.data ?? '';
        const links: Link[] = this.linkifyService.find(this.data!);
        // TODO: The limit of 5 link previews should be configurable
        links
            .slice(0, 5) // limit to 5 links
            .forEach((link) => {
                this.linkPreviewService.fetchLink(link.href).subscribe({
                    next: (linkPreview) => {
                        linkPreview.shouldPreviewBeShown = !!(linkPreview.url && linkPreview.title && linkPreview.description && linkPreview.image);

                        // Check if a link preview for the current link already exists
                        const existingLinkPreview = this.linkPreviews.find((preview) => preview.url === linkPreview.url);
                        if (existingLinkPreview) {
                            // Update the existing link preview instead of pushing a new one
                            Object.assign(existingLinkPreview, linkPreview);
                        } else {
                            this.linkPreviews.push(linkPreview);
                        }

                        this.hasError = false;
                        this.loaded = true;
                        this.showLoadingsProgress = false;
                    },
                });
            });
    }

    trackLinks(index: number, preview: LinkPreview) {
        return preview ? preview.url : undefined;
    }
}
