import { Component, Input, OnChanges, OnDestroy, OnInit } from '@angular/core';
import { Params } from '@angular/router';
import { Post } from 'app/entities/metis/post.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { Subscription } from 'rxjs';

export interface PostingContentPart {
    contentBeforeLink?: string;
    linkToReference?: (string | number)[];
    queryParams?: Params;
    linkContent?: string;
    contentAfterLink?: string;
}

@Component({
    selector: 'jhi-posting-content',
    templateUrl: './posting-content.component.html',
})
export class PostingContentComponent implements OnInit, OnChanges, OnDestroy {
    @Input() content?: string;
    currentlyLoadedPosts?: Post[];
    postingContentParts: PostingContentPart[];

    private postsSubscription: Subscription;

    constructor(private metisService: MetisService) {}

    ngOnInit(): void {
        this.postsSubscription = this.metisService.posts.subscribe((posts: Post[]) => {
            this.currentlyLoadedPosts = posts;
        });
        this.computePostingContentParts();
    }

    ngOnChanges(): void {
        this.computePostingContentParts();
    }

    /**
     * on leaving the page, the modal should be closed, subscriptions unsubscribed
     */
    ngOnDestroy(): void {
        this.postsSubscription?.unsubscribe();
    }

    private computePostingContentParts(): void {
        this.postingContentParts = [];
        const regexp = /([^#]*)(#\d+)([^#]*)/g;
        const splitArray = [...this.content!.matchAll(regexp)];
        if (splitArray && splitArray.length > 0) {
            for (const array of splitArray) {
                const referencedId = Number(array[2].substring(1));
                if (this.currentlyLoadedPosts) {
                    const referencedIndexInLoadedPosts = this.currentlyLoadedPosts.findIndex((post: Post) => post.id === referencedId);
                    // referenced post is in currently loaded posts
                    if (referencedIndexInLoadedPosts > -1) {
                        const referencedPost = this.currentlyLoadedPosts[referencedIndexInLoadedPosts];
                        const contentPart: PostingContentPart = {
                            contentBeforeLink: array[1],
                            linkToReference: this.metisService.getLinkForPost(referencedPost),
                            queryParams: this.metisService.getQueryParamsForPost(referencedPost),
                            linkContent: array[2],
                            contentAfterLink: array[3],
                        };
                        this.postingContentParts.push(contentPart);
                        // references post is not in currently loaded posts -> navigate to course discussion overview with query param of referenced id
                    } else {
                        const contentLink: PostingContentPart = {
                            contentBeforeLink: array[1],
                            linkToReference: ['/courses', this.metisService.getCourse().id!, 'discussion'],
                            queryParams: { searchText: `#${referencedId}` } as Params,
                            linkContent: array[2],
                            contentAfterLink: array[3],
                        };
                        this.postingContentParts.push(contentLink);
                    }
                }
            }
        } else {
            const contentLink: PostingContentPart = {
                contentBeforeLink: this.content,
                linkToReference: undefined,
                queryParams: undefined,
                linkContent: undefined,
                contentAfterLink: undefined,
            };
            this.postingContentParts.push(contentLink);
        }
    }
}
