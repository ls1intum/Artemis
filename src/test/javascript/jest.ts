import 'jest-preset-angular/setup-jest';
import './jest-global-mocks';
import 'jest-canvas-mock';
import 'app/shared/util/array.extension';

const noop = () => {};

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
