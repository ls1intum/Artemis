import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';
import { ProdConfig } from './blocks/config/prod.config';
import { ArTEMiSAppModule } from './app.module';

ProdConfig();

    if (module['hot']) {
        module['hot'].accept();
    }

    platformBrowserDynamic().bootstrapModule(ArTEMiSAppModule)
    .then(platformRef => {})
    .catch(err => console.error(err));
