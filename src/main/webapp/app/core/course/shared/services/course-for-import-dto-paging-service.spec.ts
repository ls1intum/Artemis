import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { take } from 'rxjs/operators';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { CourseForImportDTO } from 'app/core/course/shared/entities/course.model';
import { SortingOrder } from 'app/shared/table/pageable-table';
import { provideHttpClient } from '@angular/common/http';
import { CourseForImportDTOPagingService } from 'app/core/course/shared/services/course-for-import-dto-paging-service';

describe('CourseForImportDtoPagingService', () => {
    let pagingService: CourseForImportDTOPagingService;
    let httpTestingController: HttpTestingController;
    let actualResult: any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: AccountService, useClass: MockAccountService }],
        });

        pagingService = TestBed.inject(CourseForImportDTOPagingService);
        httpTestingController = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpTestingController.verify();
    });

    it('should get a paginated list of courses', fakeAsync(() => {
        const returnedFromService = { resultsOnPage: [new CourseForImportDTO()], numberOfPages: 1 };
        const pageable = { pageSize: 1, page: 1, sortingOrder: SortingOrder.ASCENDING, searchTerm: 'search', sortedColumn: 'ID' };
        pagingService
            .search(pageable)
            .pipe(take(1))
            .subscribe((resp) => (actualResult = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();

        expect(actualResult).toEqual(returnedFromService);
    }));
});
