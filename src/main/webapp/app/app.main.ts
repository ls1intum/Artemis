import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';
import { ProdConfig } from './blocks/config/prod.config';
import { ArTeMiSAppModule } from './app.module';

ProdConfig();

if (module['hot']) {
    module['hot'].accept();
}

platformBrowserDynamic()
    .bootstrapModule(ArTeMiSAppModule, { preserveWhitespaces: true })
    .then(platformRef => {})
    .catch(err => console.error(err));
