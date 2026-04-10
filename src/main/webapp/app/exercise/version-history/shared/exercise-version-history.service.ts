import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { ParseLinks } from 'app/core/admin/system-notification-management/parse-links.service';
import { ExerciseSnapshotDTO } from 'app/exercise/synchronization/metadata/exercise-metadata-snapshot.dto';
import { convertDateStringFromServer } from 'app/shared/util/date.utils';
import dayjs from 'dayjs/esm';
import { Observable, map } from 'rxjs';
import { ExerciseVersionMetadata, ExerciseVersionPage } from 'app/exercise/version-history/shared/exercise-version-history.model';

/** Raw JSON shape returned by the server before date conversion. */
interface ExerciseVersionMetadataResponse {
    id: number;
    author?: ExerciseVersionMetadata['author'];
    createdDate?: string;
}

/**
 * Service for fetching exercise version history data.
 *
 * Provides paginated version metadata and full snapshot retrieval for the
 * exercise version history view.
 */
@Injectable({ providedIn: 'root' })
export class ExerciseVersionHistoryService {
    private readonly http = inject(HttpClient);
    private readonly parseLinks = inject(ParseLinks);

    private readonly resourceUrl = 'api/exercise';

    /**
     * Fetches a paginated list of version metadata for the given exercise.
     *
     * Pagination information (next page, total count) is extracted from
     * the `Link` and `X-Total-Count` response headers.
     *
     * @param exerciseId the exercise whose versions to load
     * @param page       zero-based page index
     * @param size       number of items per page
     * @returns an observable that emits a single {@link ExerciseVersionPage}
     */
    getVersions(exerciseId: number, page: number, size: number): Observable<ExerciseVersionPage> {
        const params = new HttpParams().set('page', page).set('size', size);
        return this.http
            .get<ExerciseVersionMetadataResponse[]>(`${this.resourceUrl}/${exerciseId}/versions`, {
                params,
                observe: 'response',
            })
            .pipe(
                map((response) => {
                    const versions = (response.body ?? []).map((item) => this.convertMetadata(item));
                    return {
                        versions,
                        nextPage: this.parseNextPage(response.headers),
                        totalItems: Number(response.headers.get('X-Total-Count') ?? 0),
                    };
                }),
            );
    }

    /**
     * Fetches the full exercise snapshot for a specific version.
     *
     * @param exerciseId the exercise that owns the version
     * @param versionId  the version to retrieve the snapshot for
     * @returns an observable that emits the complete {@link ExerciseSnapshotDTO}
     */
    getSnapshot(exerciseId: number, versionId: number): Observable<ExerciseSnapshotDTO> {
        return this.http.get<ExerciseSnapshotDTO>(`${this.resourceUrl}/${exerciseId}/version/${versionId}`);
    }

    /** Converts a raw server response into a typed {@link ExerciseVersionMetadata} with a dayjs date. */
    private convertMetadata(item: ExerciseVersionMetadataResponse): ExerciseVersionMetadata {
        return {
            id: item.id,
            author: item.author,
            createdDate: item.createdDate ? (convertDateStringFromServer(item.createdDate) ?? dayjs(item.createdDate)) : undefined,
        };
    }

    /** Extracts the `next` page number from a RFC 5988 `Link` header, or `undefined` if absent. */
    private parseNextPage(headers: HttpHeaders): number | undefined {
        const linkHeader = headers.get('link');
        if (!linkHeader) {
            return undefined;
        }

        try {
            return this.parseLinks.parse(linkHeader).next;
        } catch {
            return undefined;
        }
    }
}
