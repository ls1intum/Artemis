import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { AboutUsComponent } from 'app/core/about-us/about-us.component';
import { ActivatedRoute } from '@angular/router';
import { StaticContentService } from 'app/shared/service/static-content.service';
import * as sinon from 'sinon';
import { AboutUsModel } from 'app/core/about-us/models/about-us-model';
import { BehaviorSubject, of } from 'rxjs';
import { MockProvider } from 'ng-mocks';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('AboutUsComponent', () => {
    let fixture: ComponentFixture<AboutUsComponent>;
    const sandbox = sinon.createSandbox();

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

    afterEach(function () {
        sandbox.restore();
    });

    it('load the json file from resources', fakeAsync(() => {
        const staticContentService = TestBed.inject(StaticContentService);
        const profileService = TestBed.inject(ProfileService);

        const getStaticJsonFromArtemisServerStub = sandbox.stub(staticContentService, 'getStaticJsonFromArtemisServer').returns(of(new AboutUsModel([], [])));
        const getProfileInfoSub = sandbox.stub(profileService, 'getProfileInfo');
        getProfileInfoSub.returns(new BehaviorSubject<ProfileInfo>({ inProduction: false, sshCloneURLTemplate: 'ssh://git@testserver.com:1234/' } as ProfileInfo).asObservable());

        fixture.detectChanges();
        tick();
        fixture.whenStable().then(() => {
            expect(getStaticJsonFromArtemisServerStub).to.have.been.calledOnce;
        });
    }));
});
