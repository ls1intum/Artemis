import { ArtemisTestModule } from '../../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { CompetencyTableComponent } from 'app/course/competencies/import-competencies/competency-table.component';
import { SortingOrder } from 'app/shared/table/pageable-table';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbPagination } from '@ng-bootstrap/ng-bootstrap';

describe('CompetencyTableComponent', () => {
    let componentFixture: ComponentFixture<CompetencyTableComponent>;
    let component: CompetencyTableComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ReactiveFormsModule],
            declarations: [CompetencyTableComponent, MockPipe(ArtemisTranslatePipe), MockDirective(SortDirective), MockDirective(TranslateDirective), MockComponent(NgbPagination)],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(CompetencyTableComponent);
                component = componentFixture.componentInstance;
                component.content = { resultsOnPage: [], numberOfPages: 1 };
                component.search = {
                    sortedColumn: 'ID',
                    sortingOrder: SortingOrder.DESCENDING,
                    pageSize: 10,
                    page: 1,
                };
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        componentFixture.detectChanges();
        expect(component).toBeDefined();
    });
});
