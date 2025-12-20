/**
 * Vitest tests for ActivateComponent.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ActivatedRoute } from '@angular/router';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { of, throwError } from 'rxjs';

import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivateService } from 'app/core/account/activate/activate.service';
import { ActivateComponent } from 'app/core/account/activate/activate.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { provideHttpClient } from '@angular/common/http';

describe('ActivateComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: ActivateComponent;
    let activateService: ActivateService;
    let profileService: ProfileService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ActivateComponent],
            providers: [
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({ key: 'ABC123' }) },
                LocalStorageService,
                SessionStorageService,
                ProfileService,
                provideHttpClient(),
            ],
        })
            .overrideTemplate(ActivateComponent, '')
            .compileComponents();

        profileService = TestBed.inject(ProfileService);
    });

    beforeEach(() => {
        // Default: registration enabled
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ registrationEnabled: true } as any);

        const fixture = TestBed.createComponent(ActivateComponent);
        comp = fixture.componentInstance;
        activateService = TestBed.inject(ActivateService);
    });

    it('calls activate.get with the key from params', () => {
        vi.spyOn(activateService, 'get').mockReturnValue(of());

        comp.activateAccount();

        expect(activateService.get).toHaveBeenCalledWith('ABC123');
    });

    it('should set success to true upon successful activation', () => {
        vi.spyOn(activateService, 'get').mockReturnValue(of({}));

        comp.activateAccount();

        expect(comp.error()).toBe(false);
        expect(comp.success()).toBe(true);
    });

    it('should set error to true upon activation failure', () => {
        vi.spyOn(activateService, 'get').mockReturnValue(throwError(() => new Error('ERROR')));

        comp.activateAccount();

        expect(comp.error()).toBe(true);
        expect(comp.success()).toBe(false);
    });

    it('should call activateAccount on ngOnInit when registration is enabled', () => {
        const activateAccountSpy = vi.spyOn(comp, 'activateAccount').mockImplementation(() => {});

        comp.ngOnInit();

        expect(activateAccountSpy).toHaveBeenCalledOnce();
    });

    describe('when registration is disabled', () => {
        let compDisabled: ActivateComponent;

        beforeEach(() => {
            // Override ProfileService mock to return registrationEnabled: false
            vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ registrationEnabled: false } as any);

            const fixture = TestBed.createComponent(ActivateComponent);
            compDisabled = fixture.componentInstance;
        });

        it('should not call activateAccount on ngOnInit when registration is disabled', () => {
            const activateAccountSpy = vi.spyOn(compDisabled, 'activateAccount');

            compDisabled.ngOnInit();

            expect(activateAccountSpy).not.toHaveBeenCalled();
            expect(compDisabled.isRegistrationEnabled).toBe(false);
        });

        it('should set isRegistrationEnabled to false when profile has registrationEnabled false', () => {
            expect(compDisabled.isRegistrationEnabled).toBe(false);
        });
    });
});
