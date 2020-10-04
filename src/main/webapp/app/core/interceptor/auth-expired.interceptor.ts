import { Injectable } from '@angular/core';
import { HttpErrorResponse, HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { LoginService } from 'app/core/login/login.service';

@Injectable()
export class AuthExpiredInterceptor implements HttpInterceptor {
    constructor(private loginService: LoginService) {}

    /**
     * Identifies and handles a given HTTP request. If the request's error status is 401, the current user will be logged out.
     * @param request The outgoing request object to handle.
     * @param next The next interceptor in the chain, or the server
     * if no interceptors remain in the chain.
     * @returns An observable of the event stream.
     */
    intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
        return next.handle(request).pipe(
            tap(
                () => {},
                (err: any) => {
                    if (err instanceof HttpErrorResponse) {
                        if (err.status === 401) {
                            this.loginService.logout();
                        }
                    }
                },
            ),
        );
    }
}
