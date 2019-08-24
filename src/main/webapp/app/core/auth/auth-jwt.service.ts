import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { of, Observable } from 'rxjs';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { SERVER_API_URL } from 'app/app.constants';

export interface Credentials {
    username: string | null;
    password: string | null;
    rememberMe: boolean;
}

export interface IAuthServerProvider {
    getToken: () => string;
    login: (credentials: Credentials) => Observable<string>;
    loginWithToken: (jwt: string, rememberMe: string) => Promise<string>;
    storeAuthenticationToken: (jwt: string, rememberMe: string) => void;
    removeAuthTokenFromCaches: () => Observable<null>;
}

@Injectable({ providedIn: 'root' })
export class AuthServerProvider implements IAuthServerProvider {
    constructor(private http: HttpClient, private $localStorage: LocalStorageService, private $sessionStorage: SessionStorageService) {}

    getToken() {
        return this.$localStorage.retrieve('authenticationToken') || this.$sessionStorage.retrieve('authenticationToken');
    }

    login(credentials: Credentials): Observable<string> {
        const data = {
            username: credentials.username,
            password: credentials.password,
            rememberMe: credentials.rememberMe,
        };
        return this.http.post(SERVER_API_URL + 'api/authenticate', data, { observe: 'response' }).map(authenticateSuccess.bind(this));

        /**
         * @function authenticateSuccess
         * @callback AuthServerProvider.login~authenticateSuccess
         * @param resp: response returned from the API
         * @desc This function is used as callback for when the authentication in the backend succeeds.
         * It prepares and stores the bearerToken in the local storage. This token is used for authentication
         * purposes and will be added to the header of every API call (via interceptors)
         */
        function authenticateSuccess(resp: HttpResponse<string>) {
            /**
             * Extract bearer token from response header
             */
            const bearerToken = resp.headers.get('Authorization');
            if (bearerToken && bearerToken.slice(0, 7) === 'Bearer ') {
                const jwt = bearerToken.slice(7, bearerToken.length);
                this.storeAuthenticationToken(jwt, credentials.rememberMe);
                return jwt;
            }
        }
    }

    loginWithToken(jwt: string, rememberMe: string) {
        if (jwt) {
            this.storeAuthenticationToken(jwt, rememberMe);
            return Promise.resolve(jwt);
        } else {
            return Promise.reject('auth-jwt-service Promise reject'); // Put appropriate error message here
        }
    }

    storeAuthenticationToken(jwt: string, rememberMe: string) {
        if (rememberMe) {
            this.$localStorage.store('authenticationToken', jwt);
        } else {
            this.$sessionStorage.store('authenticationToken', jwt);
        }
    }

    /**
     * Removes the user's auth tokens from the browser's caches.
     * This will lead to all endpoint requests failing with a 401.
     */
    removeAuthTokenFromCaches(): Observable<null> {
        this.$localStorage.clear('authenticationToken');
        this.$sessionStorage.clear('authenticationToken');
        // The local or session storage might have to be cleared asynchronously in future due to updated browser apis. This is why this method is already acting if it was asynchronous.
        return of(null);
    }
}
