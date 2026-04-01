import { Injectable, inject } from '@angular/core';
import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Observable } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { LoadingNotificationService } from 'app/core/loading-notification/loading-notification.service';

@Injectable()
export class LoadingNotificationInterceptor implements HttpInterceptor {
    private loadingNotificationService = inject(LoadingNotificationService);

    activeRequests = 0;

    /**
     * Identifies and handles a given HTTP request. If any HTTP request is sent we enable the loading screen and count up the active requests.
     * While all HTTP request complete we count down the active requests and when all HTTP requests are completed we disable the loading screen.
     * File requests (PDFs, attachments, blobs) are excluded from loading tracking to prevent UI thrashing during streaming.
     * @param request The outgoing request object to handle.
     * @param next The next interceptor in the chain, or the server
     * if no interceptors remain in the chain.
     * @returns An observable of the event stream.
     */
    intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
        // Skip loading indicator for PDF and file streaming to avoid UI thrashing
        const isFileRequest = request.url.includes('/files/') || request.url.includes('/attachments/') || request.responseType === 'blob' || request.responseType === 'arraybuffer';
        if (!isFileRequest) {
            if (this.activeRequests === 0) {
                this.loadingNotificationService.startLoading();
            }
            this.activeRequests++;
        }
        return next.handle(request).pipe(
            finalize(() => {
                if (!isFileRequest) {
                    this.activeRequests--;
                    if (this.activeRequests === 0) {
                        this.loadingNotificationService.stopLoading();
                    }
                }
            }),
        );
    }
}
