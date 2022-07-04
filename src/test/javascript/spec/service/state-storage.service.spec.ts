import { TestBed } from '@angular/core/testing';
import { StateStorageService } from 'app/core/auth/state-storage.service';
import { SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';

describe('StateStorageService', () => {
    let service: StateStorageService;
    let sessionStorageService: SessionStorageService;

    let retrieveSpy: jest.SpyInstance;
    let clearSpy: jest.SpyInstance;
    let storeSpy: jest.SpyInstance;

    const previousStateKey = 'previousState';
    const destinationStateKey = 'destinationState';
    const previousUrlKey = 'previousUrl';

    const params = {
        id: 1,
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: SessionStorageService, useClass: MockSyncStorage }],
        })
            .compileComponents()
            .then(() => {
                service = TestBed.inject(StateStorageService);
                sessionStorageService = TestBed.inject(SessionStorageService);

                retrieveSpy = jest.spyOn(sessionStorageService, 'retrieve');
                clearSpy = jest.spyOn(sessionStorageService, 'clear');
                storeSpy = jest.spyOn(sessionStorageService, 'store');
            });
    });

    afterEach(() => jest.restoreAllMocks());

    it('should retrieve previous state', () => {
        service.getPreviousState();

        expect(retrieveSpy).toHaveBeenCalledOnce();
        expect(retrieveSpy).toHaveBeenCalledWith(previousStateKey);
    });

    it('should reset previous state', () => {
        service.resetPreviousState();

        expect(clearSpy).toHaveBeenCalledOnce();
        expect(clearSpy).toHaveBeenCalledWith(previousStateKey);
    });

    it('should store a new previous state', () => {
        const name = 'new state';
        const expectedState = { name, params };

        service.storePreviousState(name, params);

        expect(storeSpy).toHaveBeenCalledOnce();
        expect(storeSpy).toHaveBeenCalledWith(previousStateKey, expectedState);
    });

    it('should retrieve destination state', () => {
        service.getDestinationState();

        expect(retrieveSpy).toHaveBeenCalledOnce();
        expect(retrieveSpy).toHaveBeenCalledWith(destinationStateKey);
    });

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

    it('should store a new destination state', () => {
        const destinationState = {
            name: 'destination state',
            data: { id: 42, body: undefined },
        };
        const fromState = { name: 'from state' };
        const expectedDestinationInfo = {
            destination: {
                name: 'destination state',
                data: { id: 42, body: undefined },
            },
            params,
            from: {
                name: 'from state',
            },
        };

        service.storeDestinationState(destinationState, params, fromState);

        expect(storeSpy).toHaveBeenCalledOnce();
        expect(storeSpy).toHaveBeenCalledWith(destinationStateKey, expectedDestinationInfo);
    });
});
