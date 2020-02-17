import { JhiAlertService } from 'ng-jhipster';
import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';

@Injectable()
export class NotificationInterceptor implements HttpInterceptor {
    constructor(private alertService: JhiAlertService) {}

    intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
        return next.handle(request).pipe(
            tap((event: HttpEvent<any>) => {
                if (event instanceof HttpResponse) {
                    let alert: string | null = null;
                    let alertParams: string | null = null;

                    event.headers.keys().forEach(entry => {
                        if (entry.toLowerCase().endsWith('app-alert')) {
                            alert = event.headers.get(entry);
                        } else if (entry.toLowerCase().endsWith('app-params')) {
                            alertParams = decodeURIComponent(event.headers.get(entry)!.replace(/\+/g, ' '));
                        }
                    });

                    // TODO: deactivate due to problems in ng-jhipster with the new Angular 9 configuration
                    // if (alert) {
                    // this.alertService.success(alert, { param: alertParams });
                    // }
                }
            }),
        );
    }
}
