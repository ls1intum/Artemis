import { TranslateService } from '@ngx-translate/core';
import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faFilter } from '@fortawesome/free-solid-svg-icons';
import {
    TumAetUiButtonComponent,
    TumAetUiButtonDirective,
    TumAetUiButtonGroupComponent,
    TumAetUiCheckboxComponent,
    TumAetUiPopoverComponent,
    TumAetUiPopoverTriggerDirective,
    TumAetUiTagComponent,
    TumAetUiTooltipDirective,
} from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

let nextFilterDropdownId = 0;

export interface FilterGroup {
    labelKey: string;
    items: string[];
    segmented?: boolean;
}

/**
 * The filters of a table as one button for its 40px toolbar: the button shows how many filters are active, and the groups are in a popover that
 * stacks them, so that the toolbar keeps its height on a narrow screen too.
 */
@Component({
    selector: 'jhi-filter-dropdown',
    templateUrl: './filter-dropdown.component.html',
    host: { class: 'inline-flex shrink-0' },
    imports: [
        ArtemisTranslatePipe,
        FaIconComponent,
        TranslateDirective,
        TumAetUiButtonDirective,
        TumAetUiCheckboxComponent,
        TumAetUiButtonComponent,
        TumAetUiButtonGroupComponent,
        TumAetUiPopoverComponent,
        TumAetUiPopoverTriggerDirective,
        TumAetUiTagComponent,
        TumAetUiTooltipDirective,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FilterDropdownComponent {
    private readonly translateService = inject(TranslateService);
    protected readonly allValue = '';
    private readonly instanceId = nextFilterDropdownId++;
    protected readonly checkboxIdPrefix = `exercise-filter-${this.instanceId}-`;
    protected readonly groupIdPrefix = `exercise-filter-group-${this.instanceId}-`;
    protected readonly faFilter = faFilter;

    readonly groupedFilters = input.required<FilterGroup[]>();
    readonly activeFilters = input.required<string[]>();
    readonly filterChange = output<string[]>();

    protected readonly groups = computed(() =>
        this.groupedFilters()
            .map((group) => ({
                segmented: group.segmented,
                labelKey: group.labelKey,
                items: group.items,
                selected: group.items.filter((item) => this.activeFilters().includes(item)),
            }))
            .sort((a, b) => Number(a.items.length === 1) - Number(b.items.length === 1)),
    );

    protected readonly checkboxGroups = computed(() => this.groups().filter((group) => group.items.length === 1));

    protected segmentedOptions(group: FilterGroup): { value: string; label: string }[] {
        return [
            {
                value: this.allValue,
                label: this.translateService.instant('artemisApp.exercise.showAll'),
            },
            ...group.items.map((value) => ({ value, label: this.translateService.instant('artemisApp.exercise.show' + value) })),
        ];
    }

    protected toggleGroupOption(group: FilterGroup & { selected: string[] }, value: string): void {
        const selected = group.selected.includes(value) ? group.selected.filter((item) => item !== value) : [...group.selected, value];
        this.updateGroup(group, value === this.allValue ? [] : selected);
    }

    /** Adds or removes one item of a group that is listed as checkboxes. */
    protected toggleItem(group: FilterGroup & { selected: string[] }, item: string, checked: boolean): void {
        this.updateGroup(group, checked ? [...group.selected, item] : group.selected.filter((value) => value !== item));
    }

    protected updateGroup(group: FilterGroup, values: unknown[]): void {
        this.filterChange.emit([...this.activeFilters().filter((value) => !group.items.includes(value)), ...group.items.filter((value) => values.includes(value))]);
    }
}
