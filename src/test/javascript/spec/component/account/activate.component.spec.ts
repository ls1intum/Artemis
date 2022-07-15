import { fakeAsync, inject, TestBed, tick } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ArtemisTestModule } from '../../test.module';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { ActivateService } from 'app/account/activate/activate.service';
import { ActivateComponent } from 'app/account/activate/activate.component';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { MockProfileService } from '../../helpers/mocks/service/mock-profile.service';

describe('ActivateComponent', () => {
    let comp: ActivateComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ActivateComponent],
            providers: [
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({ key: 'ABC123' }) },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: ProfileService, useClass: MockProfileService },
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
