export {};

declare global {
    export interface Array<T> {
        last(): T | undefined;
        first(): T | undefined;
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

if (!Array.prototype.first) {
    Array.prototype.first = function () {
        if (!this.length) {
            return undefined;
        }
        return this[0];
    };
}
