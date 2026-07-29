import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/**
 * The versions of the material a lecture unit currently offers. A missing value means that kind of material does not exist for the unit.
 */
export interface LectureUnitMaterialVersions {
    attachmentVersion?: number;
    videoVersion?: number;
}

/**
 * Fetches the versions of a lecture unit's material.
 * <p>
 * Called at the moment an Iris citation is clicked, so that the comparison against the version pinned into the citation reflects the material as it is right now, rather
 * than as it was when the chat was loaded.
 */
@Injectable({ providedIn: 'root' })
export class IrisCitationMaterialVersionService {
    private readonly http = inject(HttpClient);

    getMaterialVersions(lectureUnitId: number): Observable<LectureUnitMaterialVersions> {
        return this.http.get<LectureUnitMaterialVersions>(`api/lecture/lecture-units/${lectureUnitId}/material-versions`);
    }
}
