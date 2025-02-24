import { Injectable } from '@angular/core';
import { SearchResult, SearchTermPageableSearch } from 'app/shared/table/pageable-table';
import { BaseApiHttpService } from 'app/course/learning-paths/services/base-api-http.service';
import { HttpParams } from '@angular/common/http';
import { FilterData } from 'app/exercises/programming/manage/grading/feedback-analysis/modal/feedback-filter-modal.component';
import { VcsAccessLogDTO } from 'app/entities/vcs-access-log-entry.model';

@Injectable({ providedIn: 'root' })
export class VcsRepositoryAccessLogService extends BaseApiHttpService {
    search(pageable: SearchTermPageableSearch, options: { participationId: number; filters: FilterData }): Promise<SearchResult<VcsAccessLogDTO>> {
        const params = new HttpParams()
            .set('page', pageable.page.toString())
            .set('pageSize', pageable.pageSize.toString())
            // .set('searchTerm', pageable.searchTerm || '')
            .set('sortingOrder', pageable.sortingOrder)
            .set('sortedColumn', pageable.sortedColumn);

        return this.get<SearchResult<VcsAccessLogDTO>>(`vcs-access-log/participation/${options.participationId}`, { params });
    }
}
