import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Level, LoggersResponse } from './log.model';

@Injectable({ providedIn: 'root' })
export class LogsService {
    private http = inject(HttpClient);

    changeLevel(name: string, configuredLevel: Level): Observable<any> {
        return this.http.post(`management/loggers/${name}`, { configuredLevel });
    }

    /**
     * Sends a GET request to retrieve all logs
     */
    findAll(): Observable<LoggersResponse> {
        return this.http.get<LoggersResponse>('management/loggers');
    }
}
