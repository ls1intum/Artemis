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

    constructor(private localStorageService: LocalStorageService) {}

    ngOnInit(): void {
        const collapsed = this.localStorageService.retrieve(this.headerKey);
        this.isCollapsed = collapsed !== undefined && collapsed !== null && (collapsed as boolean);
        this.localStorageService.store(this.headerKey, this.isCollapsed);
    }

    /**
     * Toggle the state of the instruction block and store the updated state in the local storage.
     */
    toggleCollapsed() {
        this.isCollapsed = !this.isCollapsed;
        this.localStorageService.store(this.headerKey, this.isCollapsed);
    }
}
