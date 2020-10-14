import { JhiAlertService, JhiConfigService } from 'ng-jhipster';
import { Injectable, NgZone, Optional } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { DomSanitizer } from '@angular/platform-browser';

@Injectable({
    providedIn: 'root',
})
export class AlertService extends JhiAlertService {
    // TODO: this is a temporary workaround, because the super class for some reason does not get injected a proper Sanitizer object which leads to weired errors.
    // We should check again if this is really necessary, after ng-jhipster fully supports Angular 9

    mySanitizer: DomSanitizer;

    constructor(sanitizer: DomSanitizer, configService: JhiConfigService, ngZone: NgZone, @Optional() translateService: TranslateService) {
        // @ts-ignore
        super(sanitizer, configService, ngZone, translateService);
        this.mySanitizer = sanitizer;
    }
}
