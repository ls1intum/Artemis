import { Component, Input, OnChanges, OnDestroy, OnInit } from '@angular/core';
import { Params } from '@angular/router';
import { Post } from 'app/entities/metis/post.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { Subscription } from 'rxjs';

export interface PostingContentPart {
    contentBeforeLink?: string;
    linkToReference?: (string | number)[];
    queryParams?: Params;
    referenceStr?: string;
    contentAfterLink?: string;
}

@Component({
    selector: 'jhi-posting-content',
    templateUrl: './posting-content.component.html',
})
export class PostingContentComponent implements OnInit, OnChanges, OnDestroy {
    @Input() content?: string;
    currentlyLoadedPosts: Post[];
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
        const pattern = /(#\d+)/gim;
        const referenceIndicesArray: number[][] = [];

        // find start and end index of referenced posts in content, for each reference save [startIndexOfReference, endIndexOfReference] in the referenceIndicesArray
        while (true) {
            const match = pattern.exec(this.content!);
            if (!match) {
                break;
            }
            referenceIndicesArray.push([match.index, pattern.lastIndex]);
        }

        // TODO: add comments, test
        if (referenceIndicesArray && referenceIndicesArray.length > 0) {
            referenceIndicesArray.forEach((referenceIndices: number[], index: number) => {
                const referencedId = this.content!.substring(referenceIndices[0] + 1, referenceIndices[1]);
                const referenceStr = this.content!.substring(referenceIndices[0], referenceIndices[1]);
                const referencedPostInLoadedPosts = this.currentlyLoadedPosts.find((post: Post) => post.id! === +referencedId);
                const linkToReference = referencedPostInLoadedPosts
                    ? this.metisService.getLinkForPost(referencedPostInLoadedPosts)
                    : ['/courses', this.metisService.getCourse().id!, 'discussion'];
                const queryParams = referencedPostInLoadedPosts ? this.metisService.getQueryParamsForPost(referencedPostInLoadedPosts) : ({ searchText: referenceStr } as Params);
                let endIndex;
                if (index < referenceIndicesArray.length - 1) {
                    endIndex = referenceIndicesArray[index + 1][0];
                } else {
                    endIndex = this.content!.length;
                }
                const contentPart: PostingContentPart = {
                    contentBeforeLink: index === 0 ? this.content!.substring(0, referenceIndices[0]) : undefined,
                    linkToReference,
                    queryParams,
                    referenceStr,
                    contentAfterLink: this.content!.substring(referenceIndices[1], endIndex),
                };
                this.postingContentParts.push(contentPart);
            });
        } else {
            const contentLink: PostingContentPart = {
                contentBeforeLink: this.content,
                linkToReference: undefined,
                queryParams: undefined,
                referenceStr: undefined,
                contentAfterLink: undefined,
            };
            this.postingContentParts.push(contentLink);
        }
    }
}
