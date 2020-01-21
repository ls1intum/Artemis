import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { stub } from 'sinon';
import { Component, DebugElement } from '@angular/core';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { SinonStub } from 'sinon';
import { ArtemisTestModule } from '../../test.module';
import { ProgrammingExerciseUtilsModule } from 'app/entities/programming-exercise/utils/programming-exercise-utils.module';
import { ProfileService } from 'app/layouts/profiles/profile.service';
import { MockProfileService } from '../../mocks/mock-profile.service';
import { BehaviorSubject } from 'rxjs';
import { ProfileInfo } from 'app/layouts';
import { By } from '@angular/platform-browser';

chai.use(sinonChai);
const expect = chai.expect;

@Component({
    selector: 'jhi-test-component',
    template: '<a jhiBuildPlanLink [projectKey]="projectKey" [buildPlanId]="buildPlanId"></a>',
})
class TestComponent {
    projectKey = 'FOO';
    buildPlanId = 'BAR';
}

describe('BuildPlanLinkDirective', () => {
    let comp: TestComponent;
    let fixture: ComponentFixture<TestComponent>;
    let debugElement: DebugElement;
    let profileService: ProfileService;
    let getProfileInfoStub: SinonStub;
    let profileInfoSubject: BehaviorSubject<ProfileInfo | null>;
    let correctBuildPlan: string;

    const profileInfo = { buildPlanURLTemplate: 'https://some.url.com/plans/{buildPlanId}/path/{projectKey}' } as ProfileInfo;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ProgrammingExerciseUtilsModule],
            declarations: [TestComponent],
            providers: [{ provide: ProfileService, useClass: MockProfileService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TestComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;

                profileService = fixture.debugElement.injector.get(ProfileService);

                getProfileInfoStub = stub(profileService, 'getProfileInfo');

                profileInfoSubject = new BehaviorSubject<ProfileInfo | null>(profileInfo);
                getProfileInfoStub.returns(profileInfoSubject);

                correctBuildPlan = `https://some.url.com/plans/${comp.buildPlanId}/path/${comp.projectKey}`;
            });
    });

    afterEach(() => {
        getProfileInfoStub.restore();
        profileInfoSubject.complete();
    });

    it('should inject the correct build plan URL', fakeAsync(() => {
        fixture.detectChanges();
        tick();

        const linkEl = debugElement.query(By.css('a'));
        expect(linkEl.attributes['href']).to.be.equal(correctBuildPlan);
        expect(linkEl.attributes['target']).to.be.equal('_blank');
        expect(linkEl.attributes['rel']).to.be.equal('noopener noreferrer');
    }));
});
