import { JhiAlertService, JhiConfigService } from 'ng-jhipster';
import { Injectable, Optional } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { DomSanitizer } from '@angular/platform-browser';

@Injectable({
    providedIn: 'root',
})
export class AlertService extends JhiAlertService {
    // TODO: this is a temporary workaround, because the super class for some reason does not get injected a proper Sanitizer object which leads to weired errors.
    // We should check again if this is really necessary, after ng-jhipster fully supports Angular 9

    mySanitizer: DomSanitizer;

    constructor(sanitizer: DomSanitizer, configService: JhiConfigService, @Optional() translateService: TranslateService) {
        super(sanitizer, configService, translateService);
        this.mySanitizer = sanitizer;
    }
}
