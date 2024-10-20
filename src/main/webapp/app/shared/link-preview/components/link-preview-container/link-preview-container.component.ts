import { ChangeDetectionStrategy, Component, OnChanges, OnInit, computed, inject, input, signal } from '@angular/core';
import { LinkPreview, LinkPreviewService } from 'app/shared/link-preview/services/link-preview.service';
import { Link, LinkifyService } from 'app/shared/link-preview/services/linkify.service';
import { User } from 'app/core/user/user.model';
import { Posting } from 'app/entities/metis/posting.model';

@Component({
    selector: 'jhi-link-preview-container',
    templateUrl: './link-preview-container.component.html',
    styleUrls: ['./link-preview-container.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LinkPreviewContainerComponent implements OnInit, OnChanges {
    private readonly linkPreviewService: LinkPreviewService = inject(LinkPreviewService);
    private readonly linkifyService: LinkifyService = inject(LinkifyService);

    readonly data = input<string>();
    readonly author = input<User>();
    readonly posting = input<Posting>();
    readonly isEdited = input<boolean>();
    readonly isReply = input<boolean>();

    readonly dataSafe = computed<string>(() => this.data() ?? '');
    readonly linkPreviews = signal<LinkPreview[]>([]);
    readonly hasError = signal<boolean>(false);
    readonly loaded = signal<boolean>(false);
    readonly showLoadingsProgress = signal<boolean>(true);
    readonly multiple = signal<boolean>(false);

    ngOnInit() {
        this.findPreviews();
    }

    ngOnChanges() {
        this.reloadLinkPreviews();
    }

    private reloadLinkPreviews() {
        this.loaded.set(false);
        this.showLoadingsProgress.set(true);
        this.linkPreviews.set([]); // Clear the existing link previews
        this.findPreviews();
    }

    private findPreviews() {
        const links: Link[] = this.linkifyService.find(this.dataSafe());
        // TODO: The limit of 5 link previews should be configurable (maybe in course level)
        links
            .filter((link) => !link.isLinkPreviewRemoved)
            .slice(0, 5)
            .forEach((link) => {
                this.linkPreviewService.fetchLink(link.href).subscribe({
                    next: (linkPreview) => {
                        linkPreview.shouldPreviewBeShown = !!(linkPreview.url && linkPreview.title && linkPreview.description && linkPreview.image);

                        const existingLinkPreviewIndex = this.linkPreviews().findIndex((preview) => preview.url === linkPreview.url);
                        if (existingLinkPreviewIndex !== -1) {
                            this.linkPreviews.update((previews) => {
                                const existingLinkPreview = previews[existingLinkPreviewIndex];
                                Object.assign(existingLinkPreview, linkPreview);
                                return previews;
                            });
                        } else {
                            this.linkPreviews.set([...this.linkPreviews(), linkPreview]);
                        }

                        this.hasError.set(false);
                        this.loaded.set(true);
                        this.showLoadingsProgress.set(false);
                        this.multiple.set(this.linkPreviews().length > 1);
                    },
                });
            });
    }

    trackLinks(index: number, preview: LinkPreview) {
        return preview?.url;
    }
}
