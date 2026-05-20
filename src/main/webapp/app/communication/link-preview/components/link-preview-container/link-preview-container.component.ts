import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, effect, inject, input, signal, untracked } from '@angular/core';
import { Subscription } from 'rxjs';
import { User } from 'app/core/user/user.model';
import { Posting } from 'app/communication/shared/entities/posting.model';
import { LinkPreviewComponent } from '../link-preview/link-preview.component';
import { LinkPreview, LinkPreviewService } from 'app/communication/link-preview/services/link-preview.service';
import { Link, LinkifyService } from 'app/communication/link-preview/services/linkify.service';

@Component({
    selector: 'jhi-link-preview-container',
    templateUrl: './link-preview-container.component.html',
    styleUrls: ['./link-preview-container.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [LinkPreviewComponent],
})
export class LinkPreviewContainerComponent implements OnInit {
    private readonly linkPreviewService: LinkPreviewService = inject(LinkPreviewService);
    private readonly linkifyService: LinkifyService = inject(LinkifyService);
    private readonly destroyRef = inject(DestroyRef);

    readonly data = input<string>();
    readonly author = input<User>();
    readonly posting = input<Posting>();
    readonly isEdited = input<boolean>();
    readonly isReply = input<boolean>();

    readonly linkPreviews = signal<LinkPreview[]>([]);
    readonly hasError = signal<boolean>(false);
    readonly loaded = signal<boolean>(false);
    readonly showLoadingsProgress = signal<boolean>(true);
    readonly multiple = signal<boolean>(false);

    private initialized = false;
    private pendingFetches = new Subscription();

    constructor() {
        effect(() => {
            // Track all inputs that may trigger a reload
            this.data();
            this.author();
            this.posting();
            this.isEdited();
            this.isReply();
            untracked(() => {
                if (this.initialized) {
                    this.reloadLinkPreviews();
                }
            });
        });
        this.destroyRef.onDestroy(() => {
            this.pendingFetches.unsubscribe();
        });
    }

    ngOnInit() {
        this.findPreviews();
        this.initialized = true;
    }

    private reloadLinkPreviews() {
        this.pendingFetches.unsubscribe();
        this.pendingFetches = new Subscription();
        this.loaded.set(false);
        this.showLoadingsProgress.set(true);
        this.linkPreviews.set([]); // Clear the existing link previews
        this.findPreviews();
    }

    private findPreviews() {
        const links: Link[] = this.linkifyService.find(this.data() ?? '');
        // TODO: The limit of 5 link previews should be configurable (maybe in course level)
        const previewableLinks = links.filter((link) => !link.isLinkPreviewRemoved).slice(0, 5);
        if (previewableLinks.length === 0) {
            this.loaded.set(true);
            this.showLoadingsProgress.set(false);
            return;
        }
        previewableLinks.forEach((link) => {
            const sub = this.linkPreviewService.fetchLink(link.href).subscribe({
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
            this.pendingFetches.add(sub);
        });
    }

    trackLinks(index: number, preview: LinkPreview) {
        return preview?.url;
    }
}
