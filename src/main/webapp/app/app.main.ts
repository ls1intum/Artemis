import './polyfills';
import 'app/shared/util/array.extension';
import 'app/shared/util/map.extension';
import 'app/shared/util/string.extension';
import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';
import { ProdConfig } from './core/config/prod.config';
import { ArtemisAppModule } from './app.module';

ProdConfig();

if (module['hot']) {
    module['hot'].accept();
    if ('production' !== process.env.NODE_ENV) {
        console.clear();
    }
}

platformBrowserDynamic()
    .bootstrapModule(ArtemisAppModule, { preserveWhitespaces: true })
    .then(() => {})
    .catch((err) => console.error(err));
