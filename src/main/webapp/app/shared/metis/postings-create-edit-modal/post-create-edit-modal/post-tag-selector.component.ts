import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output } from '@angular/core';
import { Subscription } from 'rxjs';
import { MetisService } from 'app/shared/metis/metis.service';

@Component({
    selector: 'jhi-post-tag-selector',
    templateUrl: './post-tag-selector.component.html',
})
export class PostTagSelectorComponent implements OnInit, OnChanges, OnDestroy {
    @Input() postTags?: string[];
    @Output() postTagsChange = new EventEmitter<string[]>();
    existingPostTags: string[];
    tags: string[];

    private tagsSubscription: Subscription;

    constructor(private metisService: MetisService) {}

    /**
     * on initialization: subscribes to exsting post tags used in this course (will be shown in dropdown of tag selector),
     * copies the input post tags to tags, so that they are shown in the selector
     */
    ngOnInit(): void {
        this.tagsSubscription = this.metisService.tags.subscribe((tags: string[]) => {
            this.existingPostTags = tags;
        });
        this.tags = this.postTags ? this.postTags : [];
    }

    /**
     * on changes: updates tags (selected in selector) and post tags (input)
     */
    ngOnChanges(): void {
        this.tags = this.postTags ? this.postTags : [];
    }

    /**
     * emits an event with all post tags selected in the selector component
     * @param tags
     */
    onPostTagsChange(tags: string[]): void {
        this.postTagsChange.emit(tags);
    }

    ngOnDestroy(): void {
        this.tagsSubscription.unsubscribe();
    }
}
