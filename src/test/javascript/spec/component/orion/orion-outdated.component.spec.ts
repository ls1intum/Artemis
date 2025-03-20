import { ComponentFixture, TestBed } from '@angular/core/testing';
import { OrionOutdatedComponent } from 'app/shared/orion/outdated-plugin-warning/orion-outdated.component';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from '../../helpers/mocks/service/mock-profile.service';
import { ArtemisTranslatePipe } from '../../../../../main/webapp/app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('OrionOutdatedComponent', () => {
    let comp: OrionOutdatedComponent;
    let fixture: ComponentFixture<OrionOutdatedComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                MockPipe(ArtemisTranslatePipe),
                { provide: ActivatedRoute, useValue: { queryParams: of({ versionString: 'version' }) } },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(OrionOutdatedComponent);
                comp = fixture.componentInstance;
                jest.spyOn(TestBed.inject(ProfileService), 'getProfileInfo').mockReturnValue(of({ allowedMinimumOrionVersion: 'minVersion' } as any));
            });
    });

    it('should initialize correctly', () => {
        fixture.detectChanges();

        expect(comp.versionString).toBe('version');
        expect(comp.allowedMinimumVersion).toBe('minVersion');
    });
});
