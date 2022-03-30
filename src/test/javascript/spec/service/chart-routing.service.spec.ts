import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { MockProvider } from 'ng-mocks';
import { ChartRoutingService } from 'app/shared/chart/chart-routing.service';

describe('ChartRoutingService', () => {
    let service: ChartRoutingService;
    let router: Router;
    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [MockProvider(Router)],
        });

        service = TestBed.inject(ChartRoutingService);
        router = TestBed.inject(Router);
    });

    it('should route correctly', () => {
        const route = ['course-management', 17];
        const urlTreeMock = { path: 'testValue' } as unknown as UrlTree;
        const creationMock = jest.spyOn(router, 'createUrlTree').mockReturnValue(urlTreeMock);
        const serializationMock = jest.spyOn(router, 'serializeUrl').mockImplementation(() => 'testValue');
        const windowStub = jest.spyOn(window, 'open').mockImplementation();

        service.routeInNewTab(['course-management', 17]);

        expect(creationMock).toHaveBeenCalledWith(route);
        expect(creationMock).toHaveBeenCalledTimes(1);
        expect(serializationMock).toHaveBeenCalledWith(urlTreeMock);
        expect(windowStub).toHaveBeenCalledWith('testValue', '_blank');
    });
});
