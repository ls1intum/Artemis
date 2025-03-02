import '@analogjs/vite-plugin-angular/setup-vitest';

import { vi } from 'vitest';
import { BrowserDynamicTestingModule, platformBrowserDynamicTesting } from '@angular/platform-browser-dynamic/testing';
import { getTestBed } from '@angular/core/testing';

// NOTE: this avoids an es6 module issue with tslib resulting in "SyntaxError: Unexpected token 'export'
// "Module /Users/krusche/Projects/Artemis/node_modules/tslib/tslib.es6.mjs:24 seems to be an ES Module but shipped in a CommonJS package.
// You might want to create an issue to the package "tslib" asking them to ship the file in .mjs extension or add "type": "module" in their package.json
vi.mock('@fingerprintjs/fingerprintjs', () => ({
    default: {
        load: async () => ({
            get: async () => ({ visitorId: 'mock-visitor-id' }),
        }),
    },
}));

getTestBed().initTestEnvironment(BrowserDynamicTestingModule, platformBrowserDynamicTesting());
