import { Service, inject } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { LtiPlatformConfiguration } from 'app/lti/shared/entities/lti-configuration.model';
import { AdminLtiConfigurationApi } from 'app/openapi/api/admin-lti-configuration-api';
import { LtiPlatformConfigurationUpdate } from 'app/openapi/model/lti-platform-configuration-update';

/**
 * Pagination and sorting parameters for querying LTI platform configurations.
 */
export interface LtiPlatformConfigurationQuery {
    page?: number;
    size?: number;
    sort?: string[];
}

@Service()
export class LtiConfigurationService {
    private readonly api = inject(AdminLtiConfigurationApi);

    /**
     * Sends a GET request to retrieve all lti platform configurations
     */
    query(req?: LtiPlatformConfigurationQuery): Observable<HttpResponse<LtiPlatformConfiguration[]>> {
        return this.api.getAllConfiguredLtiPlatforms(req?.page, req?.size, req?.sort);
    }

    /**
     * Adds an LTI platform configuration.
     * @param ltiPlatformConfiguration The configuration to add.
     * @return Observable of the HTTP response.
     */
    addLtiPlatformConfiguration(ltiPlatformConfiguration: LtiPlatformConfigurationUpdate): Observable<LtiPlatformConfiguration> {
        return this.api.addLtiPlatformConfiguration(this.toDTO(ltiPlatformConfiguration));
    }

    /**
     * Updates an LTI platform configuration.
     * @param ltiPlatformConfiguration The configuration to update.
     * @return Observable of the HTTP response.
     */
    updateLtiPlatformConfiguration(ltiPlatformConfiguration: LtiPlatformConfigurationUpdate): Observable<LtiPlatformConfiguration> {
        return this.api.updateLtiPlatformConfiguration(this.toDTO(ltiPlatformConfiguration));
    }

    private toDTO(config: LtiPlatformConfigurationUpdate): LtiPlatformConfigurationUpdate {
        return {
            id: config.id,
            registrationId: config.registrationId,
            clientId: config.clientId,
            originalUrl: config.originalUrl,
            customName: config.customName,
            authorizationUri: config.authorizationUri,
            jwkSetUri: config.jwkSetUri,
            tokenUri: config.tokenUri,
        };
    }

    /**
     * Deletes an LTI platform on the server using a DELETE request.
     * @param platformId The platform id.
     */
    deleteLtiPlatform(platformId: number): Observable<void> {
        return this.api.deleteLtiPlatformConfiguration(platformId);
    }

    getLtiPlatformById(platformId: number): Observable<LtiPlatformConfiguration> {
        return this.api.getLtiPlatformConfiguration(platformId);
    }
}
