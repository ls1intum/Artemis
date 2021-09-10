import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { NgbHighlight, NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { ModelingExerciseImportComponent, TableColumn } from 'app/exercises/modeling/manage/modeling-exercise-import.component';
import { ModelingExercisePagingService } from 'app/exercises/modeling/manage/modeling-exercise-paging.service';
import { ButtonComponent } from 'app/shared/components/button.component';
import { ExerciseCourseTitlePipe } from 'app/shared/pipes/exercise-course-title.pipe';
import { SortService } from 'app/shared/service/sort.service';
import { PageableSearch, SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import * as chai from 'chai';
import { MockDirective, MockPipe } from 'ng-mocks';
import { of } from 'rxjs';
import * as sinon from 'sinon';
import sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';

chai.use(sinonChai);
const expect = chai.expect;

describe('ModelingExerciseImportComponent', () => {
    let fixture: ComponentFixture<ModelingExerciseImportComponent>;
    let comp: ModelingExerciseImportComponent;
    let pagingService: ModelingExercisePagingService;
    let sortService: SortService;
    let searchForExercisesStub: sinon.SinonStub;
    let sortByPropertyStub: sinon.SinonStub;
    let searchResult: SearchResult<ModelingExercise>;
    let state: PageableSearch;
    let modelingExercise: ModelingExercise;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule],
            declarations: [
                ModelingExerciseImportComponent,
                MockPipe(ExerciseCourseTitlePipe),
                MockDirective(NgbHighlight),
                MockDirective(NgbPagination),
                MockDirective(SortByDirective),
                MockDirective(SortDirective),
                MockDirective(ButtonComponent),
            ],
            providers: [],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ModelingExerciseImportComponent);
                comp = fixture.componentInstance;
                pagingService = TestBed.inject(ModelingExercisePagingService);
                sortService = TestBed.inject(SortService);
                searchForExercisesStub = sinon.stub(pagingService, 'searchForExercises');
                sortByPropertyStub = sinon.stub(sortService, 'sortByProperty');
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(ModelingExerciseImportComponent).to.be.ok;
    });

    beforeEach(() => {
        fixture.detectChanges();
        modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
        modelingExercise.id = 5;
        searchResult = { numberOfPages: 3, resultsOnPage: [modelingExercise] };
        state = {
            page: 0,
            pageSize: 10,
            searchTerm: 'initialSearchTerm',
            sortingOrder: SortingOrder.DESCENDING,
            sortedColumn: TableColumn.ID,
            ...searchResult,
        };
        searchForExercisesStub.returns(of(searchResult));
    });

    const setStateAndCallOnInit = (middleExpectation: () => void) => {
        comp.state = { ...state };
        comp.ngOnInit();
        middleExpectation();
        expect(comp.content).to.deep.equal(searchResult);
        comp.sortRows();
        expect(sortByPropertyStub).to.have.been.calledWithExactly(searchResult.resultsOnPage, comp.sortedColumn, comp.listSorting);
    };

    it('should set content to paging result on sort', fakeAsync(() => {
        expect(comp.listSorting).to.equal(false);
        setStateAndCallOnInit(() => {
            comp.listSorting = true;
            tick(10);
            expect(searchForExercisesStub).to.have.been.calledWithExactly({ ...state, sortingOrder: SortingOrder.ASCENDING });
            expect(comp.listSorting).to.equal(true);
        });
    }));

    it('should set content to paging result on pageChange', fakeAsync(() => {
        expect(comp.page).to.equal(0);
        setStateAndCallOnInit(() => {
            comp.onPageChange(5);
            tick(10);
            expect(searchForExercisesStub).to.have.been.calledWithExactly({ ...state, page: 5 });
            expect(comp.page).to.equal(5);
        });
    }));

    it('should set content to paging result on search', fakeAsync(() => {
        expect(comp.searchTerm).to.equal('');
        setStateAndCallOnInit(() => {
            const givenSearchTerm = 'givenSearchTerm';
            comp.searchTerm = givenSearchTerm;
            tick(10);
            expect(searchForExercisesStub).to.not.have.been.called;
            tick(290);
            expect(searchForExercisesStub).to.have.been.calledWithExactly({ ...state, searchTerm: givenSearchTerm });
            expect(comp.searchTerm).to.equal(givenSearchTerm);
        });
    }));

    it('should set content to paging result on sortedColumn change', fakeAsync(() => {
        expect(comp.sortedColumn).to.equal(TableColumn.ID);
        setStateAndCallOnInit(() => {
            comp.sortedColumn = TableColumn.TITLE;
            tick(10);
            expect(searchForExercisesStub).to.have.been.calledWithExactly({ ...state, sortedColumn: TableColumn.TITLE });
            expect(comp.sortedColumn).to.equal(TableColumn.TITLE);
        });
    }));

    it('should return modeling exercise id', () => {
        expect(comp.trackId(0, modelingExercise)).to.equal(modelingExercise.id);
    });
});
