import './polyfills';
import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';
import { ProdConfig } from './core/config/prod.config';
import { ArtemisAppModule } from './app.module';
import { Workbox } from 'workbox-window';
import { WorkboxLifecycleEvent } from 'workbox-window/utils/WorkboxEvent';

ProdConfig();

if (module['hot']) {
    module['hot'].accept();
    if ('production' !== process.env.NODE_ENV) {
        console.clear();
    }
}

platformBrowserDynamic()
    .bootstrapModule(ArtemisAppModule, { preserveWhitespaces: true })
    .then(() => {
        if ('serviceWorker' in navigator) {
            const wb = new Workbox('service-worker.js');

            wb.addEventListener('installed', (event: WorkboxLifecycleEvent) => {
                console.log('installed sw');
                if (event.isUpdate) {
                    // TODO: change this in production
                    console.log('just change somethig for test');
                    if (confirm(`New content is available!. Click OK to refresh`)) {
                        window.location.reload();
                    }
                }
            });

            wb.register();
        }
    })
    .catch((err) => console.error(err));
