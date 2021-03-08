import { GlobalCacheConfig, ICachePair, IStorageStrategy } from 'ts-cacheable';

/**
 * This is the same as the DOMStorageStrategy, only using the sessionStorage
 * instead of the localStorage: https://github.com/angelnikolov/ts-cacheable/blob/master/common/LocalStorageStrategy.ts
 */
export class SessionStorageStrategy extends IStorageStrategy {
    private masterCacheKey: string = GlobalCacheConfig.globalCacheKey;
    constructor() {
        super();
        if (sessionStorage == undefined) {
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

    addMany(entities: ICachePair<any>[], cacheKey: string) {
        const allCachedData = this.getRawData();
        if (!allCachedData[cacheKey]) {
            allCachedData[cacheKey] = [];
        }
        allCachedData[cacheKey] = entities;
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

    remove(index: number, entity: ICachePair<any>, cacheKey: string) {
        this.removeAtIndex(index, cacheKey);
    }

    updateAtIndex(index: number, entity: ICachePair<any>, cacheKey: string) {
        const allCachedData = this.getRawData();
        if (allCachedData[cacheKey] && allCachedData[cacheKey][index]) {
            allCachedData[cacheKey][index] = entity;
        }
        this.storeRawData(allCachedData);
    }

    update(index: number, entity: ICachePair<any>, cacheKey: string) {
        this.updateAtIndex(index, entity, cacheKey);
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
