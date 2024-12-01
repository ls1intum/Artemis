import { TestBed } from '@angular/core/testing';
import { StateStorageService } from 'app/core/auth/state-storage.service';
import { SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';

describe('StateStorageService', () => {
    let service: StateStorageService;
    let sessionStorageService: SessionStorageService;

    let retrieveSpy: jest.SpyInstance;
    let storeSpy: jest.SpyInstance;

    const previousUrlKey = 'previousUrl';

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: SessionStorageService, useClass: MockSyncStorage }],
        })
            .compileComponents()
            .then(() => {
                service = TestBed.inject(StateStorageService);
                sessionStorageService = TestBed.inject(SessionStorageService);

                retrieveSpy = jest.spyOn(sessionStorageService, 'retrieve');
                storeSpy = jest.spyOn(sessionStorageService, 'store');
            });
    });

    afterEach(() => jest.restoreAllMocks());

    it('should store a new previous URL', () => {
        const newUrl = 'www.examp.le';

        service.storeUrl(newUrl);

        expect(storeSpy).toHaveBeenCalledOnce();
        expect(storeSpy).toHaveBeenCalledWith(previousUrlKey, newUrl);
    });

    it('should retrieve the previous URL', () => {
        service.getUrl();

        expect(retrieveSpy).toHaveBeenCalledOnce();
        expect(retrieveSpy).toHaveBeenCalledWith(previousUrlKey);
    });
});
