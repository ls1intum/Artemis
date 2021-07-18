import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output } from '@angular/core';
import { TagModel, TagModelClass } from 'ngx-chips/core/accessor';
import { Subscription } from 'rxjs';
import { MetisService } from 'app/shared/metis/metis.service';

@Component({
    selector: 'jhi-post-tag-selector',
    templateUrl: './post-tag-selector.component.html',
    styleUrls: ['../../../../overview/discussion/discussion.scss'],
})
export class PostTagSelectorComponent implements OnInit, OnChanges, OnDestroy {
    @Input() postTags?: string[];
    @Output() postTagsChange = new EventEmitter<string[]>();
    existingPostTags: string[];
    tags: TagModel[];

    private tagsSubscription: Subscription;

    constructor(private metisService: MetisService) {
        this.tagsSubscription = this.metisService.tags.subscribe((tags: string[]) => {
            this.existingPostTags = tags;
        });
    }

    ngOnInit(): void {
        this.mapTagsToTagModel();
    }

    ngOnChanges(): void {
        this.mapTagsToTagModel();
    }

    onPostTagsChange(tags: TagModel[]) {
        this.postTagsChange.emit(tags.map((tag) => (tag as TagModelClass).value));
    }

    ngOnDestroy() {
        this.tagsSubscription.unsubscribe();
    }

    private mapTagsToTagModel() {
        this.tags = this.postTags
            ? this.postTags.map((tag) => {
                  return {
                      value: tag,
                      display: tag,
                  };
              })
            : [];
    }
}
