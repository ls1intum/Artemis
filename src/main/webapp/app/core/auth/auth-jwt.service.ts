import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { Observable, of } from 'rxjs';

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
    private localStorageService = inject(LocalStorageService);
    private sessionStorageService = inject(SessionStorageService);

    login(credentials: Credentials): Observable<object> {
        return this.http.post('api/core/public/authenticate', credentials);
    }

    loginSAML2(rememberMe: boolean): Observable<object> {
        return this.http.post('api/core/public/saml2', rememberMe.toString());
    }

    logout(): Observable<object> {
        return this.http.post('api/core/public/logout', null);
    }

    /**
     * Clears all the caches, should be invoked during logout
     */
    clearCaches(): Observable<undefined> {
        this.localStorageService.clear();
        this.sessionStorageService.clear();
        // The local or session storage might have to be cleared asynchronously in future due to updated browser apis. This is why this method is already acting asynchronous.
        return of(undefined);
    }
}
