import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { TagModel, TagModelClass } from 'ngx-chips/core/accessor';

@Component({
    selector: 'jhi-post-tag-selector',
    templateUrl: './post-tag-selector.component.html',
    styleUrls: ['../../../../overview/discussion/discussion.scss'],
})
export class PostTagSelectorComponent {
    @Input() existingPostTags: string[];
    @Input() postTags?: string[];
    @Output() postTagsChange = new EventEmitter<string[]>();

    addTag(tagModel: TagModel) {
        const tag = (tagModel as TagModelClass).value;
        this.postTagsChange.emit(this.postTags ? [...this.postTags, tag] : [tag]);
    }

    removeTag(tagModel: TagModel) {
        const tag = (tagModel as TagModelClass).value;
        this.postTagsChange.emit(this.postTags!.splice(this.postTags!.indexOf(tag), 1));
    }
}
