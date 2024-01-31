import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { take } from 'rxjs/operators';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { CourseForImportDTOPagingService } from 'app/course/course-for-import-dto-paging-service';
import { CourseForImportDTO } from 'app/entities/course.model';
import { SortingOrder } from 'app/shared/table/pageable-table';

describe('CourseForImportDtoPagingService', () => {
    let pagingService: CourseForImportDTOPagingService;
    let httpTestingController: HttpTestingController;
    let actualResult: any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [{ provide: AccountService, useClass: MockAccountService }],
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
