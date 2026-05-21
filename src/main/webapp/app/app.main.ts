import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from 'app/app.config';
import { MonacoConfig } from 'app/core/config/monaco.config';
import { ProdConfig } from 'app/core/config/prod.config';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { AppComponent } from './app.component';
import { FaIconLibrary } from '@fortawesome/angular-fontawesome';
import { registerLocaleData } from '@angular/common';
import locale from '@angular/common/locales/en';
import dayjs from 'dayjs/esm';
import { NgbDatepickerConfig, NgbTooltipConfig } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { artemisIconPack } from 'app/shared/icons/icons';

ProdConfig();
MonacoConfig();

bootstrapApplication(AppComponent, appConfig)
    .then((app) => {
        // TODO: potentially move this code into AppComponent
        const library = app.injector.get(FaIconLibrary);
        library.addIconPacks(artemisIconPack);
        const dpConfig = app.injector.get(NgbDatepickerConfig);
        const tooltipConfig = app.injector.get(NgbTooltipConfig);
        const translateService = app.injector.get(TranslateService);
        const languageHelper = app.injector.get(JhiLanguageHelper);
        const sessionStorageService = app.injector.get(SessionStorageService);
        const breakpointObserver = app.injector.get(BreakpointObserver);

        // Perform initialization logic
        registerLocaleData(locale);
        dpConfig.minDate = { year: dayjs().subtract(100, 'year').year(), month: 1, day: 1 };
        translateService.setFallbackLang('en');
        const languageKey: string = sessionStorageService.retrieve('locale') || languageHelper.determinePreferredLanguage();
        translateService.use(languageKey);
        tooltipConfig.container = 'body';

        tooltipConfig.disableTooltip = breakpointObserver.isMatched(Breakpoints.Handset);
    })
    // eslint-disable-next-line no-undef
    .catch((err) => console.error(err));
