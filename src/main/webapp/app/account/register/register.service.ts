import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';
import { User } from 'app/core';

@Injectable()
export class Register {
    constructor(private http: HttpClient) {}

    save(account: User): Observable<void> {
        return this.http.post<void>(SERVER_API_URL + 'api/register', account);
    }
}
