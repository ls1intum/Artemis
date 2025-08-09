import { TestBed } from '@angular/core/testing';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { DraftData, DraftService } from './draft-message.service';

const SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000;

describe('DraftService', () => {
    let draftService: DraftService;
    let localStorageService: LocalStorageService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [DraftService, LocalStorageService],
        });
        draftService = TestBed.inject(DraftService);
        localStorageService = TestBed.inject(LocalStorageService);
    });

    it('should save draft if key and content are valid', () => {
        const storeSpy = jest.spyOn(localStorageService, 'store');
        draftService.saveDraft('key', 'content');
        expect(storeSpy).toHaveBeenCalled();
    });

    it('should not save draft if content is empty', () => {
        const storeSpy = jest.spyOn(localStorageService, 'store');
        draftService.saveDraft('key', '');
        expect(storeSpy).not.toHaveBeenCalled();
    });

    it('should clear draft if content is empty', () => {
        const clearSpy = jest.spyOn(localStorageService, 'remove');
        draftService.clearDraft('key');
        expect(clearSpy).toHaveBeenCalledWith('key');
    });

    it('should load draft if it was saved within the last 7 days', () => {
        draftService.saveDraft('key', 'recent draft');
        const result = draftService.loadDraft('key');
        expect(result).toBe('recent draft');
    });

    it('should not load draft if it is older than 7 days', () => {
        const now = Date.now();
        const expiredTimestamp = now - 8 * 24 * 60 * 60 * 1000; // 8 days ago
        const draftData: DraftData = {
            content: 'expired draft',
            timestamp: expiredTimestamp,
        };
        localStorageService.store<DraftData>('key', draftData);

        const clearSpy = jest.spyOn(localStorageService, 'remove');
        const result = draftService.loadDraft('key');
        expect(result).toBeUndefined();
        expect(clearSpy).toHaveBeenCalledWith('key');
    });

    it('should treat draft saved just after 7 days ago as expired', () => {
        const now = Date.now();
        const justOver7DaysAgo = now - SEVEN_DAYS_MS - 1;
        const draftData: DraftData = {
            content: 'expired draft',
            timestamp: justOver7DaysAgo,
        };
        localStorageService.store<DraftData>('key', draftData);

        const clearSpy = jest.spyOn(localStorageService, 'remove');
        const result = draftService.loadDraft('key');
        expect(result).toBeUndefined();
        expect(clearSpy).toHaveBeenCalledWith('key');
    });

    it('should fallback to plain string if JSON parse fails but content is valid', () => {
        localStorageService.store<string>('key', 'some draft');
        const result = draftService.loadDraft('key');
        expect(result).toBe('some draft');
    });

    it('should return undefined if retrieved value is empty string', () => {
        jest.spyOn(localStorageService, 'retrieve').mockReturnValue('');
        const clearSpy = jest.spyOn(localStorageService, 'clear');
        const result = draftService.loadDraft('key');
        expect(result).toBeUndefined();
        expect(clearSpy).not.toHaveBeenCalled();
    });

    it('should fallback to string if JSON parse fails', () => {
        jest.spyOn(localStorageService, 'retrieve').mockReturnValue('{ invalid json');
        const result = draftService.loadDraft('key');
        expect(result).toBe('{ invalid json');
    });

    it('should return undefined if key is empty', () => {
        const result = draftService.loadDraft('');
        expect(result).toBeUndefined();
    });

    it('should not crash if raw value is not a string', () => {
        jest.spyOn(localStorageService, 'retrieve').mockReturnValue(12345);
        const result = draftService.loadDraft('key');
        expect(result).toBeUndefined();
    });
});
