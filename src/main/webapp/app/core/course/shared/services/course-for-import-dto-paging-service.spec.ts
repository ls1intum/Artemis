import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { CourseForImportDTO } from 'app/core/course/shared/entities/course.model';
import { SortingOrder } from 'app/shared/table/pageable-table';
import { provideHttpClient } from '@angular/common/http';
import { CourseForImportDTOPagingService } from 'app/core/course/shared/services/course-for-import-dto-paging-service';

describe('CourseForImportDtoPagingService', () => {
    setupTestBed({ zoneless: true });

    let pagingService: CourseForImportDTOPagingService;
    let httpTestingController: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: AccountService, useClass: MockAccountService }],
        });

        pagingService = TestBed.inject(CourseForImportDTOPagingService);
        httpTestingController = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpTestingController.verify();
        vi.restoreAllMocks();
    });

    it('should get a paginated list of courses', async () => {
        const returnedFromService = { resultsOnPage: [new CourseForImportDTO()], numberOfPages: 1 };
        const pageable = { pageSize: 1, page: 1, sortingOrder: SortingOrder.ASCENDING, searchTerm: 'search', sortedColumn: 'ID' };
        const promise = firstValueFrom(pagingService.search(pageable));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);

        const actualResult = await promise;
        expect(actualResult).toEqual(returnedFromService);
    });
});
