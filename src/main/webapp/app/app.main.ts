import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from 'app/app.config';
import { MonacoConfig } from 'app/core/config/monaco.config';
import { ProdConfig } from 'app/core/config/prod.config';
import { AppComponent } from './app.component';
import { FaIconLibrary } from '@fortawesome/angular-fontawesome';
import { registerLocaleData } from '@angular/common';
import locale from '@angular/common/locales/en';
import { NgbTooltipConfig } from '@ng-bootstrap/ng-bootstrap';
import { artemisIconPack } from 'app/foundation/icons/icons';

ProdConfig();
MonacoConfig();

bootstrapApplication(AppComponent, appConfig)
    .then((app) => {
        // TODO: potentially move this code into AppComponent
        const library = app.injector.get(FaIconLibrary);
        library.addIconPacks(artemisIconPack);
        const tooltipConfig = app.injector.get(NgbTooltipConfig);
        const breakpointObserver = app.injector.get(BreakpointObserver);

        // Perform initialization logic
        registerLocaleData(locale);
        // Attach all ng-bootstrap tooltips to <body> so they are not clipped by `overflow: hidden` ancestors
        // (e.g. the flex sidebar / layout-content), and suppress them on touch (Handset) devices.
        // ~140 [ngbTooltip] usages still rely on this global default until they are migrated to PrimeNG p-tooltip.
        tooltipConfig.container = 'body';
        tooltipConfig.disableTooltip = breakpointObserver.isMatched(Breakpoints.Handset);
    })
    // eslint-disable-next-line no-undef
    .catch((err) => console.error(err));
