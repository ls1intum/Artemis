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
    Object.defineProperty(Array.prototype, 'last', {
        value: function last<T>(): T | undefined {
            if (!this.length) {
                return undefined;
            }
            return this[this.length - 1];
        },
        enumerable: false,
        writable: true,
        configurable: true,
    });
}

if (!Array.prototype.first) {
    Object.defineProperty(Array.prototype, 'first', {
        value: function first<T>(): T | undefined {
            if (!this.length) {
                return undefined;
            }
            return this[0];
        },
        enumerable: false,
        writable: true,
        configurable: true,
    });
}

if (!Array.prototype.shuffle) {
    Object.defineProperty(Array.prototype, 'shuffle', {
        value: function shuffle(): void {
            for (let i = this.length - 1; i > 0; i--) {
                const j = Math.floor(Math.random() * (i + 1));
                [this[i], this[j]] = [this[j], this[i]]; // This is a more modern approach to swap elements.
            }
        },
        enumerable: false,
        writable: true,
        configurable: true,
    });
}
