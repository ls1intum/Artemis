import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { expectedProfileInfo } from 'app/core/layouts/profiles/shared/profile.service.spec';
import { MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { RouterModule } from '@angular/router';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { FooterComponent } from 'app/core/layouts/footer/footer.component';
import dayJs from 'dayjs/esm';

describe('FooterComponent', () => {
    let component: FooterComponent;
    let fixture: ComponentFixture<FooterComponent>;
    let profileService: ProfileService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [FooterComponent, MockPipe(ArtemisTranslatePipe)],
            imports: [TranslateModule.forRoot(), RouterModule.forRoot([])],
            providers: [{ provide: ProfileService, useClass: MockProfileService }],
        })
            .compileComponents()
            .then(() => {
                profileService = TestBed.inject(ProfileService);
                jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(expectedProfileInfo);
            });
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(FooterComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should have an About Us link with correct routing', () => {
        const aboutUsLink = fixture.debugElement.nativeElement.querySelector('#about');
        expect(aboutUsLink.getAttribute('href')).toContain('/about');
    });
    it('should have an Imprint link with correct routing', () => {
        const aboutUsLink = fixture.debugElement.nativeElement.querySelector('#imprint');
        expect(aboutUsLink.getAttribute('href')).toContain('/imprint');
    });

    describe('Git Information', () => {
        it('should display git information if not in production or on a test server', () => {
            component.isProduction = false;
            component.isTestServer = true;
            fixture.changeDetectorRef.detectChanges();

            const gitInfoElement = fixture.debugElement.nativeElement.querySelector('.footer-git');
            expect(gitInfoElement).not.toBeNull();
        });
    });

    it('should not display git information if in production and not a test server', () => {
        component.isProduction = true;
        component.isTestServer = false;
        fixture.changeDetectorRef.detectChanges();

        const gitInfoElement = fixture.debugElement.nativeElement.querySelector('.footer-git-wrapper');
        expect(gitInfoElement).toBeNull();
    });

    describe('Git Information Detailed Testing', () => {
        beforeEach(() => {
            component.gitBranch = 'main';
            component.gitCommitId = 'abc123';
            component.gitTimestamp = dayJs.utc('2023-04-01T12:00:00Z');
            component.gitCommitUser = 'user123';
            component.isProduction = false;
            component.isTestServer = false;
            fixture.changeDetectorRef.detectChanges();
        });

        it('should display all git information', () => {
            const footerGit = fixture.debugElement.nativeElement.querySelector('.footer-git');

            expect(footerGit.textContent).toContain('main'); // for git branch
            expect(footerGit.textContent).toContain('abc123'); // for git commit ID
            expect(footerGit.textContent).toContain('2023-04-01 12:00'); // for git timestamp
            expect(footerGit.textContent).toContain('user123'); // for git commit user
        });
    });
});
