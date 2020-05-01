import { Injectable } from '@angular/core';
import { JhiEventManager } from 'ng-jhipster';
import { HttpErrorResponse, HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';

@Injectable()
export class ErrorHandlerInterceptor implements HttpInterceptor {
    constructor(private eventManager: JhiEventManager) {}

    /**
     * Identifies and handles a given HTTP request. If the request's error status is not 401 and the error message is empty
     * or the error url includes '/api/account' the httpError is broadcasted to the observer.
     * @param request The outgoing request object to handle.
     * @param next The next interceptor in the chain, or the backend
     * if no interceptors remain in the chain.
     * @returns An observable of the event stream.
     */
    intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
        return next.handle(request).pipe(
            tap(
                (event: HttpEvent<any>) => {},
                (err: any) => {
                    if (err instanceof HttpErrorResponse) {
                        if (!(err.status === 401 && (err.message === '' || (err.url && err.url.includes('/api/account'))))) {
                            this.eventManager.broadcast({ name: 'artemisApp.httpError', content: err });
                        }
                    }
                },
            ),
        );
    }
}
