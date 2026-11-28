import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from 'app/app.config';
import { MonacoConfig } from 'app/core/config/monaco.config';
import { ProdConfig } from 'app/core/config/prod.config';
import { AppComponent } from './app.component';
import { captureException } from 'app/core/error-reporting/capture-exception';
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
    .catch((err) => {
        // This is the last-resort handler for a failed application bootstrap. The application is not running, so there is no
        // error handler and the user sees nothing: the console is the channel that surfaces a startup failure to a developer, and the
        // report is the one that reaches an operator. Reporting does not depend on the application, it only needs `fetch`.
        // eslint-disable-next-line no-console, no-undef -- bootstrap failure handler
        console.error('Failed to bootstrap the Angular application', err);
        captureException(err, { severity: 'CRITICAL' });
    });
