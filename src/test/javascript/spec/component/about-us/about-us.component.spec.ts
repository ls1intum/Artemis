import { ArtemisTestModule } from '../../test.module';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { AboutUsComponent } from 'app/core/about-us/about-us.component';
import { ActivatedRoute } from '@angular/router';
import { StaticContentService } from 'app/shared/service/static-content.service';
import { AboutUsModel } from 'app/core/about-us/models/about-us-model';
import { BehaviorSubject, of } from 'rxjs';
import { MockProvider } from 'ng-mocks';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { ContributorModel } from 'app/core/about-us/models/contributor-model';

describe('AboutUsComponent', () => {
    let fixture: ComponentFixture<AboutUsComponent>;

    const route = { snapshot: { url: ['about'] } } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [AboutUsComponent, TranslatePipeMock],
            providers: [{ provide: ActivatedRoute, useValue: route }, MockProvider(ProfileService), MockProvider(StaticContentService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AboutUsComponent);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('load the json file from resources', fakeAsync(() => {
        const staticContentService = TestBed.inject(StaticContentService);
        const profileService = TestBed.inject(ProfileService);

        const getStaticJsonFromArtemisServerStub = jest.spyOn(staticContentService, 'getStaticJsonFromArtemisServer').mockReturnValue(of(new AboutUsModel([], [])));
        const getProfileInfoSub = jest.spyOn(profileService, 'getProfileInfo');
        getProfileInfoSub.mockReturnValue(
            new BehaviorSubject<ProfileInfo>({ inProduction: false, sshCloneURLTemplate: 'ssh://git@testserver.com:1234/' } as ProfileInfo).asObservable(),
        );

        fixture.detectChanges();
        tick();
        fixture.whenStable().then(() => {
            expect(getStaticJsonFromArtemisServerStub).toHaveBeenCalledTimes(1);
        });
    }));

    it('load and display contributors', fakeAsync(() => {
        const staticContentService = TestBed.inject(StaticContentService);
        const profileService = TestBed.inject(ProfileService);

        const fullName = 'Full Name';
        const photoDirectory = 'Photo Directory';
        const role = 'ADMIN';
        const website = 'www.website.de';

        const contributors = [new ContributorModel(fullName, photoDirectory, role, website)];

        const getStaticJsonFromArtemisServerStub = jest.spyOn(staticContentService, 'getStaticJsonFromArtemisServer').mockReturnValue(of(new AboutUsModel([], contributors)));
        const getProfileInfoStub = jest
            .spyOn(profileService, 'getProfileInfo')
            .mockReturnValue(of({ inProduction: false, sshCloneURLTemplate: 'ssh://git@testserver.com:1234/' } as ProfileInfo));

        fixture.detectChanges();
        tick();
        fixture.whenStable().then(() => {
            expect(getStaticJsonFromArtemisServerStub).toHaveBeenCalledTimes(1);
            expect(getProfileInfoStub).toHaveBeenCalledTimes(1);
            expect(fixture.debugElement.nativeElement.querySelector('#contributorsName').innerHTML).toBe(fullName);
        });
    }));
});
