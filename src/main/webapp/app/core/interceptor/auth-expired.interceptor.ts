import { Injectable, inject } from '@angular/core';
import { HttpErrorResponse, HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { LoginService } from 'app/core/login/login.service';
import { Router } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';

@Injectable()
export class AuthExpiredInterceptor implements HttpInterceptor {
    private loginService = inject(LoginService);
    private sessionStorageService = inject(SessionStorageService);
    private router = inject(Router);
    private accountService = inject(AccountService);

    /**
     * Identifies and handles a given HTTP request. If the request's error status is 401, the current user will be logged out.
     * @param request The outgoing request object to handle.
     * @param next The next interceptor in the chain, or the server
     * if no interceptors remain in the chain.
     * @returns An observable of the event stream.
     */
    intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
        return next.handle(request).pipe(
            tap({
                error: (err: any) => {
                    if (err instanceof HttpErrorResponse) {
                        if (err.status === 401 && this.accountService.isAuthenticated()) {
                            // save the url before the logout navigates to another page in a constant
                            const currentUrl = this.router.routerState.snapshot.url;
                            this.loginService.logout(false);

                            // TODO: logging out the user automatically interferes with the canDeactivate functionality.
                            // In such a case the error message should be different or we should even send one additional message to the user
                            // store url so that the user could navigate directly to it after login
                            // store the url in the session storage after the logout, because the logout will redirect the user
                            this.sessionStorageService.store('previousUrl', currentUrl);
                        }
                    }
                },
            }),
        );
    }
}
