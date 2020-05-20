import { Injectable } from '@angular/core';
import { CookieService } from 'ngx-cookie-service';

@Injectable({ providedIn: 'root' })
export class CSRFService {
    constructor(private cookieService: CookieService) {}

    /** gets the CSRF token */
    getCSRF(name = 'XSRF-TOKEN') {
        return this.cookieService.get(name);
    }
}
