import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { FooterComponent } from 'app/shared/layouts/footer/footer.component';
import { TranslateModule } from '@ngx-translate/core';
import { ArtemisTestModule } from '../../test.module';
import { MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('FooterComponent', () => {
    let component: FooterComponent;
    let fixture: ComponentFixture<FooterComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [FooterComponent, MockPipe(ArtemisTranslatePipe)],
            imports: [ArtemisTestModule, RouterTestingModule, TranslateModule.forRoot()],
        }).compileComponents();
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
            fixture.detectChanges();

            const gitInfoElement = fixture.debugElement.nativeElement.querySelector('.footer-git');
            expect(gitInfoElement).not.toBeNull();
        });
    });

    it('should not display git information if in production and not a test server', () => {
        component.isProduction = true;
        component.isTestServer = false;
        fixture.detectChanges();

        const gitInfoElement = fixture.debugElement.nativeElement.querySelector('.footer-git-wrapper');
        expect(gitInfoElement).toBeNull();
    });

    describe('Git Information Detailed Testing', () => {
        beforeEach(() => {
            component.gitBranch = 'main';
            component.gitCommitId = 'abc123';
            component.gitTimestamp = '2023-04-01T12:00:00Z';
            component.gitCommitUser = 'user123';
            component.isProduction = false;
            component.isTestServer = false;
            fixture.detectChanges();
        });

        it('should display all git information', () => {
            // Assuming there's only one .footer-git and it contains all git info divs
            const footerGit = fixture.debugElement.nativeElement.querySelector('.footer-git');
            const gitElements = footerGit.querySelectorAll('div');

            // Ensure that we have exactly four git info divs
            expect(gitElements).toHaveLength(4);

            // Check each element for the expected text content
            expect(gitElements[0].textContent).toContain('main'); // for git branch
            expect(gitElements[1].textContent).toContain('abc123'); // for git commit ID
            expect(gitElements[2].textContent).toContain('2023-04-01T12:00:00Z'); // for git timestamp
            expect(gitElements[3].textContent).toContain('user123'); // for git commit user
        });
    });
});
