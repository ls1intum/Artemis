import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';

import { ConfigurationService } from './configuration.service';
import { Bean, PropertySource } from './configuration.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { JsonPipe, KeyValuePipe } from '@angular/common';
import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';
import { InputTextModule } from 'primeng/inputtext';
import { TagModule } from 'primeng/tag';
import { TableModule } from 'primeng/table';
import { SortEvent } from 'primeng/api';

/**
 * Component for viewing application configuration.
 * Displays beans and property sources with filtering and sorting.
 */
@Component({
    selector: 'jhi-configuration',
    templateUrl: './configuration.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, FormsModule, JsonPipe, KeyValuePipe, AdminTitleBarTitleDirective, InputTextModule, TagModule, TableModule],
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
     * Handles a PrimeNG table sort event. The table runs in `[customSort]` mode and only sorts by
     * the single `prefix` field, so the handler just mirrors the resolved order onto the
     * `beansAscending` signal that drives the client-side sort in `beans()`.
     */
    onTableSort(event: SortEvent): void {
        this.beansAscending.set((event.order ?? 1) === 1);
    }
}
