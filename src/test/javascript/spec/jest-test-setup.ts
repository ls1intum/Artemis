// import 'core-js';
// import 'zone.js/dist/zone';
// import 'zone.js/dist/long-stack-trace-zone';
// import 'zone.js/dist/async-test';
// import 'zone.js/dist/fake-async-test';
// import 'zone.js/dist/sync-test';
// import 'zone.js/dist/proxy';
// import 'zone.js/dist/jasmine-patch';
// import 'rxjs';
// import { TestBed } from '@angular/core/testing';
// import { BrowserDynamicTestingModule, platformBrowserDynamicTesting } from '@angular/platform-browser-dynamic/testing';

// import './jest-global-mocks';
import 'jest-canvas-mock';
import 'app/shared/util/array.extension';
import 'app/core/config/dayjs';

const noop = () => {};

// TestBed.initTestEnvironment(BrowserDynamicTestingModule, platformBrowserDynamicTesting());

// declare let require: any;
// const testsContext: any = require.context('./', true, /\.spec/);
// testsContext.keys().forEach(testsContext);

// jest global mocks

const mock = () => {
    let storage = {};
    return {
        getItem: (key: any) => (key in storage ? storage[key] : null),
        setItem: (key: any, value: any) => (storage[key] = value || ''),
        removeItem: (key: any) => delete storage[key],
        clear: () => (storage = {}),
    };
};

Object.defineProperty(window, 'localStorage', { value: mock() });
Object.defineProperty(window, 'sessionStorage', { value: mock() });
Object.defineProperty(window, 'getComputedStyle', {
    value: () => ['-webkit-appearance'],
});

Object.defineProperty(window, 'scrollTo', { value: noop, writable: true });
Object.defineProperty(window, 'scroll', { value: noop, writable: true });
Object.defineProperty(window, 'alert', { value: noop, writable: true });

Object.defineProperty(window, 'getComputedStyle', {
    value: () => ({
        getPropertyValue: () => {
            return '';
        },
    }),
});

Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: jest.fn().mockImplementation((query) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: jest.fn(), // deprecated
        removeListener: jest.fn(), // deprecated
        addEventListener: jest.fn(),
        removeEventListener: jest.fn(),
        dispatchEvent: jest.fn(),
    })),
});
