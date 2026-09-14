/**
 * Vitest tests for ActivateComponent.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { of, throwError } from 'rxjs';

import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivateService } from 'app/account/activate/activate.service';
import { ActivateComponent } from 'app/account/activate/activate.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { provideHttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ActivateComponent', () => {
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

    it('calls activate with the key from params', () => {
        vi.spyOn(activateService, 'activate').mockReturnValue(of());

        comp.activateAccount();

        expect(activateService.activate).toHaveBeenCalledWith('ABC123');
    });

    it('should set success to true upon successful activation', () => {
        vi.spyOn(activateService, 'activate').mockReturnValue(of({}));

        comp.activateAccount();

        expect(comp.error()).toBe(false);
        expect(comp.success()).toBe(true);
    });

    it('should set error to true upon activation failure', () => {
        vi.spyOn(activateService, 'activate').mockReturnValue(throwError(() => new Error('ERROR')));

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
            expect(compDisabled.isRegistrationEnabled()).toBe(false);
        });

        it('should set isRegistrationEnabled to false when profile has registrationEnabled false', () => {
            expect(compDisabled.isRegistrationEnabled()).toBe(false);
        });
    });

    /**
     * The suites above render no template, and the page is only reachable on a server with registration
     * enabled -- so nothing else covers this markup.
     */
    describe('template', () => {
        let templateFixture: ComponentFixture<ActivateComponent>;

        beforeEach(async () => {
            TestBed.resetTestingModule();
            await TestBed.configureTestingModule({
                imports: [ActivateComponent],
                providers: [
                    { provide: ActivatedRoute, useValue: new MockActivatedRoute({ key: 'ABC123' }) },
                    { provide: TranslateService, useClass: MockTranslateService },
                    LocalStorageService,
                    SessionStorageService,
                    ProfileService,
                    provideHttpClient(),
                    provideRouter([]),
                ],
            }).compileComponents();

            vi.spyOn(TestBed.inject(ProfileService), 'getProfileInfo').mockReturnValue({ registrationEnabled: true } as any);
            vi.spyOn(TestBed.inject(ActivateService), 'activate').mockReturnValue(of());

            templateFixture = TestBed.createComponent(ActivateComponent);
            templateFixture.detectChanges();
        });

        const messages = () => [...templateFixture.nativeElement.querySelectorAll('tum-ui-message')] as HTMLElement[];

        it('shows nothing until the activation call has answered', () => {
            expect(messages()).toHaveLength(0);
        });

        it('confirms the activation and links on to signing in', () => {
            templateFixture.componentInstance.success.set(true);
            templateFixture.detectChanges();

            expect(messages()).toHaveLength(1);
            expect(templateFixture.nativeElement.querySelector('a')).not.toBeNull();
        });

        it('reports a failed activation', () => {
            templateFixture.componentInstance.error.set(true);
            templateFixture.detectChanges();

            expect(messages()).toHaveLength(1);
            expect(messages()[0].textContent).toContain('activate.messages.error');
        });

        it('renders nothing at all while registration is disabled', () => {
            templateFixture.componentInstance.isRegistrationEnabled.set(false);
            templateFixture.componentInstance.success.set(true);
            templateFixture.detectChanges();

            expect(messages()).toHaveLength(0);
        });
    });
});
