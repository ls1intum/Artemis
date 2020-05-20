import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';

@Injectable({ providedIn: 'root' })
export class PasswordResetFinishService {
    constructor(private http: HttpClient) {}

    /** saves the new password after reset
     * @param keyAndPassword - the new password to be saved
     * */
    save(keyAndPassword: any): Observable<any> {
        return this.http.post(SERVER_API_URL + 'api/account/reset-password/finish', keyAndPassword);
    }
}
