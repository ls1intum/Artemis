import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from 'app/app.constants';

@Injectable({ providedIn: 'root' })
export class ActivateService {
    constructor(private http: HttpClient) {}

    /**
     * Sends request to the server to activate the user
     * @param key the activation key
     */
    get(key: string): Observable<void> {
        return this.http.get<void>(SERVER_API_URL + 'api/activate', {
            params: new HttpParams().set('key', key),
        });
    }
}
