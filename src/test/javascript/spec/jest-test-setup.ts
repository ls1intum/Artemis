import 'jest-canvas-mock';
import 'app/shared/util/array.extension';
import 'app/shared/util/map.extension';
import 'app/shared/util/string.extension';
import 'app/core/config/dayjs';
import 'jest-extended';

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
