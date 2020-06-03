import { Injectable } from '@angular/core';
import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Observable } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { LoadingNotificationService } from 'app/shared/notification/loading-notification/loading-notification.service';

@Injectable()
export class LoadingNotificationInterceptor implements HttpInterceptor {
    activeRequests = 0;

    constructor(private loadingNotificationService: LoadingNotificationService) {}

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
