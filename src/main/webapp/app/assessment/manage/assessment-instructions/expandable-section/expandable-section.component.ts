import { Component, OnInit, inject, input } from '@angular/core';
import { faAngleDown, faAngleRight } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbCollapse } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { LocalStorageService } from 'app/shared/service/local-storage.service';

@Component({
    selector: 'jhi-expandable-section',
    templateUrl: './expandable-section.component.html',
    imports: [FaIconComponent, NgbCollapse, ArtemisTranslatePipe],
})
export class ExpandableSectionComponent implements OnInit {
    private localStorageService = inject(LocalStorageService);

    readonly headerKey = input.required<string>();
    readonly hasTranslation = input(true);
    readonly isSubHeader = input(false);

    isCollapsed: boolean;
    // Icons
    faAngleRight = faAngleRight;
    faAngleDown = faAngleDown;

    readonly PREFIX = 'collapsed.';

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
        return this.PREFIX + this.headerKey();
    }
}
