import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { of, Subject } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SortService } from 'app/shared/service/sort.service';
import { NgbActiveModal, NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { FormsModule } from '@angular/forms';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { SearchResult } from 'app/shared/table/pageable-table';
import { QuizExerciseImportComponent } from 'app/exercises/quiz/manage/quiz-exercise-import.component';
import { QuizExercisePagingService } from 'app/exercises/quiz/manage/quiz-exercise-paging.service';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';

describe('QuizExercise Import Component', () => {
    let comp: QuizExerciseImportComponent;
    let fixture: ComponentFixture<QuizExerciseImportComponent>;
    let sortService: SortService;
    let pagingService: QuizExercisePagingService;
    let activeModal: NgbActiveModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(FormsModule)],
            declarations: [QuizExerciseImportComponent, MockComponent(NgbPagination), MockPipe(ArtemisTranslatePipe), MockDirective(SortByDirective), MockDirective(SortDirective)],
            providers: [MockProvider(SortService), MockProvider(QuizExercisePagingService), MockProvider(NgbActiveModal)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(QuizExerciseImportComponent);
                comp = fixture.componentInstance;
                sortService = fixture.debugElement.injector.get(SortService);
                pagingService = fixture.debugElement.injector.get(QuizExercisePagingService);
                activeModal = TestBed.inject(NgbActiveModal);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize the subjects', () => {
        // GIVEN
        const searchSpy = jest.spyOn(comp, 'performSearch' as any);

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
        expect(comp.content).toEqual({ resultsOnPage: [], numberOfPages: 0 });
    });

    it('should close the active modal', () => {
        // GIVEN
        const activeModalSpy = jest.spyOn(activeModal, 'dismiss');

        // WHEN
        comp.clear();

        // THEN
        expect(activeModalSpy).toHaveBeenCalledTimes(1);
        expect(activeModalSpy).toHaveBeenCalledWith('cancel');
    });

    it('should close the active modal with result', () => {
        // GIVEN
        const activeModalSpy = jest.spyOn(activeModal, 'close');
        const exercise = { id: 1 } as QuizExercise;
        // WHEN
        comp.openImport(exercise);

        // THEN
        expect(activeModalSpy).toHaveBeenCalledTimes(1);
        expect(activeModalSpy).toHaveBeenCalledWith(exercise);
    });

    it('should change the page on active modal', fakeAsync(() => {
        const defaultPageSize = 10;
        const numberOfPages = 5;
        const pagingServiceSpy = jest.spyOn(pagingService, 'searchForExercises');
        pagingServiceSpy.mockReturnValue(of({ numberOfPages } as SearchResult<QuizExercise>));

        fixture.detectChanges();

        let expectedPageNumber = 1;
        comp.onPageChange(expectedPageNumber);
        tick();
        expect(comp.page).toBe(expectedPageNumber);
        expect(comp.total).toBe(numberOfPages * defaultPageSize);

        expectedPageNumber = 2;
        comp.onPageChange(expectedPageNumber);
        tick();
        expect(comp.page).toBe(expectedPageNumber);
        expect(comp.total).toBe(numberOfPages * defaultPageSize);

        // Page number should be changed unless it is falsy.
        comp.onPageChange(0);
        tick();
        expect(comp.page).toBe(expectedPageNumber);

        // Number of times onPageChange is called with a truthy value.
        expect(pagingServiceSpy).toHaveBeenCalledTimes(2);
    }));

    it('should sort rows with default values', () => {
        const sortServiceSpy = jest.spyOn(sortService, 'sortByProperty');

        fixture.detectChanges();
        comp.sortRows();

        expect(sortServiceSpy).toHaveBeenCalledTimes(1);
        expect(sortServiceSpy).toHaveBeenCalledWith([], comp.column.ID, false);
    });

    it('should set search term and search', fakeAsync(() => {
        const pagingServiceSpy = jest.spyOn(pagingService, 'searchForExercises');
        pagingServiceSpy.mockReturnValue(of({ numberOfPages: 3 } as SearchResult<QuizExercise>));

        fixture.detectChanges();

        const expectedSearchTerm = 'search term';
        comp.searchTerm = expectedSearchTerm;
        tick();
        expect(comp.searchTerm).toBe(expectedSearchTerm);

        // It should wait first before executing search.
        expect(pagingServiceSpy).toHaveBeenCalledTimes(0);

        tick(300);

        expect(pagingServiceSpy).toHaveBeenCalledTimes(1);
    }));
});
