import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LtiConfigurationService } from 'app/admin/lti-configuration/lti-configuration.service';
import { Router } from '@angular/router';
import { SortService } from 'app/shared/service/sort.service';
import { LtiConfigurationComponent } from 'app/admin/lti-configuration/lti-configuration.component';
import { MockLtiConfigurationService } from '../../helpers/mocks/service/mock-lti-configuration-service';
import { LtiPlatformConfiguration } from 'app/admin/lti-configuration/lti-configuration.model';
import { NgbNavModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { CopyIconButtonComponent } from 'app/shared/components/copy-icon-button/copy-icon-button.component';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';

describe('LtiConfigurationComponent', () => {
    let component: LtiConfigurationComponent;
    let fixture: ComponentFixture<LtiConfigurationComponent>;
    let mockRouter: any;
    let mockSortService: any;
    let ltiConfigurationService: LtiConfigurationService;

    beforeEach(async () => {
        mockRouter = { navigate: jest.fn() };
        mockSortService = { sortByProperty: jest.fn() };

        await TestBed.configureTestingModule({
            imports: [NgbNavModule, FontAwesomeModule],
            declarations: [
                LtiConfigurationComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(HelpIconComponent),
                MockComponent(CopyIconButtonComponent),
                MockDirective(SortDirective),
                MockDirective(SortByDirective),
                MockDirective(DeleteButtonDirective),
                MockRouterLinkDirective,
            ],
            providers: [
                { provide: LtiConfigurationService, useClass: MockLtiConfigurationService },
                { provide: Router, useValue: mockRouter },
                { provide: SortService, useValue: mockSortService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(LtiConfigurationComponent);
        component = fixture.componentInstance;
        ltiConfigurationService = TestBed.inject(LtiConfigurationService);
        fixture.detectChanges();
        component.predicate = 'id';
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize and load LTI platforms', () => {
        expect(component.platforms).toBeDefined();
        expect(component.platforms).toHaveLength(2);
    });

    it('should generate URLs correctly', () => {
        // Test for getDynamicRegistrationUrl
        expect(component.getDynamicRegistrationUrl()).toContain('/lti/dynamic-registration');

        // Test for getDeepLinkingUrl
        expect(component.getDeepLinkingUrl()).toContain('/api/public/lti13/deep-link');

        // Test for getToolUrl
        expect(component.getToolUrl()).toContain('/courses');

        // Test for getKeysetUrl
        expect(component.getKeysetUrl()).toContain('/.well-known/jwks.json');

        // Test for getInitiateLoginUrl
        expect(component.getInitiateLoginUrl()).toContain('/api/public/lti13/initiate-login');

        // Test for getRedirectUri
        expect(component.getRedirectUri()).toContain('/api/public/lti13/auth-callback');
    });

    it('should sort platforms', () => {
        const dummyPlatforms: LtiPlatformConfiguration[] = [
            {
                id: 1,
                customName: 'Platform A',
                clientId: 'platform-a',
                authorizationUri: 'platformA.com/auth-login',
                jwkSetUri: 'platformA.com/jwk',
                tokenUri: 'platformA.com/token',
            },
            {
                id: 2,
                customName: 'Platform B',
                clientId: 'platform-b',
                authorizationUri: 'platformB.com/auth-login',
                jwkSetUri: 'platformB.com/jwk',
                tokenUri: 'platformB.com/token',
            },
        ];
        component.platforms = dummyPlatforms;
        component.sortRows();
        expect(mockSortService.sortByProperty).toHaveBeenCalledWith(dummyPlatforms, 'id', false);
    });

    it('should delete an LTI platform and navigate', () => {
        const platformIdToDelete = 1;
        const deleteSpy = jest.spyOn(ltiConfigurationService, 'deleteLtiPlatform');

        component.deleteLtiPlatform(platformIdToDelete);

        expect(deleteSpy).toHaveBeenCalledWith(platformIdToDelete);
        expect(mockRouter.navigate).toHaveBeenCalledWith(['admin', 'lti-configuration']);
    });
});
