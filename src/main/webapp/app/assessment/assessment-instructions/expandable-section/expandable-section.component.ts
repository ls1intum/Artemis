import { Component, Input, OnInit } from '@angular/core';
import { faAngleDown, faAngleRight } from '@fortawesome/free-solid-svg-icons';
import { LocalStorageService } from 'ngx-webstorage';

@Component({
    selector: 'jhi-expandable-section',
    templateUrl: './expandable-section.component.html',
})
export class ExpandableSectionComponent implements OnInit {
    @Input() headerKey: string;
    @Input() hasTranslation = true;
    @Input() isSubHeader = false;

    isCollapsed: boolean;
    // Icons
    faAngleRight = faAngleRight;
    faAngleDown = faAngleDown;

    readonly prefix = 'collapsed.';

    constructor(private localStorageService: LocalStorageService) {}

    ngOnInit(): void {
        this.isCollapsed = !!this.localStorageService.retrieve(this.storageKey);
        this.localStorageService.store(this.storageKey, this.isCollapsed);
    }

    /**
     * Toggle the state of the instruction block and store the updated state in the local storage.
     */
    toggleCollapsed() {
        this.isCollapsed = !this.isCollapsed;
        this.localStorageService.store(this.storageKey, this.isCollapsed);
    }

    /**
     * Returns the key to identify the value in the local storage
     */
    get storageKey() {
        return this.prefix + this.headerKey;
    }
}
