import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { map } from 'rxjs/operators';

export class Credentials {
    constructor(public username: string, public password: string, public rememberMe: boolean) {}
}

export interface IAuthServerProvider {
    getToken: () => string;
    login: (credentials: Credentials) => Observable<void>;
    logout: () => Observable<void>;
    clearCaches: () => Observable<undefined>;
}

@Injectable({ providedIn: 'root' })
export class AuthServerProvider implements IAuthServerProvider {
    constructor(private http: HttpClient, private localStorage: LocalStorageService, private sessionStorage: SessionStorageService) {}

    getToken() {
        return this.localStorage.retrieve('authenticationToken') || this.sessionStorage.retrieve('authenticationToken');
    }

    login(credentials: Credentials): Observable<void> {
        return this.http.post(SERVER_API_URL + 'api/authenticate', credentials).pipe(map(() => console.log('')));
    }

    loginSAML2(rememberMe: boolean): Observable<void> {
        return this.http.post(SERVER_API_URL + 'api/saml2', rememberMe.toString()).pipe(map(() => console.log('')));
    }

    logout(): Observable<void> {
        return this.http.post(SERVER_API_URL + 'api/logout', null).pipe(map(() => console.log('')));
    }

    /**
     * Clears all the caches, should be invoked during logout
     */
    clearCaches(): Observable<undefined> {
        this.localStorage.clear();
        this.sessionStorage.clear();
        // The local or session storage might have to be cleared asynchronously in future due to updated browser apis. This is why this method is already acting asynchronous.
        return of(undefined);
    }
}
