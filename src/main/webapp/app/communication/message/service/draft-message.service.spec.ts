import { TestBed } from '@angular/core/testing';
import { DraftService } from './draft-message.service';
import { LocalStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from 'test/helpers/mocks/service/mock-sync-storage.service';

describe('DraftService', () => {
    let draftService: DraftService;
    let localStorageService: LocalStorageService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [DraftService, { provide: LocalStorageService, useClass: MockSyncStorage }],
        });
        draftService = TestBed.inject(DraftService);
        localStorageService = TestBed.inject(LocalStorageService);
    });

    it('should save draft if key and content are valid', () => {
        const storeSpy = jest.spyOn(localStorageService, 'store');
        draftService.saveDraft('key', 'content');
        expect(storeSpy).toHaveBeenCalledWith('key', 'content');
    });

    it('should not save draft if content is empty', () => {
        const storeSpy = jest.spyOn(localStorageService, 'store');
        draftService.saveDraft('key', '');
        expect(storeSpy).not.toHaveBeenCalled();
    });

    it('should clear draft if content is empty', () => {
        const clearSpy = jest.spyOn(localStorageService, 'clear');
        draftService.clearDraft('key');
        expect(clearSpy).toHaveBeenCalledWith('key');
    });

    it('should load draft if key is valid and draft exists', () => {
        jest.spyOn(localStorageService, 'retrieve').mockReturnValue('draft content');
        const result = draftService.loadDraft('key');
        expect(localStorageService.retrieve).toHaveBeenCalledWith('key');
        expect(result).toBe('draft content');
    });

    it('should clear draft if loaded draft is empty', () => {
        jest.spyOn(localStorageService, 'retrieve').mockReturnValue('');
        const clearSpy = jest.spyOn(localStorageService, 'clear');
        const result = draftService.loadDraft('key');
        expect(clearSpy).toHaveBeenCalledWith('key');
        expect(result).toBeUndefined();
    });

    it('should return undefined if key is empty', () => {
        const result = draftService.loadDraft('');
        expect(result).toBeUndefined();
    });
});
