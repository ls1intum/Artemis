import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { CopyIconButtonComponent } from 'app/shared/components/copy-icon-button/copy-icon-button.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { NgbNavModule } from '@ng-bootstrap/ng-bootstrap';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SortService } from 'app/shared/service/sort.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';
import { ArtemisTestModule } from '../../test.module';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { LtiConfigurationComponent } from 'app/admin/lti-configuration/lti-configuration.component';
import { NgModule } from '@angular/core';
import { SortDirective } from 'app/shared/sort/sort.directive';

describe('LTI Configuration Component', () => {
    let comp: LtiConfigurationComponent;
    let fixture: ComponentFixture<LtiConfigurationComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgbNavModule, MockModule(NgbTooltipModule), NgModule],
            declarations: [
                LtiConfigurationComponent,
                MockDirective(TranslateDirective),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(HelpIconComponent),
                MockComponent(CopyIconButtonComponent),
                MockDirective(SortDirective),
                MockRouterLinkDirective,
            ],
            providers: [MockProvider(CourseManagementService), MockProvider(SortService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(LtiConfigurationComponent);
                comp = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).not.toBeNull();
    });

    describe('OnInit', () => {
        it('should load lti 1.3 information', () => {
            comp.ngOnInit();

            expect(comp.getDynamicRegistrationUrl()).toBe(`${location.origin}/lti/dynamic-registration`);
            expect(comp.getDeepLinkingUrl()).toBe(`${location.origin}/api/public/lti13/deep-linking`);
            expect(comp.getToolUrl()).toBe(`${location.origin}/courses`);
            expect(comp.getKeysetUrl()).toBe(`${location.origin}/.well-known/jwks.json`);
            expect(comp.getInitiateLoginUrl()).toBe(`${location.origin}/api/public/lti13/initiate-login`);
            expect(comp.getRedirectUri()).toBe(`${location.origin}/api/public/lti13/auth-callback`);
            expect(comp.predicate).toBe('id');
        });
    });
});
