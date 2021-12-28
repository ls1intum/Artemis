import { AfterContentChecked, ChangeDetectorRef, Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output } from '@angular/core';
import { Subscription } from 'rxjs';
import { MetisService } from 'app/shared/metis/metis.service';

@Component({
    selector: 'jhi-post-tag-selector',
    templateUrl: './post-tag-selector.component.html',
    styleUrls: ['./post-tag-selector.component.scss'],
})
export class PostTagSelectorComponent implements OnInit, OnChanges, OnDestroy, AfterContentChecked {
    @Input() postTags?: string[];

    @Output() postTagsChange = new EventEmitter<string[]>();

    existingPostTags: string[];
    tags: string[];

    private tagsSubscription: Subscription;

    constructor(private metisService: MetisService, private cdref: ChangeDetectorRef) {}

    /**
     * on initialization: subscribes to existing post tags used in this course (will be shown in dropdown of tag selector),
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
     * this lifecycle hook is required to avoid causing "Expression has changed after it was checked"-error when dismissing all changes in the tag-selector
     * on dismissing the edit-create-modal -> we do not want to store changes in the create-edit-modal that are not saved
     */
    ngAfterContentChecked() {
        this.cdref.detectChanges();
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
