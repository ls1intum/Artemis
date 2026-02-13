import { AfterContentInit, ContentChild, Directive, HostListener, effect, inject, input } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconDefinition, faSort, faSortDown, faSortUp } from '@fortawesome/free-solid-svg-icons';

import { SortDirective } from './sort.directive';

@Directive({ selector: '[jhiSortBy]' })
export class SortByDirective<T> implements AfterContentInit {
    private sort = inject<SortDirective<T>>(SortDirective, { host: true });

    jhiSortBy = input.required<T>();

    @ContentChild(FaIconComponent, { static: false })
    iconComponent?: FaIconComponent;

    sortIcon = faSort;
    sortAscIcon = faSortUp;
    sortDescIcon = faSortDown;

    constructor() {
        effect(() => {
            // Track changes to predicate and ascending
            this.sort.predicate();
            this.sort.ascending();
            this.updateIconDefinition();
        });
    }

    @HostListener('click')
    onClick(): void {
        if (this.iconComponent) {
            this.sort.sort(this.jhiSortBy());
        }
    }

    ngAfterContentInit(): void {
        this.updateIconDefinition();
    }

    private updateIconDefinition(): void {
        if (this.iconComponent) {
            let icon: IconDefinition = this.sortIcon;
            if (this.sort.predicate() === this.jhiSortBy()) {
                icon = this.sort.ascending() ? this.sortAscIcon : this.sortDescIcon;
            }
            this.iconComponent.icon.set(icon);
        }
    }
}
