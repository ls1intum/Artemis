import { TestBed } from '@angular/core/testing';

import { CachedRepositoryFilesService } from 'app/exercises/programming/manage/services/cached-repository-files.service';

describe('CachedRepositoryFilesServiceService', () => {
    let service: CachedRepositoryFilesService;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        service = TestBed.inject(CachedRepositoryFilesService);
    });

    it('should emit event if cachedRepositoryFilesChanged is invoked', () => {
        const emitSpy = jest.spyOn(service.cachedRepositoryFilesChanged, 'emit');
        const data = new Map<string, Map<string, string>>();
        data.set('test', new Map<string, string>());
        service.emitCachedRepositoryFiles(data);
        expect(emitSpy).toHaveBeenCalledExactlyOnceWith(data);
    });

    it('should return observable if getCachedRepositoryFilesObservable is invoked', () => {
        const data = new Map<string, Map<string, string>>();
        data.set('test', new Map<string, string>());
        service.emitCachedRepositoryFiles(data);
        const observable = service.getCachedRepositoryFilesObservable();
        expect(observable).toBeDefined();
        observable.subscribe((observableContent) => {
            expect(observableContent).toEqual(data);
        });
    });
});
