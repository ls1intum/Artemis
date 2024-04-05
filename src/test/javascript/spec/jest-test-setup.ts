import 'app/shared/util/map.extension';
import 'app/shared/util/string.extension';
import 'app/shared/util/array.extension';
import 'app/core/config/dayjs';
import 'jest-canvas-mock';
import 'jest-extended';
import failOnConsole from 'jest-fail-on-console';
import { TextDecoder, TextEncoder } from 'util';

/*
 * In the Jest configuration, we only import the basic features of monaco (editor.api.js) instead
 * of the full module (editor.main.js) because of a ReferenceError in the language features of Monaco.
 * The following import imports the core features of the monaco editor, but leaves out the language
 * features. It contains an unchecked call to queryCommandSupported, so the function has to be set
 * on the document.
 */
document.queryCommandSupported = () => false;
import 'monaco-editor/esm/vs/editor/edcore.main';

failOnConsole({
    shouldFailOnWarn: true,
    shouldFailOnLog: true,
    shouldFailOnInfo: true,
});

const noop = () => {};

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

Object.defineProperty(window, 'location', {
    value: {
        hash: '',
        href: 'https://artemis.fake/test',
    },
});

Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: jest.fn().mockImplementation((query) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: jest.fn(),
        removeListener: jest.fn(),
        addEventListener: jest.fn(),
        removeEventListener: jest.fn(),
        dispatchEvent: jest.fn(),
    })),
});

// Prevents an error with the monaco editor tests
Object.assign(global, { TextDecoder, TextEncoder });
