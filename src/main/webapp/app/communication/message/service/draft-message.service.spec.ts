import { TestBed } from '@angular/core/testing';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { DraftService } from './draft-message.service';

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
        const now = Date.now();
        const recentTimestamp = now - 6 * 24 * 60 * 60 * 1000; // 6 days ago
        const draftData = JSON.stringify({ content: 'recent draft', timestamp: recentTimestamp });

        jest.spyOn(localStorageService, 'retrieve').mockReturnValue(draftData);
        const result = draftService.loadDraft('key');
        expect(result).toBe('recent draft');
    });

    it('should not load draft if it is older than 7 days', () => {
        const now = Date.now();
        const expiredTimestamp = now - 8 * 24 * 60 * 60 * 1000; // 8 days ago
        const draftData = JSON.stringify({ content: 'expired draft', timestamp: expiredTimestamp });

        const clearSpy = jest.spyOn(localStorageService, 'remove');
        jest.spyOn(localStorageService, 'retrieve').mockReturnValue(draftData);

        const result = draftService.loadDraft('key');
        expect(result).toBeUndefined();
        expect(clearSpy).toHaveBeenCalledWith('key');
    });

    it('should treat draft saved just after 7 days ago as expired', () => {
        const now = Date.now();
        const justOver7DaysAgo = now - SEVEN_DAYS_MS - 1;
        const draftData = JSON.stringify({ content: 'expired draft', timestamp: justOver7DaysAgo });

        const clearSpy = jest.spyOn(localStorageService, 'remove');
        jest.spyOn(localStorageService, 'retrieve').mockReturnValue(draftData);

        const result = draftService.loadDraft('key');
        expect(result).toBeUndefined();
        expect(clearSpy).toHaveBeenCalledWith('key');
    });

    it('should fallback to plain string if JSON parse fails but content is valid', () => {
        jest.spyOn(localStorageService, 'retrieve').mockReturnValue('legacy string draft');
        const result = draftService.loadDraft('key');
        expect(result).toBe('legacy string draft');
    });

    it('should return undefined if retrieved raw value is empty string', () => {
        jest.spyOn(localStorageService, 'retrieve').mockReturnValue('');
        const clearSpy = jest.spyOn(localStorageService, 'clear');
        const result = draftService.loadDraft('key');
        expect(result).toBeUndefined();
        expect(clearSpy).not.toHaveBeenCalled();
    });

    it('should fallback to legacy string if JSON parse fails', () => {
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
