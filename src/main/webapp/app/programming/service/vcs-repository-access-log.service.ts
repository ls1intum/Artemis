import { Injectable } from '@angular/core';
import { SearchResult, SearchTermPageableSearch } from 'app/shared/table/pageable-table';
import { HttpParams } from '@angular/common/http';
import { VcsAccessLogDTO } from 'app/entities/vcs-access-log-entry.model';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { BaseApiHttpService } from 'app/shared/service/base-api-http.service';

@Injectable({ providedIn: 'root' })
export class VcsRepositoryAccessLogService extends BaseApiHttpService {
    search(pageable: SearchTermPageableSearch, options: { exerciseId: number; repositoryId: number; repositoryType: RepositoryType }): Promise<SearchResult<VcsAccessLogDTO>> {
        const params = new HttpParams()
            .set('page', pageable.page.toString())
            .set('pageSize', pageable.pageSize.toString())
            .set('sortingOrder', pageable.sortingOrder)
            .set('sortedColumn', pageable.sortedColumn)
            .set('repositoryId', options.repositoryId);

        return this.get<SearchResult<VcsAccessLogDTO>>(`programming-exercises/${options.exerciseId}/repository/${options.repositoryType}/vcs-access-log`, {
            params,
        });
    }
}
