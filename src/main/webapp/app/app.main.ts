import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from 'app/app.config';
import { MonacoConfig } from 'app/core/config/monaco.config';
import { ProdConfig } from 'app/core/config/prod.config';
import { AppComponent } from './app.component';
import { FaIconLibrary } from '@fortawesome/angular-fontawesome';
import { registerLocaleData } from '@angular/common';
import locale from '@angular/common/locales/en';
import { artemisIconPack } from 'app/foundation/icons/icons';

ProdConfig();
MonacoConfig();

bootstrapApplication(AppComponent, appConfig)
    .then((app) => {
        // TODO: potentially move this code into AppComponent
        const library = app.injector.get(FaIconLibrary);
        library.addIconPacks(artemisIconPack);

        // Perform initialization logic
        registerLocaleData(locale);
    })
    // eslint-disable-next-line no-undef
    .catch((err) => console.error(err));
