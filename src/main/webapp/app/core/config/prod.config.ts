import { enableProdMode } from '@angular/core';
import { DEBUG_INFO_ENABLED } from 'app/app.constants';

/**
 * Disable debug data on prod profile to improve performance
 */
export function ProdConfig() {
    if (!DEBUG_INFO_ENABLED) {
        enableProdMode();
    }
}
