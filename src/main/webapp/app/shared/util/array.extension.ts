export {};

declare global {
    interface Array<T> {
        last(): T | undefined;
        first(): T | undefined;

        /**
         * Shuffles array in place.
         */
        shuffle(): void;
    }
}

if (!Array.prototype.last) {
    Array.prototype.last = function last<T>(): T | undefined {
        if (!this.length) {
            return undefined;
        }
        return this[this.length - 1];
    };
}

if (!Array.prototype.first) {
    Array.prototype.first = function first<T>(): T | undefined {
        if (!this.length) {
            return undefined;
        }
        return this[0];
    };
}

if (!Array.prototype.shuffle) {
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    Array.prototype.shuffle = function shuffle<T>(): void {
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
