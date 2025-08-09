import { TestBed } from '@angular/core/testing';
import { SessionStorageService } from './session-storage.service';

enum TestStringEnum {
    Category1 = 'category1',
    Category2 = 'category2',
    Category3 = 'category3',
}

describe('SessionStorageService', () => {
    let service: SessionStorageService;
    const testKey = 'testKey';

    beforeEach(() => {
        TestBed.configureTestingModule({});
        service = TestBed.inject(SessionStorageService);
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
});
