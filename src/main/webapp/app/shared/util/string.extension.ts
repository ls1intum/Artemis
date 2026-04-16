// String.prototype.replaceAll is an ES2021 method available in every browser we support
// (Chrome 85+, Firefox 77+, Safari 13.1+). This file exists only to augment the TS lib
// typings for older tsconfig targets; no runtime polyfill is shipped.
export {};

declare global {
    export interface String {
        replaceAll(search: string | RegExp, replacement: string): string;
    }
}
