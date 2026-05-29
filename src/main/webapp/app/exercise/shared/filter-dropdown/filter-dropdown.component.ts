import { Component, ViewEncapsulation, computed, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { SelectModule } from 'primeng/select';

export interface FilterGroup {
    /** i18n key for the group header label. Empty string renders no header. */
    labelKey: string;
    items: string[];
}

@Component({
    selector: 'jhi-filter-dropdown',
    templateUrl: './filter-dropdown.component.html',
    styleUrls: ['./filter-dropdown.component.scss'],
    imports: [FormsModule, ArtemisTranslatePipe, SelectModule],
    encapsulation: ViewEncapsulation.None,
})
export class FilterDropdownComponent {
    readonly filters = input<string[]>([]);
    readonly groupedFilters = input<FilterGroup[] | undefined>(undefined);
    readonly activeFilter = input.required<string>();
    /** The value that represents the "show all" state — used to decide button style and label. */
    readonly allValue = input<string>('All');
    /** i18n key shown as the button label when no filter is active (allValue is selected). */
    readonly filterLabelKey = input<string>('artemisApp.exercise.filter');
    /** i18n key prefix prepended to each filter value for item labels. */
    readonly translationPrefix = input<string>('artemisApp.exercise.show');
    readonly filterChange = output<string>();

    readonly isFiltered = computed(() => this.activeFilter() !== this.allValue());
}
