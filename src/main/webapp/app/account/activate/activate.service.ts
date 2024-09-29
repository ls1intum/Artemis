import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ActivateService {
    private http = inject(HttpClient);

    /**
     * Sends request to the server to activate the user
     * @param key the activation key
     */
    get(key: string): Observable<object> {
        return this.http.get('api/public/activate', {
            params: new HttpParams().set('key', key),
        });
    }
}
