import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TumAetUiButtonDirective, TumAetUiChipComponent, TumAetUiMultiSelectComponent } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

export interface FilterGroup {
    labelKey: string;
    items: string[];
}

@Component({
    selector: 'jhi-filter-dropdown',
    templateUrl: './filter-dropdown.component.html',
    imports: [FormsModule, ArtemisTranslatePipe, TumAetUiButtonDirective, TumAetUiChipComponent, TumAetUiMultiSelectComponent],
    host: { class: 'block max-w-full' },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FilterDropdownComponent {
    readonly groupedFilters = input.required<FilterGroup[]>();
    readonly activeFilters = input.required<string[]>();
    readonly filterChange = output<string[]>();

    protected readonly groups = computed(() =>
        this.groupedFilters().map((group) => ({
            labelKey: group.labelKey,
            items: group.items,
            selected: group.items.filter((item) => this.activeFilters().includes(item)),
        })),
    );

    protected updateGroup(group: FilterGroup, values: unknown[]): void {
        this.filterChange.emit([...this.activeFilters().filter((value) => !group.items.includes(value)), ...group.items.filter((value) => values.includes(value))]);
    }

    protected removeFilter(value: string, event: Event): void {
        event.stopPropagation();
        this.filterChange.emit(this.activeFilters().filter((filter) => filter !== value));
    }
}
