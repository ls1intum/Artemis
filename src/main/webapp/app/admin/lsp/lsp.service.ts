import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { LspStatus } from 'app/entities/lsp-status.model';

const resourceUrl = 'api/lsp/';

@Injectable({ providedIn: 'root' })
export class LspService {
    constructor(private http: HttpClient) {}

    /**
     * Retrieves a list of registered LSP servers
     */
    getLspServers(): Observable<Array<string>> {
        return this.http.get<Array<string>>(resourceUrl + 'list', {
            headers: { 'Content-Type': 'application/json', observe: 'response' },
        });
    }

    /**
     * Retrieves the status of all registered LSP servers
     */
    getLspServersStatus(updateMetrics: boolean): Observable<Array<LspStatus>> {
        return this.http.get<Array<LspStatus>>(resourceUrl + 'status', {
            headers: { 'Content-Type': 'application/json', observe: 'response' },
            params: { update: updateMetrics },
        });
    }

    /**
     * Adds a new LSP Server
     * @param serverUrl of the server to add
     */
    addServer(serverUrl: string) {
        return this.http.post<LspStatus>(
            resourceUrl + `add`,
            {},
            {
                params: new HttpParams().set('monacoServerUrl', serverUrl),
            },
        );
    }

    /**
     * Requests to pause/resume a given LSP Server
     * @param serverUrl of the server to pause/resume
     */
    pauseServer(serverUrl: string) {
        return this.http.put<boolean>(
            resourceUrl + `pause`,
            {},
            {
                params: new HttpParams().set('monacoServerUrl', serverUrl),
            },
        );
    }
}
