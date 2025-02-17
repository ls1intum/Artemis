import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { AlertService } from 'app/core/util/alert.service';

@Injectable()
export class NotificationInterceptor implements HttpInterceptor {
    private alertService = inject(AlertService);

    /**
     * Identifies and handles a given HTTP request. If the event is a HttpResponse and contains an alert, the alert
     * and its parameters are broadcasted to the AlertService.
     * @param request The outgoing request object to handle.
     * @param next The next interceptor in the chain, or the server
     * if no interceptors remain in the chain.
     * @returns An observable of the event stream.
     */
    intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
        return next.handle(request).pipe(
            tap((event: HttpEvent<any>) => {
                if (event instanceof HttpResponse) {
                    let alert: string | null = null;
                    let alertParams: string | null = null;

                    event.headers.keys().forEach((entry) => {
                        if (entry.toLowerCase().endsWith('app-alert')) {
                            alert = event.headers.get(entry);
                        } else if (entry.toLowerCase().endsWith('app-params')) {
                            alertParams = decodeURIComponent(event.headers.get(entry)!.replace(/\+/g, ' '));
                        }
                    });

                    if (alert) {
                        this.alertService.success(alert, { param: alertParams });
                    }
                }
            }),
        );
    }
}
