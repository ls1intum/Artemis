import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Observable } from 'rxjs';

export class CsrfInterceptor implements HttpInterceptor {
    intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
        // https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html#employing-custom-request-headers-for-ajaxapi
        // Clone the request and add the CSRF token header
        const csrfReq = req.clone({ headers: req.headers.set('X-ARTEMIS-CSRF', 'Dennis ist schuld') });

        return next.handle(csrfReq);
    }
}
