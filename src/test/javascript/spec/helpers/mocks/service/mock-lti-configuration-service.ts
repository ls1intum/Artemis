import { of } from 'rxjs';
import { LtiPlatformConfiguration } from 'app/admin/lti-configuration/lti-configuration.model';

export class MockLtiConfigurationService {
    private dummyLtiPlatforms: LtiPlatformConfiguration[] = [
        {
            id: 1,
            registrationId: 'platform-1',
            customName: 'Platform A',
            originalUrl: 'lms1.com',
            clientId: 'client-id-a',
            authorizationUri: 'lms1.com/aut-callback',
            tokenUri: 'lms1.com/token',
            jwkSetUri: 'lms1.com/jwk',
        },
        {
            id: 2,
            registrationId: 'platform-2',
            customName: 'Platform B',
            originalUrl: 'lms2.com',
            clientId: 'client-id-b',
            authorizationUri: 'lms2.com/aut-callback',
            tokenUri: 'lms2.com/token',
            jwkSetUri: 'lms2.com/jwk',
        },
    ];

    public findAll() {
        return of(this.dummyLtiPlatforms);
    }

    public updateLtiPlatformConfiguration(config: LtiPlatformConfiguration) {
        const index = this.dummyLtiPlatforms.findIndex((p) => p.id === config.id);
        if (index !== -1) {
            this.dummyLtiPlatforms[index] = config;
        }
        return of({ status: 200, statusText: 'OK' });
    }

    public deleteLtiPlatform(platformId: number) {
        this.dummyLtiPlatforms = this.dummyLtiPlatforms.filter((p) => p.id !== platformId);
        return of({ status: 200, statusText: 'Deleted' });
    }
}
