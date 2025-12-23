import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';

import { ConfigurationService } from './configuration.service';
import { Bean, PropertySource } from './configuration.model';
import { faSort } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { JsonPipe, KeyValuePipe } from '@angular/common';

/**
 * Component for viewing application configuration.
 * Displays beans and property sources with filtering and sorting.
 */
@Component({
    selector: 'jhi-configuration',
    templateUrl: './configuration.component.html',
    imports: [TranslateDirective, FormsModule, SortDirective, SortByDirective, FaIconComponent, JsonPipe, KeyValuePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfigurationComponent implements OnInit {
    private readonly configurationService = inject(ConfigurationService);

    /** All beans before filtering */
    private readonly allBeans = signal<Bean[]>([]);

    /** Filter string for bean prefixes */
    readonly beansFilter = signal('');

    /** Sort direction for beans */
    readonly beansAscending = signal(true);

    /** Filtered and sorted beans for display (computed from other signals) */
    readonly beans = computed(() => {
        const allBeansValue = this.allBeans();
        const filterValue = this.beansFilter();
        const ascendingValue = this.beansAscending();
        const ascendingMultiplier = ascendingValue ? -1 : 1;
        const descendingMultiplier = ascendingValue ? 1 : -1;

        return allBeansValue
            .filter((bean) => !filterValue || bean.prefix.toLowerCase().includes(filterValue.toLowerCase()))
            .sort((a, b) => (a.prefix < b.prefix ? ascendingMultiplier : descendingMultiplier));
    });

    /** Property sources from configuration */
    readonly propertySources = signal<PropertySource[]>([]);

    /** Icons */
    protected readonly faSort = faSort;

    /**
     * Loads beans and property sources on initialization.
     */
    ngOnInit(): void {
        this.configurationService.getBeans().subscribe((beans) => {
            this.allBeans.set(beans);
        });

        this.configurationService.getPropertySources().subscribe((propertySources) => {
            this.propertySources.set(propertySources);
        });
    }

    /**
     * Updates filter value for bean filtering.
     * @param value - The filter string
     */
    updateBeansFilter(value: string): void {
        this.beansFilter.set(value);
    }

    /**
     * Updates sort direction.
     * @param ascending - Sort direction
     */
    updateBeansAscending(ascending: boolean): void {
        this.beansAscending.set(ascending);
    }
}
