import { Injectable, inject } from '@angular/core';
import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Observable } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { LoadingNotificationService } from 'app/shared/notification/loading-notification/loading-notification.service';

@Injectable()
export class LoadingNotificationInterceptor implements HttpInterceptor {
    private loadingNotificationService = inject(LoadingNotificationService);

    activeRequests = 0;

    /**
     * Identifies and handles a given HTTP request. If any HTTP request is sent we enable the loading screen and count up the active requests.
     * While all HTTP request complete we count down the active requests and when all HTTP requests are completed we disable the loading screen.
     * @param request The outgoing request object to handle.
     * @param next The next interceptor in the chain, or the server
     * if no interceptors remain in the chain.
     * @returns An observable of the event stream.
     */
    intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
        if (this.activeRequests === 0) {
            this.loadingNotificationService.startLoading();
        }
        this.activeRequests++;

        return next.handle(request).pipe(
            finalize(() => {
                this.activeRequests--;
                if (this.activeRequests === 0) {
                    this.loadingNotificationService.stopLoading();
                }
            }),
        );
    }
}
