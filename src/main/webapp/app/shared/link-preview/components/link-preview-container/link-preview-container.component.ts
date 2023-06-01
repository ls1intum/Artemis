import { Component, Input } from '@angular/core';
import { LinkPreviewService } from 'app/shared/link-preview/service/link-preview.service';
import { Link } from 'app/shared/link-preview/linkify/interfaces/linkify.interface';

@Component({
    selector: 'jhi-link-preview-container',
    templateUrl: './link-preview-container.component.html',
    styleUrls: ['./link-preview-container.component.scss'],
})
export class LinkPreviewContainerComponent {
    // to forward
    @Input() color = 'primary'; // accent | warn
    @Input() multiple: boolean;
    @Input() showLoadingsProgress = true;

    constructor(public linkPreviewService: LinkPreviewService) {
        console.log('fetching LinkPreviewContainerComponent  : ');
    }

    trackLinks(index: number, link: Link) {
        return link ? link.href : undefined;
    }
}
