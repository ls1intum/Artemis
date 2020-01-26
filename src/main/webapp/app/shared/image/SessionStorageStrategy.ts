import { IStorageStrategy } from 'ngx-cacheable';
import { GlobalCacheConfig, ICachePair } from 'ngx-cacheable';

/**
 * This is the same as the DOMStorageStrategy, only using the sessionStorage instead of the localStorage: https://github.com/angelnikolov/ngx-cacheable/blob/master/common/DOMStorageStrategy.ts
 */
export class SessionStorageStrategy extends IStorageStrategy {
    private masterCacheKey: string = GlobalCacheConfig.globalCacheKey;
    constructor() {
        super();
        if (typeof sessionStorage === 'undefined') {
            throw new Error('Platform not supported.');
        }
    }

    add(cachePair: ICachePair<any>, cacheKey: string) {
        const allCachedData = this.getRawData();
        if (!allCachedData[cacheKey]) {
            allCachedData[cacheKey] = [];
        }
        allCachedData[cacheKey].push(cachePair);
        this.storeRawData(allCachedData);
    }

    getAll(cacheKey: string) {
        return this.getRawData()[cacheKey] || [];
    }

    removeAtIndex(index: number, cacheKey: string) {
        const allCachedData = this.getRawData();
        if (allCachedData[cacheKey] && allCachedData[cacheKey].length) {
            allCachedData[cacheKey].splice(index, 1);
        }
        this.storeRawData(allCachedData);
    }

    updateAtIndex(index: number, entity: any, cacheKey: string) {
        const allCachedData = this.getRawData();
        if (allCachedData[cacheKey] && allCachedData[cacheKey][index]) {
            allCachedData[cacheKey][index] = entity;
        }
        this.storeRawData(allCachedData);
    }

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
