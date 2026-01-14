import { TestBed } from '@angular/core/testing';
import { LocalStorageService } from './local-storage.service';

enum TestStringEnum {
    Category1 = 'category1',
    Category2 = 'category2',
    Category3 = 'category3',
}

describe('LocalStorageService', () => {
    let service: LocalStorageService;
    const testKey = 'testKey';

    beforeEach(() => {
        TestBed.configureTestingModule({});
        service = TestBed.inject(LocalStorageService);
        service.clear();
    });

    it('should store and retrieve a string correctly', () => {
        const value = 'Hello, world!';
        service.store<string>(testKey, value);
        const retrieved = service.retrieve<string>(testKey);
        expect(retrieved).toBe(value);
    });

    it('should store and retrieve a number correctly', () => {
        const value = 42;
        service.store<number>(testKey, value);
        const retrieved = service.retrieve<number>(testKey);
        expect(retrieved).toBe(value);
    });

    it('should store and retrieve a boolean correctly', () => {
        const value = true;
        service.store<boolean>(testKey, value);
        const retrieved = service.retrieve<boolean>(testKey);
        expect(retrieved).toBe(value);
    });

    it('should store and retrieve a Date object correctly', () => {
        const value = new Date();
        service.store<Date>(testKey, value);
        const retrieved = service.retrieveDate(testKey);
        expect(retrieved).toBeDefined();
        expect(retrieved?.toISOString()).toBe(value.toISOString());
    });

    it('should store and retrieve a string-based enum value correctly', () => {
        const value = TestStringEnum.Category1;
        service.store<TestStringEnum>(testKey, value);
        const retrieved = service.retrieve<TestStringEnum>(testKey);
        expect(retrieved).toBe(TestStringEnum.Category1);
    });

    it('should clear all items from local storage', () => {
        service.store<string>('key1', 'value1');
        service.store<string>('key2', 'value2');
        service.store<string>('key3', 'value3');

        service.clear();

        expect(service.retrieve<string>('key1')).toBeUndefined();
        expect(service.retrieve<string>('key2')).toBeUndefined();
        expect(service.retrieve<string>('key3')).toBeUndefined();
    });

    it('should clear all items except specified keys', () => {
        service.store<string>('key1', 'value1');
        service.store<string>('key2', 'value2');
        service.store<string>('key3', 'value3');
        service.store<string>('preserve1', 'preserve-value1');
        service.store<string>('preserve2', 'preserve-value2');

        service.clearExcept(['preserve1', 'preserve2']);

        expect(service.retrieve<string>('key1')).toBeUndefined();
        expect(service.retrieve<string>('key2')).toBeUndefined();
        expect(service.retrieve<string>('key3')).toBeUndefined();
        expect(service.retrieve<string>('preserve1')).toBe('preserve-value1');
        expect(service.retrieve<string>('preserve2')).toBe('preserve-value2');
    });

    it('should handle clearExcept with non-existent keys', () => {
        service.store<string>('key1', 'value1');
        service.store<string>('key2', 'value2');

        service.clearExcept(['nonExistentKey', 'anotherNonExistentKey']);

        expect(service.retrieve<string>('key1')).toBeUndefined();
        expect(service.retrieve<string>('key2')).toBeUndefined();
        expect(service.retrieve<string>('nonExistentKey')).toBeUndefined();
    });

    it('should preserve Date objects when using clearExcept', () => {
        const testDate = new Date('2025-12-08T10:00:00.000Z');
        service.store<string>('key1', 'value1');
        service.store<Date>('dateKey', testDate);

        service.clearExcept(['dateKey']);

        expect(service.retrieve<string>('key1')).toBeUndefined();
        expect(service.retrieveDate('dateKey')).toBeDefined();
        expect(service.retrieveDate('dateKey')?.toISOString()).toBe(testDate.toISOString());
    });
});
