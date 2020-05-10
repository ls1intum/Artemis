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

    /**
     * @function add
     *
     * @param cachePair { ICachePair } Interface for value which is inserted into cache
     * @param cacheKey { string } Key name
     */
    add(cachePair: ICachePair<any>, cacheKey: string) {
        const allCachedData = this.getRawData();
        if (!allCachedData[cacheKey]) {
            allCachedData[cacheKey] = [];
        }
        allCachedData[cacheKey].push(cachePair);
        this.storeRawData(allCachedData);
    }

    /**
     * @function getAll
     * Returns all values for a specific key.
     * @param cacheKey { string } Key for which all values are retrieved
     */
    getAll(cacheKey: string) {
        return this.getRawData()[cacheKey] || [];
    }

    /**
     * @function removeAtIndex
     * Removes a value from the cache at a specific index.
     * @param index { number } The index of the value which should be removed for a specific key
     * @param cacheKey { string } The cache key
     */
    removeAtIndex(index: number, cacheKey: string) {
        const allCachedData = this.getRawData();
        if (allCachedData[cacheKey] && allCachedData[cacheKey].length) {
            allCachedData[cacheKey].splice(index, 1);
        }
        this.storeRawData(allCachedData);
    }

    /**
     * @function updateAtIndex
     * Replaces the specific value at index with key cacheKey with entity.
     * @param index { number }
     * @param entity
     * @param cacheKey { string }
     */
    updateAtIndex(index: number, entity: any, cacheKey: string) {
        const allCachedData = this.getRawData();
        if (allCachedData[cacheKey] && allCachedData[cacheKey][index]) {
            allCachedData[cacheKey][index] = entity;
        }
        this.storeRawData(allCachedData);
    }

    /**
     * @function removeAll
     * Removes all values stored for the specific key.
     * @param cacheKey { string } The cache key
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
