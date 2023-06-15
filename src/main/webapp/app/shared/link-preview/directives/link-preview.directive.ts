import { Directive, Input, OnInit } from '@angular/core';

import { LinkPreviewService } from 'app/shared/link-preview/service/link-preview.service';
import { LinkifyService } from 'app/shared/link-preview/linkify/services/linkify.service';
import { Link } from 'app/shared/link-preview/linkify/interfaces/linkify.interface';

@Directive({
    // eslint-disable-next-line @angular-eslint/directive-selector
    selector: '[linkPreview]',
    exportAs: '[linkPreview]',
})
export class LinkPreviewDirective implements OnInit {
    @Input() data: string | undefined;
    constructor(public linkifyService: LinkifyService, public linkPreviewService: LinkPreviewService) {}

    ngOnInit(): void {
        this.data = this.data ?? '';
        const links: Link[] = this.linkifyService.find(this.data!);
        this.linkPreviewService.onLinkFound.emit(links);
    }
}
