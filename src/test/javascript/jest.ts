import 'jest-preset-angular/setup-jest';
import './jest-global-mocks';
import 'jest-canvas-mock';

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

// Same as in global.utils.ts
declare global {
    interface Array<T> {
        last(): T | undefined;
    }
}

if (!Array.prototype.last) {
    Array.prototype.last = function () {
        if (!this.length) {
            return undefined;
        }
        return this[this.length - 1];
    };
}
