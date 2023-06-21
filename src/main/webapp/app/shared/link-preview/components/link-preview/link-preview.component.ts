import { Component, Input } from '@angular/core';
import { LinkPreview } from 'app/shared/link-preview/service/link-preview.service';

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
}
