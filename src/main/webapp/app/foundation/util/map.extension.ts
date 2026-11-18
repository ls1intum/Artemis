export {};

declare global {
    export interface Map<K, V> {
        computeIfAbsent(key: K, mappingFunction: (key: K) => V): V;
    }
}

if (!Map.prototype.computeIfAbsent) {
    Object.defineProperty(Map.prototype, 'computeIfAbsent', {
        value: function <K, V>(this: Map<K, V>, key: K, mappingFunction: (key: K) => V): V {
            let value = this.get(key);
            if (value !== undefined) {
                return value;
            } else {
                value = mappingFunction(key);
                this.set(key, value);
                return value;
            }
        },
        enumerable: false,
        writable: true,
        configurable: true,
    });
}
