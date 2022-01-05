export {};

declare global {
    export interface Array<T> {
        last(): T | undefined;
        first(): T | undefined;

        /**
         * Shuffles array in place.
         */
        shuffle(): void;
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

if (!Array.prototype.shuffle) {
    Array.prototype.shuffle = function () {
        if (this.length > 1) {
            for (let i = this.length - 1; i > 0; i--) {
                const randomIndex = Math.floor(Math.random() * (i + 1));
                const randomElement = this[randomIndex];
                this[randomIndex] = this[i];
                this[i] = randomElement;
            }
        }
    };
}
