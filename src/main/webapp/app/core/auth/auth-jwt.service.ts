import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

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

    login(credentials: Credentials): Observable<object> {
        return this.http.post('api/core/public/authenticate', credentials);
    }

    loginSAML2(rememberMe: boolean): Observable<object> {
        return this.http.post('api/core/public/saml2', rememberMe.toString());
    }

    logout(): Observable<object> {
        return this.http.post('api/core/public/logout', null);
    }
}
