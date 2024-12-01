import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';

export class Credentials {
    constructor(
        public username: string,
        public password: string,
        public rememberMe: boolean,
    ) {}
}

export interface IAuthServerProvider {
    login: (credentials: Credentials) => Observable<any>;
    loginSAML2: (rememberMe: boolean) => Observable<any>;
    logout: () => Observable<any>;
    clearCaches: () => Observable<undefined>;
}

@Injectable({ providedIn: 'root' })
export class AuthServerProvider implements IAuthServerProvider {
    private http = inject(HttpClient);
    private localStorage = inject(LocalStorageService);
    private sessionStorage = inject(SessionStorageService);

    login(credentials: Credentials): Observable<object> {
        return this.http.post('api/public/authenticate', credentials);
    }

    loginSAML2(rememberMe: boolean): Observable<object> {
        return this.http.post('api/public/saml2', rememberMe.toString());
    }

    logout(): Observable<object> {
        return this.http.post('api/public/logout', null);
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
