import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TumAetUiButtonDirective, TumAetUiCheckboxComponent, TumAetUiChipComponent, TumAetUiMultiSelectComponent, TumAetUiSelectComponent } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

export interface FilterGroup {
    labelKey: string;
    items: string[];
    single?: boolean;
}

@Component({
    selector: 'jhi-filter-dropdown',
    templateUrl: './filter-dropdown.component.html',
    imports: [FormsModule, ArtemisTranslatePipe, TumAetUiButtonDirective, TumAetUiCheckboxComponent, TumAetUiChipComponent, TumAetUiMultiSelectComponent, TumAetUiSelectComponent],
    host: { class: 'block max-w-full' },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FilterDropdownComponent {
    readonly groupedFilters = input.required<FilterGroup[]>();
    readonly activeFilters = input.required<string[]>();
    readonly filterChange = output<string[]>();

    protected readonly groups = computed(() =>
        this.groupedFilters()
            .map((group) => ({
                single: group.single,
                labelKey: group.labelKey,
                items: group.items,
                selected: group.items.filter((item) => this.activeFilters().includes(item)),
            }))
            .sort((a, b) => Number(a.items.length === 1) - Number(b.items.length === 1)),
    );

    protected updateGroup(group: FilterGroup, values: unknown[]): void {
        this.filterChange.emit([...this.activeFilters().filter((value) => !group.items.includes(value)), ...group.items.filter((value) => values.includes(value))]);
    }

    protected removeFilter(value: string, event: Event): void {
        event.stopPropagation();
        this.filterChange.emit(this.activeFilters().filter((filter) => filter !== value));
    }
}
