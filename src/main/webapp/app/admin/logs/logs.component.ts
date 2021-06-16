import { Component, OnInit } from '@angular/core';
import { Log } from 'app/admin/logs/log.model';
import { LogsService } from 'app/admin/logs/logs.service';

@Component({
    selector: 'jhi-logs',
    templateUrl: './logs.component.html',
    styleUrls: ['./logs.component.scss'],
})
export class LogsComponent implements OnInit {
    loggers: Log[];
    filter: string;
    orderProp: string;
    reverse: boolean;

    constructor(private logsService: LogsService) {
        this.filter = '';
        this.orderProp = 'name';
        this.reverse = false;
    }

    /**
     * Subscribe to the logsService to retrieve all logs
     */
    ngOnInit() {
        this.logsService.findAll().subscribe((response) => (this.loggers = response.body!));
    }

    /**
     * Changes the log level for the log with the name {@param name}
     *
     * @param name  name of the log
     * @param level log level (TRACE, DEBUG, INFO, WARN, ERROR)
     */
    changeLevel(name: string, level: string) {
        const log = new Log(name, level);
        this.logsService.changeLevel(log).subscribe(() => {
            this.logsService.findAll().subscribe((response) => (this.loggers = response.body!));
        });
    }
}
