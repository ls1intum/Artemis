import { TranslateService } from '@ngx-translate/core';
import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
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
    private readonly translateService = inject(TranslateService);
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

    protected singleOptions(group: FilterGroup): { value: string; label: string }[] {
        return [
            { value: 'All', label: this.translateService.instant(group.labelKey) + ': ' + this.translateService.instant('artemisApp.exercise.showAll') },
            ...group.items.map((value) => ({ value, label: this.translateService.instant('artemisApp.exercise.show' + value) })),
        ];
    }

    protected updateGroup(group: FilterGroup, values: unknown[]): void {
        this.filterChange.emit([...this.activeFilters().filter((value) => !group.items.includes(value)), ...group.items.filter((value) => values.includes(value))]);
    }

    protected removeFilter(value: string, event: Event): void {
        event.stopPropagation();
        this.filterChange.emit(this.activeFilters().filter((filter) => filter !== value));
    }
}
