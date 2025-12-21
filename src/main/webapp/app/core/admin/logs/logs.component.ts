import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { faSort } from '@fortawesome/free-solid-svg-icons';
import { Level, Log, LoggersResponse } from 'app/core/admin/logs/log.model';
import { LogsService } from 'app/core/admin/logs/logs.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgClass, SlicePipe } from '@angular/common';

/**
 * Component for managing application log levels.
 * Allows viewing and changing log levels for different loggers.
 */
@Component({
    selector: 'jhi-logs',
    templateUrl: './logs.component.html',
    styleUrls: ['./logs.component.scss'],
    imports: [TranslateDirective, FormsModule, SortDirective, SortByDirective, FaIconComponent, NgClass, SlicePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LogsComponent implements OnInit {
    private readonly logsService = inject(LogsService);

    /** All available loggers */
    readonly loggers = signal<Log[]>([]);

    /** Filter string for logger names */
    readonly filter = signal('');

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
     * Updates filter value for logger filtering.
     * @param value - The filter string
     */
    updateFilter(value: string): void {
        this.filter.set(value);
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
