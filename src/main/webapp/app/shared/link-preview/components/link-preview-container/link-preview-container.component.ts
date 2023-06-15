import { Component, Input, OnInit } from '@angular/core';
import { LinkPreviewService } from 'app/shared/link-preview/service/link-preview.service';
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
    @Input() showLoadingsProgress = true;
    @Input() data: string | undefined;

    constructor(public linkPreviewService: LinkPreviewService, public linkifyService: LinkifyService) {}

    ngOnInit() {
        this.data = this.data ?? '';
        const links: Link[] = this.linkifyService.find(this.data!);
        this.linkPreviewService.onLinkFound.emit(links);
    }

    trackLinks(index: number, link: Link) {
        return link ? link.href : undefined;
    }
}
