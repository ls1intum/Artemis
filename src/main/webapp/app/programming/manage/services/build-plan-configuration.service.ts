import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { BuildPlanPhases } from 'app/programming/shared/entities/build-plan-phases.model';

/**
 * Payload for updating the build plan configuration from the dedicated build plan editor.
 * Matches the server-side UpdateBuildPlanConfigurationDTO.
 */
export interface UpdateBuildPlanConfiguration {
    buildPlan: BuildPlanPhases;
    timeoutSeconds: number;
    dockerFlags?: string;
}

@Injectable({ providedIn: 'root' })
export class BuildPlanConfigurationService {
    private http = inject(HttpClient);

    private resourceUrl = 'api/programming/programming-exercises';

    /**
     * Updates the build plan configuration (build phases, Docker image, timeout, and Docker flags) of a programming exercise.
     *
     * @param exerciseId    the id of the programming exercise whose build config should be updated
     * @param configuration the new build plan configuration
     * @return the server response
     */
    updateBuildPlanConfiguration(exerciseId: number, configuration: UpdateBuildPlanConfiguration): Observable<HttpResponse<object>> {
        return this.http.put<object>(`${this.resourceUrl}/${exerciseId}/build-config`, configuration, { observe: 'response' });
    }
}
