import { TestBed, fakeAsync, inject, tick } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { of, throwError } from 'rxjs';

import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivateService } from 'app/core/account/activate/activate.service';
import { ActivateComponent } from 'app/core/account/activate/activate.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { provideHttpClient } from '@angular/common/http';

describe('ActivateComponent', () => {
    let comp: ActivateComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ActivateComponent],
            providers: [
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({ key: 'ABC123' }) },
                LocalStorageService,
                SessionStorageService,
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
            ],
        })
            .overrideTemplate(ActivateComponent, '')
            .compileComponents();
    });

    beforeEach(() => {
        const fixture = TestBed.createComponent(ActivateComponent);
        comp = fixture.componentInstance;
    });

    it('calls activate.get with the key from params', inject(
        [ActivateService],
        fakeAsync((service: ActivateService) => {
            jest.spyOn(service, 'get').mockReturnValue(of());

            comp.activateAccount();
            tick();

            expect(service.get).toHaveBeenCalledWith('ABC123');
        }),
    ));

    it('should set set success to true upon successful activation', inject(
        [ActivateService],
        fakeAsync((service: ActivateService) => {
            jest.spyOn(service, 'get').mockReturnValue(of({}));

            comp.activateAccount();
            tick();

            expect(comp.error).toBeFalse();
            expect(comp.success).toBeTrue();
        }),
    ));

    it('should set set error to true upon activation failure', inject(
        [ActivateService],
        fakeAsync((service: ActivateService) => {
            jest.spyOn(service, 'get').mockReturnValue(throwError(() => new Error('ERROR')));

            comp.activateAccount();
            tick();

            expect(comp.error).toBeTrue();
            expect(comp.success).toBeFalse();
        }),
    ));
});
