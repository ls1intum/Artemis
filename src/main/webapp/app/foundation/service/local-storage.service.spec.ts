import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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
        localStorage.clear();
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

    it('preserves each user’s build notification preference through logout while clearing session data', () => {
        service.store('buildNotifications.7', true);
        service.store('buildNotifications.8', false);
        service.store('authenticationToken', 'expired-token');

        service.clear();

        expect(service.retrieve<boolean>('buildNotifications.7')).toBe(true);
        expect(service.retrieve<boolean>('buildNotifications.8')).toBe(false);
        expect(service.retrieve<string>('authenticationToken')).toBeUndefined();
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

    describe('stored values that are not in the current format', () => {
        afterEach(() => {
            vi.restoreAllMocks();
        });

        it('reads the bare ISO date of an older client and rewrites it in the current format', () => {
            const iso = '2026-12-08T10:00:00.000Z';
            localStorage.setItem(testKey, iso);

            expect(service.retrieveDate(testKey)?.toISOString()).toBe(iso);
            expect(localStorage.getItem(testKey)).toBe(JSON.stringify(iso));
            expect(service.retrieveDate(testKey)?.toISOString()).toBe(iso);
        });

        it('removes only the key of a date that is garbage', () => {
            localStorage.setItem(testKey, 'not a date {');
            localStorage.setItem('otherKey', 'keep me');

            expect(service.retrieveDate(testKey)).toBeUndefined();
            expect(localStorage.getItem(testKey)).toBeNull();
            expect(localStorage.getItem('otherKey')).toBe('keep me');
        });

        it.each([['42'], ['{"a":1}'], ['"tomorrow"'], ['"2026-12-08"'], ['null']])('removes the key of a date that is stored as %s', (stored) => {
            localStorage.setItem(testKey, stored);

            expect(service.retrieveDate(testKey)).toBeUndefined();
            expect(localStorage.getItem(testKey)).toBeNull();
        });

        it('returns undefined for a missing date', () => {
            expect(service.retrieveDate(testKey)).toBeUndefined();
        });

        it('treats a value that is not JSON as absent and removes it', () => {
            localStorage.setItem(testKey, '123e4567-e89b-12d3-a456-426614174000');

            expect(service.retrieve<string>(testKey)).toBeUndefined();
            expect(localStorage.getItem(testKey)).toBeNull();
        });

        it('treats an inaccessible storage as empty', () => {
            vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
                throw new TypeError("null is not an object (evaluating 'localStorage.getItem')");
            });

            expect(service.retrieve<string>(testKey)).toBeUndefined();
            expect(service.retrieveDate(testKey)).toBeUndefined();
        });
    });
});
