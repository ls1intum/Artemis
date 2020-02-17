import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from 'app/app.constants';
import { Log } from 'app/admin/logs/log.model';

@Injectable({ providedIn: 'root' })
export class LogsService {
    constructor(private http: HttpClient) {}

    /**
     * Sends a PUT request to change the log level for the given log
     * @param log for which the log level should be changed
     */
    changeLevel(log: Log): Observable<HttpResponse<void>> {
        return this.http.put<void>(SERVER_API_URL + 'management/logs', log, { observe: 'response' });
    }

    /**
     * Sends a GET request to retrieve all logs
     */
    findAll(): Observable<HttpResponse<Log[]>> {
        return this.http.get<Log[]>(SERVER_API_URL + 'management/logs', { observe: 'response' });
    }
}
