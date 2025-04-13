import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from 'app/app.config';
import { MonacoConfig } from 'app/core/config/monaco.config';
import { ProdConfig } from 'app/core/config/prod.config';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { AppComponent } from './app.component';
import { FaIconLibrary } from '@fortawesome/angular-fontawesome';
import isMobile from 'ismobilejs-es5';
import { registerLocaleData } from '@angular/common';
import locale from '@angular/common/locales/en';
import dayjs from 'dayjs/esm';
import { NgbDatepickerConfig, NgbTooltipConfig } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { SessionStorageService } from 'ngx-webstorage';
import { artemisIconPack } from 'app/shared/icons/icons';
import { inject } from '@angular/core';

ProdConfig();
MonacoConfig();

bootstrapApplication(AppComponent, appConfig)
    .then(() => {
        // TODO: potentially move this code into AppComponent
        const library = inject(FaIconLibrary);
        library.addIconPacks(artemisIconPack);
        const dpConfig = inject(NgbDatepickerConfig);
        const tooltipConfig = inject(NgbTooltipConfig);
        const translateService = inject(TranslateService);
        const languageHelper = inject(JhiLanguageHelper);
        const sessionStorageService = inject(SessionStorageService);

        // Perform initialization logic
        registerLocaleData(locale);
        dpConfig.minDate = { year: dayjs().subtract(100, 'year').year(), month: 1, day: 1 };
        translateService.setDefaultLang('en');
        const languageKey = sessionStorageService.retrieve('locale') || languageHelper.determinePreferredLanguage();
        translateService.use(languageKey);
        tooltipConfig.container = 'body';
        if (isMobile(window.navigator.userAgent).any ?? false) {
            tooltipConfig.disableTooltip = true;
        }
    })
    // eslint-disable-next-line no-undef
    .catch((err) => console.error(err));
