import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LtiConfigurationService } from 'app/admin/lti-configuration/lti-configuration.service';
import { Router } from '@angular/router';
import { SortService } from 'app/shared/service/sort.service';
import { LtiConfigurationComponent } from 'app/admin/lti-configuration/lti-configuration.component';
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
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { AlertService } from 'app/core/util/alert.service';
import { MockAlertService } from '../../helpers/mocks/service/mock-alert.service';

describe('LtiConfigurationComponent', () => {
    let component: LtiConfigurationComponent;
    let fixture: ComponentFixture<LtiConfigurationComponent>;
    let mockRouter: any;
    let mockActivatedRoute: any;
    let mockSortService: any;
    let mockLtiConfigurationService: any;
    let mockAlertService: AlertService;

    beforeEach(async () => {
        mockRouter = { navigate: jest.fn() };
        mockSortService = { sortByProperty: jest.fn() };
        mockActivatedRoute = {
            data: of({ defaultSort: 'id,desc' }),
            queryParamMap: of(
                new Map([
                    ['page', '1'],
                    ['sort', 'id,asc'],
                ]),
            ),
        };
        mockLtiConfigurationService = {
            query: jest.fn().mockReturnValue(
                of(
                    new HttpResponse({
                        body: [{ id: 1, registrationId: 'platform-1' }],
                        headers: new HttpHeaders({ 'X-Total-Count': '1' }),
                    }),
                ),
            ),
            deleteLtiPlatform: jest.fn().mockReturnValue(of({})),
        };
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
                { provide: Router, useValue: mockRouter },
                { provide: SortService, useValue: mockSortService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: mockActivatedRoute },
                { provide: LtiConfigurationService, useValue: mockLtiConfigurationService },
                { provide: AlertService, useClass: MockAlertService },
            ],
            schemas: [NO_ERRORS_SCHEMA],
        }).compileComponents();

        fixture = TestBed.createComponent(LtiConfigurationComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        component.predicate = 'id';
        mockAlertService = fixture.debugElement.injector.get(AlertService);
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize and load LTI platforms', () => {
        expect(component.platforms).toBeDefined();
        expect(component.page).toBe(1);
        expect(component.predicate).toBe('id');
        expect(component.ascending).toBeTrue();
        expect(mockLtiConfigurationService.query).toHaveBeenCalled();
        expect(component.platforms).toHaveLength(1);
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
        component.deleteLtiPlatform(platformIdToDelete);
        expect(mockLtiConfigurationService.deleteLtiPlatform).toHaveBeenCalledWith(platformIdToDelete);
        expect(mockRouter.navigate).toHaveBeenCalledWith(['admin', 'lti-configuration']);
    });

    it('should handle navigation on transition', () => {
        component.transition();
        expect(mockRouter.navigate).toHaveBeenCalledWith(['/admin/lti-configuration'], {
            queryParams: {
                page: component.page,
                sort: 'id,asc',
            },
        });
    });

    it('should handle errors on deleting LTI platform', () => {
        const errorResponse = new HttpErrorResponse({ status: 500, statusText: 'Server Error', error: { message: 'Error occurred' } });
        const errorSpy = jest.spyOn(mockAlertService, 'error');
        mockLtiConfigurationService.deleteLtiPlatform.mockReturnValue(throwError(() => errorResponse));
        component.deleteLtiPlatform(123);
        expect(errorSpy).toHaveBeenCalled();
    });
});
