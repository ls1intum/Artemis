import { HttpClient } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';

/**
 * The versions of the material a lecture unit currently offers. A missing value means that kind of material does not exist for the unit.
 * <p>
 * `hasVideo` separates the two reasons a `videoVersion` can be missing: the video is gone, or the video is still there while its transcription is not. Only the first is
 * material that no longer exists.
 */
export interface LectureUnitMaterialVersions {
    attachmentVersion?: number;
    videoVersion?: number;
    hasVideo?: boolean;
}

/**
 * Fetches the versions of a lecture unit's material.
 * <p>
 * Called immediately before an Iris citation or point-out is followed, so that the comparison against its pinned version reflects the material as it is right now, rather
 * than as it was when the chat was loaded.
 */
@Service()
export class IrisMaterialVersionService {
    private readonly http = inject(HttpClient);

    getMaterialVersions(lectureUnitId: number): Observable<LectureUnitMaterialVersions> {
        return this.http.get<LectureUnitMaterialVersions>(`api/lecture/lecture-units/${lectureUnitId}/material-versions`);
    }
}
