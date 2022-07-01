import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { of, Subject } from 'rxjs';
import { ArtemisTestModule } from '../../../test.module';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SortService } from 'app/shared/service/sort.service';
import { NgbActiveModal, NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { FormsModule } from '@angular/forms';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { SearchResult } from 'app/shared/table/pageable-table';
import { ExamImportComponent } from 'app/exam/manage/exams/exam-import/exam-import.component';
import { ExamImportPagingService } from 'app/exam/manage/exams/exam-import/exam-import-paging.service';
import { Exam } from 'app/entities/exam.model';

describe('Exam Import Component', () => {
    let component: ExamImportComponent;
    let fixture: ComponentFixture<ExamImportComponent>;
    let sortService: SortService;
    let pagingService: ExamImportPagingService;
    let activeModal: NgbActiveModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(FormsModule)],
            declarations: [ExamImportComponent, MockComponent(NgbPagination), MockPipe(ArtemisTranslatePipe), MockDirective(SortByDirective), MockDirective(SortDirective)],
            providers: [MockProvider(SortService), MockProvider(ExamImportPagingService), MockProvider(NgbActiveModal)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamImportComponent);
                component = fixture.componentInstance;
                sortService = fixture.debugElement.injector.get(SortService);
                pagingService = fixture.debugElement.injector.get(ExamImportPagingService);
                activeModal = TestBed.inject(NgbActiveModal);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize the subjects', () => {
        // GIVEN
        const searchSpy = jest.spyOn(component, 'performSearch' as any);

        // WHEN
        fixture.detectChanges();

        // THEN
        expect(searchSpy).toHaveBeenCalledTimes(2);
        expect(searchSpy).toHaveBeenCalledWith(expect.any(Subject), 0);
        expect(searchSpy).toHaveBeenCalledWith(expect.any(Subject), 300);
    });

    it('should initialize the content', () => {
        // WHEN
        fixture.detectChanges();

        // THEN
        expect(component.content).toEqual({ resultsOnPage: [], numberOfPages: 0 });
    });

    it('should close the active modal', () => {
        // GIVEN
        const activeModalSpy = jest.spyOn(activeModal, 'dismiss');

        // WHEN
        component.clear();

        // THEN
        expect(activeModalSpy).toHaveBeenCalledOnce();
        expect(activeModalSpy).toHaveBeenCalledWith('cancel');
    });

    it('should close the active modal with result', () => {
        // GIVEN
        const activeModalSpy = jest.spyOn(activeModal, 'close');
        const exam = { id: 1 } as Exam;
        // WHEN
        component.forwardSelectedExam(exam);

        // THEN
        expect(activeModalSpy).toHaveBeenCalledOnce();
        expect(activeModalSpy).toHaveBeenCalledWith(exam);
    });

    it('should change the page on active modal', fakeAsync(() => {
        const defaultPageSize = 10;
        const numberOfPages = 5;
        const pagingServiceSpy = jest.spyOn(pagingService, 'searchForExams');
        pagingServiceSpy.mockReturnValue(of({ numberOfPages } as SearchResult<Exam>));

        fixture.detectChanges();

        let expectedPageNumber = 1;
        component.onPageChange(expectedPageNumber);
        tick();
        expect(component.page).toBe(expectedPageNumber);
        expect(component.total).toBe(numberOfPages * defaultPageSize);

        expectedPageNumber = 2;
        component.onPageChange(expectedPageNumber);
        tick();
        expect(component.page).toBe(expectedPageNumber);
        expect(component.total).toBe(numberOfPages * defaultPageSize);

        // Page number should be changed unless it is falsy.
        component.onPageChange(0);
        tick();
        expect(component.page).toBe(expectedPageNumber);

        // Number of times onPageChange is called with a truthy value.
        expect(pagingServiceSpy).toHaveBeenCalledTimes(2);
    }));

    it('should sort rows with default values', () => {
        const sortServiceSpy = jest.spyOn(sortService, 'sortByProperty');

        fixture.detectChanges();
        component.sortRows();

        expect(sortServiceSpy).toHaveBeenCalledOnce();
        expect(sortServiceSpy).toHaveBeenCalledWith([], component.column.ID, false);
    });

    it('should set search term and search', fakeAsync(() => {
        const pagingServiceSpy = jest.spyOn(pagingService, 'searchForExams');
        pagingServiceSpy.mockReturnValue(of({ numberOfPages: 3 } as SearchResult<Exam>));

        fixture.detectChanges();

        const expectedSearchTerm = 'search term';
        component.searchTerm = expectedSearchTerm;
        tick();
        expect(component.searchTerm).toBe(expectedSearchTerm);

        // It should wait first before executing search.
        expect(pagingServiceSpy).toHaveBeenCalledTimes(0);

        tick(300);

        expect(pagingServiceSpy).toHaveBeenCalledOnce();
    }));

    it('should track the id correctly', () => {
        const exam = { id: 1 } as Exam;
        expect(component.trackId(5, exam)).toBe(exam.id);
    });
});
