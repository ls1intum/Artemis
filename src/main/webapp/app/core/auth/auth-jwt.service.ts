import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';

export class Credentials {
    constructor(public username: string, public password: string, public rememberMe: boolean) {}
}

export interface IAuthServerProvider {
    login: (credentials: Credentials) => Observable<Object>;
    loginSAML2: (rememberMe: boolean) => Observable<Object>;
    logout: () => Observable<Object>;
    clearCaches: () => Observable<undefined>;
}

@Injectable({ providedIn: 'root' })
export class AuthServerProvider implements IAuthServerProvider {
    constructor(private http: HttpClient, private localStorage: LocalStorageService, private sessionStorage: SessionStorageService) {}

    login(credentials: Credentials): Observable<Object> {
        return this.http.post(SERVER_API_URL + 'api/authenticate', credentials);
    }

    loginSAML2(rememberMe: boolean): Observable<Object> {
        return this.http.post(SERVER_API_URL + 'api/saml2', rememberMe.toString());
    }

    logout(): Observable<Object> {
        return this.http.post(SERVER_API_URL + 'api/logout', null);
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
