import { Inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { SERVER_API_URL } from '../../app.constants';
import { NG1AUTH_SERVICE } from './ng1-auth-wrapper.service';

@Injectable()
export class AuthServerProvider {
    constructor(
        private http: HttpClient,
        private $localStorage: LocalStorageService,
        private $sessionStorage: SessionStorageService,
        @Inject(NG1AUTH_SERVICE) private ng1AuthService: any) {
    }

    getToken() {
        return this.$localStorage.retrieve('authenticationToken') || this.$sessionStorage.retrieve('authenticationToken');
    }

    login(credentials): Observable<any> {

        const data = {
            username: credentials.username,
            password: credentials.password,
            rememberMe: credentials.rememberMe
        };
        return this.http.post(SERVER_API_URL + 'api/authenticate', data, {observe : 'response'}).map(authenticateSuccess.bind(this));

        /**
         * @function authenticateSuccess
         * @callback AuthServerProvider.login~authenticateSuccess
         * @param resp: response returned from the API
         * @desc This function is used as callback for when the authentication in the backend succeeds.
         * It does two things:
         * 1) It prepares and stores the bearerToken in the local storage. This token is used for authentication
         * purposes and will be added to the header of every API call (via interceptors)
         * 2) It calls the login function in the upgraded authentication service from the legacy app with the
         * the provided credentials. By chaining the ng1 login to the authentication success callback, we assure
         * that both applications in our setup are logged in, have a authToken and can therefore make API calls
         * without running into "403 - Forbidden" errors.
         */
        function authenticateSuccess(resp) {
            /**
             * Extract bearer token from response header
             */
            const bearerToken = resp.headers.get('Authorization');
            if (bearerToken && bearerToken.slice(0, 7) === 'Bearer ') {
                const jwt = bearerToken.slice(7, bearerToken.length);
                this.storeAuthenticationToken(jwt, credentials.rememberMe);
                /**
                 * Call ng1 AuthService with same credentials
                 * This won't fail because we already know that the credentials are valid!
                 * We do this inside the authenticateSucess callback to avoid unnecessary API calls when the
                 * authorization fails in the first place (since both apps authorize against the same backend with
                 * identical credentials).
                 */
                this.ng1AuthService.login(credentials, jwt);
                return jwt;
            }
        }
    }

    loginWithToken(jwt, rememberMe) {
        if (jwt) {
            this.storeAuthenticationToken(jwt, rememberMe);
            return Promise.resolve(jwt);
        } else {
            return Promise.reject('auth-jwt-service Promise reject'); // Put appropriate error message here
        }
    }

    storeAuthenticationToken(jwt, rememberMe) {
        if (rememberMe) {
            this.$localStorage.store('authenticationToken', jwt);
        } else {
            this.$sessionStorage.store('authenticationToken', jwt);
        }
    }

    logout(): Observable<any> {
        return new Observable(observer => {
            this.ng1AuthService.logout();
            this.$localStorage.clear('authenticationToken');
            this.$sessionStorage.clear('authenticationToken');
            /**
             * Manually delete the token from the local and session storage
             * to ensure that the token is cleared. This is due to the hybrid setup
             * since the ng1 application also stores the tokens.
             */
            localStorage.removeItem('jhi-authenticationToken');
            sessionStorage.removeItem('jhi-authenticationToken');
            localStorage.clear();
            sessionStorage.clear();
            observer.complete();
        });
    }
}
