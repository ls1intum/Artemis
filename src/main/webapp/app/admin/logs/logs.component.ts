import { Component, OnInit, inject } from '@angular/core';
import { faSort } from '@fortawesome/free-solid-svg-icons';
import { Level, Log, LoggersResponse } from 'app/admin/logs/log.model';
import { LogsService } from 'app/admin/logs/logs.service';

@Component({
    selector: 'jhi-logs',
    templateUrl: './logs.component.html',
    styleUrls: ['./logs.component.scss'],
})
export class LogsComponent implements OnInit {
    private logsService = inject(LogsService);

    loggers?: Log[];
    filteredAndOrderedLoggers?: Log[];
    filter = '';
    orderProp: keyof Log = 'name';
    ascending = true;

    // Icons
    faSort = faSort;

    /**
     * Subscribe to the logsService to retrieve all logs
     */
    ngOnInit() {
        this.findAndExtractLoggers();
    }

    /**
     * Changes the log level for the log with the name {@param name}
     *
     * @param name  name of the log
     * @param level log level (TRACE, DEBUG, INFO, WARN, ERROR)
     */
    changeLevel(name: string, level: Level): void {
        this.logsService.changeLevel(name, level).subscribe(() => this.findAndExtractLoggers());
    }

    filterAndSort(): void {
        this.filteredAndOrderedLoggers = this.loggers!.filter((logger) => !this.filter || logger.name.toLowerCase().includes(this.filter.toLowerCase())).sort((a, b) => {
            if (a[this.orderProp] < b[this.orderProp]) {
                return this.ascending ? -1 : 1;
            } else if (a[this.orderProp] > b[this.orderProp]) {
                return this.ascending ? 1 : -1;
            } else if (this.orderProp === 'level') {
                return a.name < b.name ? -1 : 1;
            }
            return 0;
        });
    }

    private findAndExtractLoggers(): void {
        this.logsService.findAll().subscribe((response: LoggersResponse) => {
            this.loggers = Object.entries(response.loggers).map(([key, logger]) => new Log(key, logger.effectiveLevel));
            this.filterAndSort();
        });
    }
}
