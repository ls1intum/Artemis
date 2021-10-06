import { TestBed } from '@angular/core/testing';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { spy } from 'sinon';
import { ArtemisTestModule } from '../test.module';
import { NavigationEnd, Router } from '@angular/router';
import { MockRouter } from '../helpers/mocks/service/mock-route.service';
import { Location } from '@angular/common';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { of } from 'rxjs';

chai.use(sinonChai);
const expect = chai.expect;

describe('Navigation Util Service', () => {
    let service: ArtemisNavigationUtilService;

    const router = new MockRouter();
    const events = of(new NavigationEnd(1, 'a', 'b'), new NavigationEnd(1, 'a', 'b'));
    router.setEvents(events);

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
        router.navigateSpy.reset();
    });

    it('should go back', () => {
        const backSpy = spy(TestBed.inject(Location), 'back');
        // @ts-ignore
        service.onFirstPage = false;

        service.navigateBack([]);

        expect(backSpy).to.have.been.calledOnceWithExactly();
    });
    it('should use fallback', () => {
        service.navigateBack(['a', 'b', 'c']);

        expect(router.navigateSpy).to.have.been.calledOnceWithExactly(['a', 'b', 'c']);
    });

    it('should use without optional', () => {
        service.navigateBackWithOptional(['a', 'b', 'c'], undefined);

        expect(router.navigateSpy).to.have.been.calledOnceWithExactly(['a', 'b', 'c']);
    });
    it('should use with optional', () => {
        service.navigateBackWithOptional(['a', 'b', 'c'], 'd');

        expect(router.navigateSpy).to.have.been.calledOnceWithExactly(['a', 'b', 'c', 'd']);
    });
});
