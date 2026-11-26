import { Component, OnInit, inject, input, signal } from '@angular/core';
import { faAngleDown, faAngleRight } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';

@Component({
    selector: 'jhi-expandable-section',
    templateUrl: './expandable-section.component.html',
    styleUrls: ['./expandable-section.component.css'],
    imports: [FaIconComponent, ArtemisTranslatePipe],
})
export class ExpandableSectionComponent implements OnInit {
    private localStorageService = inject(LocalStorageService);

    readonly headerKey = input.required<string>();
    readonly hasTranslation = input(true);
    readonly isSubHeader = input(false);
    readonly initiallyCollapsed = input(false);
    /** Keeps the saved state apart from other places that show a section with the same header. */
    readonly storageScope = input<string>();

    readonly isCollapsed = signal<boolean>(undefined!);
    // Icons
    faAngleRight = faAngleRight;
    faAngleDown = faAngleDown;

    readonly PREFIX = 'collapsed.';

    ngOnInit(): void {
        // Only a toggle saves the state, so a section the user never toggled keeps following initiallyCollapsed.
        this.isCollapsed.set(this.localStorageService.retrieve<boolean>(this.storageKey) ?? this.initiallyCollapsed());
    }

    /**
     * Toggle the state of the instruction block and store the updated state in the local storage.
     */
    toggleCollapsed() {
        this.isCollapsed.update((collapsed) => !collapsed);
        this.localStorageService.store(this.storageKey, this.isCollapsed());
    }

    /**
     * Returns the key to identify the value in the local storage
     */
    get storageKey() {
        const scope = this.storageScope();
        return this.PREFIX + (scope ? `${scope}.` : '') + this.headerKey();
    }
}
