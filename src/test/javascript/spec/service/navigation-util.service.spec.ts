import { TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../test.module';
import { NavigationEnd, Router, UrlTree } from '@angular/router';
import { Location } from '@angular/common';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { of } from 'rxjs';
import { MockRouter } from '../helpers/mocks/mock-router';

describe('Navigation Util Service', () => {
    let service: ArtemisNavigationUtilService;

    const router = new MockRouter();
    router.events = of(new NavigationEnd(1, 'a', 'b'), new NavigationEnd(1, 'a', 'b'));

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            providers: [
                { provide: Router, useValue: router },
                { provide: Location, useClass: Location },
            ],
        })
            .compileComponents()
            .then(() => {
                service = TestBed.inject(ArtemisNavigationUtilService);
                // @ts-ignore
                service.onFirstPage = true;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
        router.navigate.mockRestore();
    });

    it('should go back', () => {
        const backSpy = jest.spyOn(TestBed.inject(Location), 'back');
        // @ts-ignore
        service.onFirstPage = false;

        service.navigateBack([]);

        expect(backSpy).toHaveBeenCalledOnce();
        expect(backSpy).toHaveBeenCalledWith();
    });

    it('should use fallback', () => {
        service.navigateBack(['a', 'b', 'c']);

        expect(router.navigate).toHaveBeenCalledOnce();
        expect(router.navigate).toHaveBeenCalledWith(['a', 'b', 'c']);
    });

    it('should use without optional', () => {
        service.navigateBackWithOptional(['a', 'b', 'c'], undefined);

        expect(router.navigate).toHaveBeenCalledOnce();
        expect(router.navigate).toHaveBeenCalledWith(['a', 'b', 'c']);
    });

    it('should use with optional', () => {
        service.navigateBackWithOptional(['a', 'b', 'c'], 'd');

        expect(router.navigate).toHaveBeenCalledOnce();
        expect(router.navigate).toHaveBeenCalledWith(['a', 'b', 'c', 'd']);
    });

    it('should route correctly', () => {
        const route = ['course-management', 17];
        const queryParam = { filterOption: 30 };
        const urlTreeMock = { path: 'testValue' } as unknown as UrlTree;
        const creationMock = jest.spyOn(router, 'createUrlTree');
        const serializationMock = jest.spyOn(router, 'serializeUrl');
        const windowStub = jest.spyOn(window, 'open').mockImplementation();

        service.routeInNewTab(['course-management', 17], queryParam);

        expect(creationMock).toHaveBeenCalledWith(route, queryParam);
        expect(creationMock).toHaveBeenCalledOnce();
        expect(serializationMock).toHaveBeenCalledWith(urlTreeMock);
        expect(windowStub).toHaveBeenCalledWith('testValue', '_blank');
    });
});
