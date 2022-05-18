export {};

declare global {
    export interface Map<K, V> {
        computeIfAbsent(key: K, mappingFunction: (key: K) => V): V;
    }
}

if (!Map.prototype.computeIfAbsent) {
    Map.prototype.computeIfAbsent = function (key, mappingFunction) {
        const value = this.get(key);
        if (value !== undefined) {
            return value;
        }
        const valueToAdd = mappingFunction(key);
        this.set(key, valueToAdd);
        return valueToAdd;
    };
}
