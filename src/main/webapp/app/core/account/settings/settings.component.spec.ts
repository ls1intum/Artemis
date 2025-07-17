import { TestBed, fakeAsync, inject, tick } from '@angular/core/testing';
import { FormBuilder } from '@angular/forms';
import { LocalStorageService } from 'app/shared/storage/local-storage.service';
import { SessionStorageService } from 'app/shared/storage/session-storage.service';
import { of, throwError } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { SettingsComponent } from 'app/core/account/settings/settings.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { User } from 'app/core/user/user.model';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { HttpResponse } from '@angular/common/http';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('SettingsComponent', () => {
    let comp: SettingsComponent;

    const accountValues: User = {
        internal: true,
        name: 'John Doe',
        firstName: 'John',
        lastName: 'Doe',
        activated: true,
        email: 'john.doe@mail.com',
        langKey: 'en',
        login: 'john',
        authorities: [],
        imageUrl: '',
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [SettingsComponent],
            providers: [
                FormBuilder,
                LocalStorageService,
                SessionStorageService,
                { provide: ProfileService, useClass: MockProfileService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideTemplate(SettingsComponent, '')
            .compileComponents();
    });

    beforeEach(() => {
        const fixture = TestBed.createComponent(SettingsComponent);
        comp = fixture.componentInstance;
        comp.isRegistrationEnabled = true;
    });

    it('should send the current identity upon save', inject(
        [AccountService],
        fakeAsync((service: AccountService) => {
            // GIVEN
            jest.spyOn(service, 'identity').mockReturnValue(Promise.resolve(accountValues));
            jest.spyOn(service, 'save').mockReturnValue(of(new HttpResponse({ body: {} })));
            jest.spyOn(service, 'authenticate');

            const settingsFormValues = {
                firstName: 'John',
                lastName: 'Doe',
                email: 'john.doe@mail.com',
                langKey: 'en',
            };

            // WHEN
            comp.ngOnInit();
            tick();
            comp.save();
            tick();

            // THEN
            expect(service.identity).toHaveBeenCalledOnce();
            expect(service.save).toHaveBeenCalledWith(accountValues);
            expect(service.authenticate).toHaveBeenCalledWith(accountValues);
            expect(comp.settingsForm.value).toEqual(settingsFormValues);
        }),
    ));

    it('should notify of success upon successful save', inject(
        [AccountService],
        fakeAsync((service: AccountService) => {
            // GIVEN
            jest.spyOn(service, 'identity').mockReturnValue(Promise.resolve(accountValues));
            jest.spyOn(service, 'save').mockReturnValue(of(new HttpResponse({ body: {} })));

            // WHEN
            comp.ngOnInit();
            tick();
            comp.save();
            tick();

            // THEN
            expect(comp.success).toBeTrue();
        }),
    ));

    it('should notify of error upon failed save', inject(
        [AccountService],
        fakeAsync((service: AccountService) => {
            // GIVEN
            jest.spyOn(service, 'identity').mockReturnValue(Promise.resolve(accountValues));
            jest.spyOn(service, 'save').mockReturnValue(throwError(() => new Error('ERROR')));

            // WHEN
            comp.ngOnInit();
            tick();

            comp.save();
            tick();

            // THEN
            expect(comp.success).toBeFalse();
        }),
    ));
});
