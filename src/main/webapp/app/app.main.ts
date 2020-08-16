import './polyfills';
import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';
import { ProdConfig } from './core/config/prod.config';
import { ArtemisAppModule } from './app.module';

import * as Chart from 'chart.js';
import ChartDataLabels from 'chartjs-plugin-datalabels';

/**
 * This plugin registers itself globally,
 * meaning that once imported, all charts will display labels.
 * In our case we want it enabled only for a few charts, so we first need to unregister it globally
 */
Chart.plugins.unregister(ChartDataLabels);
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
