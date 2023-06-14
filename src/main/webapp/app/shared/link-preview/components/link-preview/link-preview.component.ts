import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs/internal/Subscription';
import { LinkPreview, LinkPreviewService } from 'app/shared/link-preview/service/link-preview.service';
import { Link } from 'app/shared/link-preview/linkify/interfaces/linkify.interface';

@Component({
    selector: 'jhi-link-preview',
    exportAs: 'matLinkPreview',
    templateUrl: './link-preview.component.html',
    styleUrls: ['./link-preview.component.scss'],
})
export class LinkPreviewComponent implements OnInit, OnDestroy {
    @Input() link: Link;
    @Input() linkPreview: LinkPreview;

    // forwarded from the container
    @Input() color = 'primary'; // accent | warn
    @Input() showLoadingsProgress = true;

    loaded: boolean;
    hasError: boolean;
    private _subscription: Subscription;

    constructor(public linkPreviewService: LinkPreviewService) {}

    ngOnInit(): void {
        if (this.link && !this.linkPreview) {
            this._subscription = this.linkPreviewService.fetchLink(this.link.href).subscribe({
                next: (value) => (this.linkPreview = value),
                error: () => (this.hasError = true),
                complete: () => (this.loaded = true),
            });
        }
    }

    ngOnDestroy(): void {
        if (this._subscription) {
            this._subscription.unsubscribe();
        }
    }
}
