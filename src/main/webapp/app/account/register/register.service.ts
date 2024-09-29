import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { User } from 'app/core/user/user.model';

@Injectable({ providedIn: 'root' })
export class RegisterService {
    private http = inject(HttpClient);

    /**
     * Registers a new user. This is only possible if the password is long enough and there is no other user with the
     * same username or e-mail.
     *
     * @param account The data object holding the information about the new user
     */
    save(account: User): Observable<void> {
        return this.http.post<void>('api/public/register', account);
    }
}
