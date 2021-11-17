import { ArtemisTestModule } from '../../../test.module';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of } from 'rxjs';
import { ExampleSubmissionImportComponent } from 'app/exercises/shared/example-submission/example-submission-import/example-submission-import.component';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { NgbPagination, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { NgModel } from '@angular/forms';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { SortService } from 'app/shared/service/sort.service';
import { ExampleSubmissionImportPagingService } from 'app/exercises/shared/example-submission/example-submission-import/example-submission-import-paging.service';
import { PageableSearch, SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import { Submission } from 'app/entities/submission.model';
import { TableColumn } from 'app/exercises/shared/example-submission/example-submission-import/example-submission-import.component';
import { TextSubmission } from 'app/entities/text-submission.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { ExampleSubmissionService } from 'app/exercises/shared/example-submission/example-submission.service';

describe('ExampleSubmissionImportComponent', () => {
    let component: ExampleSubmissionImportComponent;
    let fixture: ComponentFixture<ExampleSubmissionImportComponent>;
    let pagingService: ExampleSubmissionImportPagingService;
    let sortService: SortService;
    let searchForSubmissionsSpy: jest.SpyInstance;
    let sortByPropertySpy: jest.SpyInstance;
    let searchResult: SearchResult<Submission>;
    let state: PageableSearch;
    let submission: Submission;
    let exercise: Exercise;
    let exampleSubmissionService: ExampleSubmissionService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                ExampleSubmissionImportComponent,
                MockComponent(ResultComponent),
                MockDirective(NgbPagination),
                MockDirective(SortByDirective),
                MockDirective(SortDirective),
                MockDirective(ButtonComponent),
                MockDirective(NgModel),
                MockPipe(ArtemisDatePipe),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(NgbTooltip),
            ],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExampleSubmissionImportComponent);
                component = fixture.componentInstance;
                pagingService = TestBed.inject(ExampleSubmissionImportPagingService);
                sortService = TestBed.inject(SortService);
                exampleSubmissionService = TestBed.inject(ExampleSubmissionService);
                searchForSubmissionsSpy = jest.spyOn(pagingService, 'searchForSubmissions');
                sortByPropertySpy = jest.spyOn(sortService, 'sortByProperty');
            });
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    beforeEach(() => {
        fixture.detectChanges();
        submission = {
            id: 1,
            submitted: true,
            type: 'AUTOMATIC',
            text: 'Test\n\nTest\n\nTest',
        } as unknown as TextSubmission;
        exercise = {
            id: 1,
            problemStatement: 'problem statement',
            title: 'title',
            shortName: 'titleShort',
            type: ExerciseType.TEXT,
        } as Exercise;
        searchResult = { numberOfPages: 3, resultsOnPage: [submission] };
        state = {
            page: 0,
            pageSize: 10,
            searchTerm: 'initialSearchTerm',
            sortingOrder: SortingOrder.DESCENDING,
            sortedColumn: TableColumn.ID,
            ...searchResult,
        };
        searchForSubmissionsSpy.mockReturnValue(of(searchResult));
    });

    const setStateAndCallOnInit = (middleExpectation: () => void) => {
        component.state = { ...state };
        component.exercise = exercise;
        component.ngOnInit();
        middleExpectation();
        expect(component.content).toBe(searchResult);
        component.sortRows();
        expect(sortByPropertySpy).toHaveBeenCalledWith(searchResult.resultsOnPage, component.sortedColumn, component.listSorting);
    };

    it('should set content to paging result on sort', fakeAsync(() => {
        expect(component.listSorting).toBe(false);
        setStateAndCallOnInit(() => {
            component.listSorting = true;
            tick(10);
            expect(searchForSubmissionsSpy).toHaveBeenCalledWith({ ...state, sortingOrder: SortingOrder.ASCENDING }, exercise.id);
            expect(component.listSorting).toBe(true);
        });
    }));

    it('should set content to paging result on pageChange', fakeAsync(() => {
        expect(component.page).toBe(0);
        setStateAndCallOnInit(() => {
            component.onPageChange(2);
            tick(10);
            expect(searchForSubmissionsSpy).toHaveBeenCalledWith({ ...state, page: 2 }, exercise.id);
            expect(component.page).toBe(2);
        });
    }));

    it('should set content to paging result on search', fakeAsync(() => {
        expect(component.searchTerm).toBe('');
        setStateAndCallOnInit(() => {
            const givenSearchTerm = 'givenTestSearchTerm';
            component.searchTerm = givenSearchTerm;
            tick(10);
            expect(searchForSubmissionsSpy).not.toHaveBeenCalled();
            tick(290);
            expect(searchForSubmissionsSpy).toHaveBeenCalledWith({ ...state, searchTerm: givenSearchTerm }, exercise.id);
            expect(component.searchTerm).toBe(givenSearchTerm);
        });
    }));

    it('should set content to paging result on sortedColumn change', fakeAsync(() => {
        expect(component.sortedColumn).toBe(TableColumn.ID);
        setStateAndCallOnInit(() => {
            component.sortedColumn = TableColumn.STUDENT_NAME;
            tick(10);
            expect(searchForSubmissionsSpy).toHaveBeenCalledWith({ ...state, sortedColumn: TableColumn.STUDENT_NAME }, exercise.id);
            expect(component.sortedColumn).toBe(TableColumn.STUDENT_NAME);
        });
    }));

    it('should get the submission size', () => {
        const modelingExercise = {
            id: 1,
            type: ExerciseType.MODELING,
        } as Exercise;
        const elements = [{ id: 1 }, { id: 2 }, { id: 3 }];
        const relationships = [{ id: 1 }];
        const model = JSON.stringify({ elements, relationships });
        const modelingSubmission = {
            id: 1,
            model,
        } as ModelingSubmission;
        const textSubmission = {
            id: 2,
            text: 'test text',
        } as TextSubmission;
        let submissionSize;

        const getSubmissionSizeSpy = jest.spyOn(exampleSubmissionService, 'getSubmissionSize');

        component.exercise = exercise;
        submissionSize = component.getSubmissionSize(textSubmission);
        expect(submissionSize).toBe(2);

        component.exercise = modelingExercise;
        submissionSize = component.getSubmissionSize(modelingSubmission);
        expect(submissionSize).toBe(4);

        submissionSize = component.getSubmissionSize();
        expect(submissionSize).toBe(0);

        expect(getSubmissionSizeSpy).toHaveBeenCalledTimes(3);
    });
});
