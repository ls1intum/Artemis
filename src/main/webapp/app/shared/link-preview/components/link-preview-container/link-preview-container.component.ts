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
        links.forEach((link) => {
            this.linkPreviewService.fetchLink(link.href).subscribe({
                next: (linkPreview) => {
                    linkPreview.shouldPreviewBeShown = !!(linkPreview.url && linkPreview.title && linkPreview.description && linkPreview.image);
                    this.linkPreviews.push(linkPreview);
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
