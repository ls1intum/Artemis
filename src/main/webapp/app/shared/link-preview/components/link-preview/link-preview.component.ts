import { Component, Input } from '@angular/core';
import { LinkPreview } from 'app/shared/link-preview/services/link-preview.service';

@Component({
    selector: 'jhi-link-preview',
    templateUrl: './link-preview.component.html',
    styleUrls: ['./link-preview.component.scss'],
})
export class LinkPreviewComponent {
    @Input() linkPreview: LinkPreview;
    @Input() showLoadingsProgress: boolean;
    @Input() loaded: boolean;
    @Input() hasError: boolean;
}
