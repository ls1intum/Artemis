import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { SERVER_API_URL } from '../../app.constants';
import { JhiAlertService } from 'ng-jhipster';

export interface Credentials {
    username: string;
    password: string;
    rememberMe: boolean;
}

@Injectable({ providedIn: 'root' })
export class AuthServerProvider {
    constructor(
        private http: HttpClient,
        private $localStorage: LocalStorageService,
        private $sessionStorage: SessionStorageService,
        private jhiAlertService: JhiAlertService
    ) {}

    getToken() {
        return this.$localStorage.retrieve('authenticationToken') || this.$sessionStorage.retrieve('authenticationToken');
    }

    login(credentials: Credentials): Observable<string> {
        const data = {
            username: credentials.username,
            password: credentials.password,
            rememberMe: credentials.rememberMe
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

    logout(): Observable<any> {
        return new Observable(observer => {
            this.$localStorage.clear('authenticationToken');
            this.$sessionStorage.clear('authenticationToken');
            observer.complete();
            // clear notifications on logout
            this.jhiAlertService.clear();
        });
    }
}
