import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { LoggersResponse, Level } from './log.model';
import { SERVER_API_URL } from 'app/app.constants';

@Injectable({ providedIn: 'root' })
export class LogsService {
    constructor(private http: HttpClient) {}

    changeLevel(name: string, configuredLevel: Level): Observable<{}> {
        return this.http.post(SERVER_API_URL + 'management/loggers/' + name, { configuredLevel });
    }

    /**
     * Sends a GET request to retrieve all logs
     */
    findAll(): Observable<LoggersResponse> {
        return this.http.get<LoggersResponse>(SERVER_API_URL + 'management/loggers');
    }
}
