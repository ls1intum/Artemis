import { AfterContentChecked, ChangeDetectorRef, Component, ElementRef, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, ViewChild, inject } from '@angular/core';
import { Observable, Subscription, map, startWith } from 'rxjs';
import { MetisService } from 'app/shared/metis/metis.service';
import { COMMA, ENTER, TAB } from '@angular/cdk/keycodes';
import { FormControl } from '@angular/forms';
import { MatChipInputEvent } from '@angular/material/chips';
import { MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { faTimes } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-post-tag-selector',
    templateUrl: './post-tag-selector.component.html',
    styleUrls: ['./post-tag-selector.component.scss'],
})
export class PostTagSelectorComponent implements OnInit, OnChanges, OnDestroy, AfterContentChecked {
    private metisService = inject(MetisService);
    private changeDetector = inject(ChangeDetectorRef);

    @Input() postTags?: string[];

    @Output() postTagsChange = new EventEmitter<string[]>();

    @ViewChild('tagInput') tagInput: ElementRef<HTMLInputElement>;

    existingPostTags: Observable<string[]>;
    tags: string[];

    separatorKeysCodes = [ENTER, COMMA, TAB];
    tagCtrl = new FormControl<string | undefined>(undefined);

    private tagsSubscription: Subscription;

    // Icons
    faTimes = faTimes;

    /**
     * on initialization: subscribes to existing post tags used in this course (will be shown in dropdown of tag selector),
     * copies the input post tags to string tags, so that they are shown in the selector
     */
    ngOnInit(): void {
        this.tagsSubscription = this.metisService.tags.subscribe((tags: string[]) => {
            this.existingPostTags = this.tagCtrl.valueChanges.pipe(
                startWith(undefined),
                map((category: string | undefined) => (category ? this._filter(category) : tags.slice())),
            );
        });
        this.tags = this.postTags ? this.postTags : [];
    }

    private _filter(value: string): string[] {
        const filterValue = value.toLowerCase();
        return this.tags.filter((tag) => tag.toLowerCase().includes(filterValue));
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
        this.changeDetector.detectChanges();
    }

    /**
     * Called when the user selects a tag
     * @param event a new tag was added
     */
    onItemAdd(event: MatChipInputEvent) {
        const tagString = (event.value || '').trim();
        // also test for duplicated tag
        if (tagString && !this.tags?.includes(tagString) && this.tags?.length < 3) {
            if (!this.tags) {
                this.tags = [];
            }
            this.tags.push(tagString);
            this.postTagsChange.emit(this.tags);
        }

        // Clear the input value
        event.chipInput!.clear();
        this.tagCtrl.setValue(null);
    }

    onItemSelect(event: MatAutocompleteSelectedEvent): void {
        if (!this.tags?.includes(event.option.viewValue) && this.tags?.length < 3) {
            if (!this.tags) {
                this.tags = [];
            }
            this.tags.push(event.option.viewValue);
            this.postTagsChange.emit(this.tags);
        }
        this.tagInput.nativeElement.value = '';
        this.tagCtrl.setValue(null);
    }

    /**
     * cancel colorSelector and remove exerciseCategory
     * @param {string} tagToRemove
     */
    onItemRemove(tagToRemove: string) {
        this.tags = this.tags.filter((tag) => tag !== tagToRemove);
        this.postTagsChange.emit(this.tags);
    }

    ngOnDestroy(): void {
        this.tagsSubscription.unsubscribe();
    }
}
