import { LectureUnitApi } from 'app/openapi/api/lecture-unit-api';
import type { LectureUnitMaterialVersions } from 'app/openapi/model/lecture-unit-material-versions';
import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';

/**
 * Fetches the versions of a lecture unit's material.
 * <p>
 * Called immediately before an Iris citation or point-out is followed, so that the comparison against its pinned version reflects the material as it is right now, rather
 * than as it was when the chat was loaded.
 */
@Service()
export class IrisMaterialVersionService {
    private readonly lectureUnitApi = inject(LectureUnitApi);

    private latestNavigation = 0;

    /** Starts a user-requested navigation across all citation messages and point-out markers. */
    beginNavigation(): number {
        return ++this.latestNavigation;
    }

    isCurrentNavigation(navigation: number): boolean {
        return navigation === this.latestNavigation;
    }

    getMaterialVersions(lectureUnitId: number): Observable<LectureUnitMaterialVersions> {
        return this.lectureUnitApi.getLectureUnitMaterialVersions(lectureUnitId);
    }
}
