import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { faSort } from '@fortawesome/free-solid-svg-icons';
import { Level, Log, LoggersResponse } from 'app/admin/logs/log.model';
import { LogsService } from 'app/admin/logs/logs.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { SortDirective } from 'app/foundation/sort/directive/sort.directive';
import { SortByDirective } from 'app/foundation/sort/directive/sort-by.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { SlicePipe } from '@angular/common';
import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';
import { ButtonModule } from 'primeng/button';
import { ButtonGroupModule } from 'primeng/buttongroup';
import { TableModule } from 'primeng/table';
import { InputTextModule } from 'primeng/inputtext';

/**
 * Component for managing application log levels.
 * Allows viewing and changing log levels for different loggers.
 */
@Component({
    selector: 'jhi-logs',
    templateUrl: './logs.component.html',
    styleUrls: ['./logs.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        TranslateDirective,
        FormsModule,
        SortDirective,
        SortByDirective,
        FaIconComponent,
        SlicePipe,
        AdminTitleBarTitleDirective,
        ButtonModule,
        ButtonGroupModule,
        TableModule,
        InputTextModule,
    ],
})
export class LogsComponent implements OnInit, OnDestroy {
    private readonly logsService = inject(LogsService);

    /** Debounce delay (ms) before the filter is applied to the (potentially large) logger list */
    private static readonly FILTER_DEBOUNCE_MS = 200;

    /** All available loggers */
    readonly loggers = signal<Log[]>([]);

    /** Immediate value bound to the filter input (keeps typing responsive) */
    readonly filterInput = signal('');

    /** Debounced filter string actually used for filtering/sorting the logger list */
    readonly filter = signal('');

    /** Pending debounce timer handle for the filter input */
    private filterDebounceHandle?: ReturnType<typeof setTimeout>;

    /** Property to sort by */
    readonly orderProp = signal<keyof Log>('name');

    /** Sort direction */
    readonly ascending = signal(true);

    /** Filtered and sorted loggers for display (computed from other signals) */
    readonly filteredAndOrderedLoggers = computed(() => {
        const loggerList = this.loggers();
        const filterValue = this.filter();
        const orderPropValue = this.orderProp();
        const ascendingValue = this.ascending();

        return loggerList
            .filter((logger) => !filterValue || logger.name.toLowerCase().includes(filterValue.toLowerCase()))
            .sort((a, b) => {
                if (a[orderPropValue] < b[orderPropValue]) {
                    return ascendingValue ? -1 : 1;
                } else if (a[orderPropValue] > b[orderPropValue]) {
                    return ascendingValue ? 1 : -1;
                } else if (orderPropValue === 'level') {
                    return a.name < b.name ? -1 : 1;
                }
                return 0;
            });
    });

    /** Icons */
    protected readonly faSort = faSort;

    /**
     * Loads all loggers on initialization.
     */
    ngOnInit(): void {
        this.findAndExtractLoggers();
    }

    /**
     * Changes the log level for the specified logger.
     * @param name - Name of the logger
     * @param level - New log level (TRACE, DEBUG, INFO, WARN, ERROR)
     */
    changeLevel(name: string, level: Level): void {
        this.logsService.changeLevel(name, level).subscribe(() => this.findAndExtractLoggers());
    }

    /**
     * Cleans up any pending debounce timer.
     */
    ngOnDestroy(): void {
        if (this.filterDebounceHandle !== undefined) {
            clearTimeout(this.filterDebounceHandle);
        }
    }

    /**
     * Updates filter value for logger filtering.
     * The bound input updates immediately for responsiveness, while the actual filter applied to the
     * (potentially large) logger list is debounced so the filter+sort computation does not re-run on every keystroke.
     * @param value - The filter string
     */
    updateFilter(value: string): void {
        this.filterInput.set(value);
        if (this.filterDebounceHandle !== undefined) {
            clearTimeout(this.filterDebounceHandle);
        }
        this.filterDebounceHandle = setTimeout(() => this.filter.set(value), LogsComponent.FILTER_DEBOUNCE_MS);
    }

    /**
     * Updates sorting configuration.
     * @param prop - Property to sort by
     * @param asc - Sort direction
     */
    updateSort(prop: keyof Log, asc: boolean): void {
        this.orderProp.set(prop);
        this.ascending.set(asc);
    }

    /**
     * Fetches all loggers from the service and updates state.
     */
    private findAndExtractLoggers(): void {
        this.logsService.findAll().subscribe((response: LoggersResponse) => {
            const logs = Object.entries(response.loggers).map(([key, logger]) => new Log(key, logger.effectiveLevel));
            this.loggers.set(logs);
        });
    }
}
