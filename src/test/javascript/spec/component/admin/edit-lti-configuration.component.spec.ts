import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { NgbNavModule } from '@ng-bootstrap/ng-bootstrap';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisTestModule } from '../../test.module';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { EditLtiConfigurationComponent } from 'app/admin/lti-configuration/edit-lti-configuration.component';
import { LtiConfigurationService } from 'app/admin/lti-configuration/lti-configuration.service';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { LtiPlatformConfiguration } from 'app/admin/lti-configuration/lti-configuration.model';
import { of, throwError } from 'rxjs';
import { HttpClient, HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';

describe('Edit LTI Configuration Component', () => {
    let comp: EditLtiConfigurationComponent;
    let fixture: ComponentFixture<EditLtiConfigurationComponent>;
    let ltiConfigurationService: LtiConfigurationService;

    const router = new MockRouter();
    let route: ActivatedRoute;

    const platformConfiguration = {
        id: 1,
        registrationId: 'registration_id',
        originalUrl: 'original_url',
        customName: 'custom_platform',
        clientId: 'client_id',
        authorizationUri: 'auth_uri',
        jwkSetUri: 'jwkSet_uri',
        tokenUri: 'token_uri',
    } as LtiPlatformConfiguration;

    beforeEach(() => {
        route = {
            snapshot: { paramMap: convertToParamMap({ platformId: '1' }) },
        } as ActivatedRoute;

        const httpClientMock = {
            get: jest.fn().mockReturnValue(of(platformConfiguration)),
        };

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgbNavModule, MockModule(NgbTooltipModule), MockModule(ReactiveFormsModule)],
            declarations: [EditLtiConfigurationComponent, MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe), MockComponent(HelpIconComponent)],
            providers: [
                MockProvider(LtiConfigurationService),
                { provide: Router, useValue: router },
                { provide: ActivatedRoute, useValue: route },
                { provide: HttpClient, useValue: httpClientMock },
                MockProvider(AlertService),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(EditLtiConfigurationComponent);
                comp = fixture.componentInstance;
                ltiConfigurationService = TestBed.inject(LtiConfigurationService);
                jest.spyOn(ltiConfigurationService, 'getLtiPlatformById').mockReturnValue(of(platformConfiguration));
            });
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should initialize', fakeAsync(() => {
        fixture.detectChanges();
        expect(comp).toBeTruthy();
        expect(comp.platform).toEqual(platformConfiguration);
        expect(comp.platformConfigurationForm).toBeDefined();
        expect(comp.platformConfigurationForm.get('registrationId')?.value).toEqual(platformConfiguration.registrationId);
        expect(comp.platformConfigurationForm.get('originalUrl')?.value).toEqual(platformConfiguration.originalUrl);
        expect(comp.platformConfigurationForm.get('customName')?.value).toEqual(platformConfiguration.customName);
        expect(comp.platformConfigurationForm.get('clientId')?.value).toEqual(platformConfiguration.clientId);
        expect(comp.platformConfigurationForm.get('authorizationUri')?.value).toEqual(platformConfiguration.authorizationUri);
        expect(comp.platformConfigurationForm.get('jwkSetUri')?.value).toEqual(platformConfiguration.jwkSetUri);
        expect(comp.platformConfigurationForm.get('tokenUri')?.value).toEqual(platformConfiguration.tokenUri);
    }));

    it('should save and navigate', async () => {
        fixture.detectChanges();

        const changedConfiguration = updateConfiguration();

        const updateResponse: HttpResponse<void> = new HttpResponse({
            status: 200,
        });

        const updatedStub = jest.spyOn(ltiConfigurationService, 'updateLtiPlatformConfiguration').mockReturnValue(of(updateResponse));
        const navigateSpy = jest.spyOn(router, 'navigate');

        expect(comp.isSaving).toBeFalse();

        await comp.save();

        expect(updatedStub).toHaveBeenCalledOnce();
        expect(updatedStub).toHaveBeenCalledWith(changedConfiguration);
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['admin', 'lti-configuration']);
    });

    it('should handle save failure and display error', async () => {
        fixture.detectChanges();

        const changedConfiguration = updateConfiguration();

        const errorResponse = new HttpErrorResponse({
            error: 'test error',
            status: 400,
            statusText: 'Bad Request',
        });

        const updateErrorStub = jest.spyOn(ltiConfigurationService, 'updateLtiPlatformConfiguration').mockReturnValue(throwError(() => errorResponse));
        const navigateSpy = jest.spyOn(router, 'navigate');

        expect(comp.isSaving).toBeFalse();
        await comp.save();
        expect(updateErrorStub).toHaveBeenCalledOnce();
        expect(updateErrorStub).toHaveBeenCalledWith(changedConfiguration);

        expect(navigateSpy).not.toHaveBeenCalled();
    });

    function updateConfiguration() {
        const changedConfiguration = {
            ...platformConfiguration,
            customName: 'custom_name_2',
        } as LtiPlatformConfiguration;

        comp.platformConfigurationForm = new FormGroup({
            id: new FormControl(changedConfiguration.id),
            registrationId: new FormControl(changedConfiguration.registrationId),
            originalUrl: new FormControl(changedConfiguration.originalUrl),
            customName: new FormControl(changedConfiguration.customName),
            clientId: new FormControl(changedConfiguration.clientId),
            authorizationUri: new FormControl(changedConfiguration.authorizationUri),
            tokenUri: new FormControl(changedConfiguration.tokenUri),
            jwkSetUri: new FormControl(changedConfiguration.jwkSetUri),
        });
        return changedConfiguration;
    }
});
