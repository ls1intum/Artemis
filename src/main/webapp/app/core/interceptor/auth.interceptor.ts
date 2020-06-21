import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { isRequestToArtemisServer } from './interceptor.util';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {
    constructor(private localStorage: LocalStorageService, private sessionStorage: SessionStorageService) {}

    /**
     * Identifies and handles a given HTTP request. If the request is valid, add a authenticationToken from localStorage or sessionStorage
     * and pass on to next.
     * @param request The outgoing request object to handle.
     * @param next The next interceptor in the chain, or the backend
     * if no interceptors remain in the chain.
     * @returns An observable of the event stream.
     */
    intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
        if (isRequestToArtemisServer(request)) {
            const token = this.localStorage.retrieve('authenticationToken') || this.sessionStorage.retrieve('authenticationToken');
            if (token) {
                request = request.clone({
                    setHeaders: {
                        Authorization: 'Bearer ' + token,
                    },
                });
            }
        }

        return next.handle(request);
    }
}
