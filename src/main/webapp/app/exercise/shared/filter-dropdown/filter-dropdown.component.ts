import { TranslateService } from '@ngx-translate/core';
import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
    TumAetUiButtonComponent,
    TumAetUiButtonDirective,
    TumAetUiButtonGroupComponent,
    TumAetUiCheckboxComponent,
    TumAetUiMultiSelectComponent,
    TumAetUiSelectComponent,
    TumAetUiTooltipDirective,
} from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

let nextFilterDropdownId = 0;

export interface FilterGroup {
    labelKey: string;
    items: string[];
    single?: boolean;
    segmented?: boolean;
}

@Component({
    selector: 'jhi-filter-dropdown',
    templateUrl: './filter-dropdown.component.html',
    imports: [
        FormsModule,
        ArtemisTranslatePipe,
        TumAetUiButtonDirective,
        TumAetUiCheckboxComponent,
        TumAetUiMultiSelectComponent,
        TumAetUiButtonComponent,
        TumAetUiButtonGroupComponent,
        TumAetUiSelectComponent,
        TumAetUiTooltipDirective,
    ],
    styleUrls: ['./filter-dropdown.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FilterDropdownComponent {
    private readonly translateService = inject(TranslateService);
    protected readonly allValue = '';
    protected readonly checkboxIdPrefix = `exercise-filter-${nextFilterDropdownId++}-`;

    readonly groupedFilters = input.required<FilterGroup[]>();
    readonly activeFilters = input.required<string[]>();
    readonly filterChange = output<string[]>();

    protected readonly groups = computed(() =>
        this.groupedFilters()
            .map((group) => ({
                single: group.single,
                segmented: group.segmented,
                labelKey: group.labelKey,
                items: group.items,
                selected: group.items.filter((item) => this.activeFilters().includes(item)),
            }))
            .sort((a, b) => Number(a.items.length === 1) - Number(b.items.length === 1)),
    );

    protected readonly checkboxGroups = computed(() => this.groups().filter((group) => group.items.length === 1));

    protected singleOptions(group: FilterGroup): { value: string; label: string }[] {
        return [
            {
                value: this.allValue,
                label: (group.segmented ? '' : this.translateService.instant(group.labelKey) + ': ') + this.translateService.instant('artemisApp.exercise.showAll'),
            },
            ...group.items.map((value) => ({ value, label: this.translateService.instant('artemisApp.exercise.show' + value) })),
        ];
    }

    protected toggleGroupOption(group: FilterGroup & { selected: string[] }, value: string): void {
        const selected = group.selected.includes(value) ? group.selected.filter((item) => item !== value) : [...group.selected, value];
        this.updateGroup(group, value === this.allValue ? [] : selected);
    }

    protected updateGroup(group: FilterGroup, values: unknown[]): void {
        this.filterChange.emit([...this.activeFilters().filter((value) => !group.items.includes(value)), ...group.items.filter((value) => values.includes(value))]);
    }
}
