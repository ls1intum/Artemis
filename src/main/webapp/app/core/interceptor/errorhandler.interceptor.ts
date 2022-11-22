import { Injectable } from '@angular/core';
import { HttpErrorResponse, HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { EventManager } from 'app/core/util/event-manager.service';

@Injectable()
export class ErrorHandlerInterceptor implements HttpInterceptor {
    constructor(private eventManager: EventManager) {}

    /**
     * Identifies and handles a given HTTP request. If the request's error status is not 401 and the error message is empty
     * or the error url includes '/api/public/account' or '/api/account' the httpError is broadcasted to the observer.
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
                        if (!(err.status === 401 && (err.message === '' || (err.url && err.url.match('/api(/public)?/account'))))) {
                            this.eventManager.broadcast({ name: 'artemisApp.httpError', content: err });
                        }
                    }
                },
            }),
        );
    }
}
