import { GlobalCacheConfig, ICachePair, IStorageStrategy } from 'ngx-cacheable';

/**
 * This is the same as the DOMStorageStrategy, only using the sessionStorage
 * instead of the localStorage: https://github.com/angelnikolov/ngx-cacheable/blob/master/common/DOMStorageStrategy.ts
 */
export class SessionStorageStrategy extends IStorageStrategy {
    private masterCacheKey: string = GlobalCacheConfig.globalCacheKey;
    constructor() {
        super();
        if (typeof sessionStorage === 'undefined') {
            throw new Error('Platform not supported.');
        }
    }

    /** Pushes the {@param cachePair} into the cache with key {@param cacheKey}.
     * @method
     * @param cachePair { ICachePair } Interface for value which is inserted into the cache.
     * @param cacheKey { string } Key name.
     */
    add(cachePair: ICachePair<any>, cacheKey: string) {
        const allCachedData = this.getRawData();
        if (!allCachedData[cacheKey]) {
            allCachedData[cacheKey] = [];
        }
        allCachedData[cacheKey].push(cachePair);
        this.storeRawData(allCachedData);
    }

    /** Wrapper function for {@link getRawData}. Returns all values for a specific key.
     * @method
     * @param cacheKey { string } Key for which all values are retrieved.
     */
    getAll(cacheKey: string) {
        return this.getRawData()[cacheKey] || [];
    }

    /** Removes a value from the cache at a specific index.
     * @method
     * @param index {number} The index of the value which should be removed for a specific key.
     * @param cacheKey {string} The cache key.
     */
    removeAtIndex(index: number, cacheKey: string) {
        const allCachedData = this.getRawData();
        if (allCachedData[cacheKey] && allCachedData[cacheKey].length) {
            allCachedData[cacheKey].splice(index, 1);
        }
        this.storeRawData(allCachedData);
    }

    /** Replaces the specific value at index with key cacheKey with entity.
     * @method
     * @param index {number}
     * @param entity
     * @param cacheKey {string}
     */
    updateAtIndex(index: number, entity: any, cacheKey: string) {
        const allCachedData = this.getRawData();
        if (allCachedData[cacheKey] && allCachedData[cacheKey][index]) {
            allCachedData[cacheKey][index] = entity;
        }
        this.storeRawData(allCachedData);
    }

    /** Removes all values stored for the specific key.
     * @method
     * @param cacheKey { string } The cache key.
     */
    removeAll(cacheKey: string) {
        const allCachedData = this.getRawData();
        if (allCachedData[cacheKey] && allCachedData[cacheKey].length) {
            allCachedData[cacheKey].length = 0;
        }
        this.storeRawData(allCachedData);
    }

    private getRawData(): { [key: string]: Array<ICachePair<any>> } {
        const data = sessionStorage.getItem(this.masterCacheKey);
        try {
            return data ? JSON.parse(data) : {};
        } catch (error) {
            throw new Error(error);
        }
    }

    private storeRawData(data: { [key: string]: Array<ICachePair<any>> }): void {
        sessionStorage.setItem(this.masterCacheKey, JSON.stringify(data));
    }
}
