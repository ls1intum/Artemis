import { ArtemisTestModule } from '../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { OrionOutdatedComponent } from 'app/shared/orion/outdated-plugin-warning/orion-outdated.component';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { MockProfileService } from '../../helpers/mocks/service/mock-profile.service';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';

describe('OrionOutdatedComponent', () => {
    let comp: OrionOutdatedComponent;
    let fixture: ComponentFixture<OrionOutdatedComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [OrionOutdatedComponent, TranslatePipeMock],
            providers: [
                { provide: ActivatedRoute, useValue: { queryParams: of({ versionString: 'version' }) } },
                { provide: ProfileService, useClass: MockProfileService },
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
