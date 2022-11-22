import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ActivateService {
    constructor(private http: HttpClient) {}

    /**
     * Sends request to the server to activate the user
     * @param key the activation key
     */
    get(key: string): Observable<{}> {
        return this.http.get(SERVER_API_URL + 'api/public/activate', {
            params: new HttpParams().set('key', key),
        });
    }
}
