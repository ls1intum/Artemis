import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { TagModel } from 'ngx-chips/core/accessor';
import { PostTag } from 'app/entities/metis/post-tag.model';

@Component({
    selector: 'jhi-post-tag-selector',
    templateUrl: './post-tag-selector.component.html',
    styleUrls: ['../../../category-selector/category-selector.scss'],
})
export class PostTagSelectorComponent {
    @Input() postTags: String[];
    @Input() existingPostTags: String[];
    @Output() selectedPostTags = new EventEmitter<String[]>();

    /**
     * set color if not selected and add exerciseCategory
     * @param postTagItem the tag of the exercise category
     */
    onItemAdded(postTagItem: TagModel) {
        const postTag = postTagItem as String;
        if (this.postTags) {
            this.postTags.push(postTag);
        } else {
            this.postTags = [postTag];
        }
        this.selectedPostTags.emit(this.postTags);
    }

    /**
     * cancel colorSelector and remove exerciseCategory
     * @param {PostTag} postTagItem
     */
    onItemRemove(postTagItem: TagModel) {
        const postTagToRemove = postTagItem as String;
        this.postTags = this.postTags.filter((postTag) => postTag !== postTagToRemove);
        this.selectedPostTags.emit(this.postTags);
    }
}
