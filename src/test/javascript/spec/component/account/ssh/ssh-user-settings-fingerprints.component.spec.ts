import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { SshUserSettingsFingerprintsComponent } from 'app/shared/user-settings/ssh-settings/fingerprints/ssh-user-settings-fingerprints.component';
import { SshUserSettingsFingerprintsService } from 'app/shared/user-settings/ssh-settings/fingerprints/ssh-user-settings-fingerprints.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { MockActivatedRoute } from '../../../helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute } from '@angular/router';

describe('SshUserSettingsFingerprintsComponent', () => {
    let fixture: ComponentFixture<SshUserSettingsFingerprintsComponent>;
    let comp: SshUserSettingsFingerprintsComponent;
    const mockFingerprints: { [key: string]: string } = {
        RSA: 'SHA512:abcde123',
    };

    let fingerPintsServiceMock: {
        getSshFingerprints: jest.Mock;
    };
    let translateService: TranslateService;

    beforeEach(async () => {
        fingerPintsServiceMock = {
            getSshFingerprints: jest.fn(),
        };
        jest.spyOn(console, 'error').mockImplementation(() => {});
        await TestBed.configureTestingModule({
            providers: [
                { provide: SshUserSettingsFingerprintsService, useValue: fingerPintsServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(SshUserSettingsFingerprintsComponent);
        comp = fixture.componentInstance;
        translateService = TestBed.inject(TranslateService);
        translateService.currentLang = 'en';

        fingerPintsServiceMock.getSshFingerprints.mockImplementation(() => Promise.resolve(mockFingerprints));
    });

    it('should display fingerprints', async () => {
        await comp.ngOnInit();
        await fixture.whenStable();

        expect(fingerPintsServiceMock.getSshFingerprints).toHaveBeenCalled();
    });
});
