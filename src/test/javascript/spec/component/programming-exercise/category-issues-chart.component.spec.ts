import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { StaticCodeAnalysisCategory, StaticCodeAnalysisCategoryState } from 'app/entities/static-code-analysis-category.model';
import { CategoryIssuesChartComponent } from 'app/exercises/programming/manage/grading/charts/category-issues-chart.component';
import { MockDirective } from 'ng-mocks';

describe('CategoryIssuesChartComponent', () => {
    let fixture: ComponentFixture<CategoryIssuesChartComponent>;
    let comp: CategoryIssuesChartComponent;
    let category: StaticCodeAnalysisCategory;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [CategoryIssuesChartComponent, MockDirective(NgbTooltip)],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CategoryIssuesChartComponent);
                comp = fixture.componentInstance;
            });
    });

    const initComponent = (categories: StaticCodeAnalysisCategory, maxGradedIssues: number, totalStudents: number, maxNumberOfIssues: number) => {
        comp.issuesMap = configureIssuesMap();
        comp.category = category;
        comp.maxGradedIssues = maxGradedIssues;
        comp.totalStudents = totalStudents;
        comp.maxNumberOfIssues = maxNumberOfIssues;

        comp.ngOnChanges();
        tick();
    };

    const configureCategory = () => {
        category = {
            id: 0,
            name: '',
            state: StaticCodeAnalysisCategoryState.Graded,
            penalty: 2,
            maxPenalty: 4,
        };
        return category;
    };
    const configureIssuesMap = () => {
        const issuesMap = {
            '1': 2,
            '2': 3,
            '3': 4,
        };
        return issuesMap;
    };

    it('should display the right tooltip content', fakeAsync(() => {
        const staticCodeAnalysisCategory = configureCategory();
        initComponent(staticCodeAnalysisCategory, 0, 0, 6);

        expect(comp.columns).toBeDefined();
        expect(comp.columns).toHaveLength(11);

        expect(comp.columns[0].tooltip).toBe('2 students have 1 issue.');
        expect(comp.columns[1].tooltip).toBe('3 students have 2 issues.');
        expect(comp.columns[2].tooltip).toBe('4 students have 3 issues.');
        expect(comp.columns[3].tooltip).toBe('0 students have 4 issues.');
    }));

    it('should display the right tooltip color for feedback', fakeAsync(() => {
        category = configureCategory();
        category.state = StaticCodeAnalysisCategoryState.Feedback;
        initComponent(category, 0, 0, 5);

        expect(comp.columns[0].color).toBe('#28a745');
        expect(comp.columns[1].color).toBe('#28a745');
        expect(comp.columns[2].color).toBe('#28a745');
        expect(comp.columns[3].color).toBe('#28a745');
    }));

    it('should display the right tooltip color for inactive', fakeAsync(() => {
        category = configureCategory();
        category.state = StaticCodeAnalysisCategoryState.Inactive;

        initComponent(category, 0, 0, 5);

        expect(comp.columns[0].color).toBe('#ddd');
        expect(comp.columns[1].color).toBe('#ddd');
        expect(comp.columns[2].color).toBe('#ddd');
        expect(comp.columns[3].color).toBe('#ddd');
    }));

    it('should display the right tooltip color for more issues than max graded', fakeAsync(() => {
        category = configureCategory();

        initComponent(category, 1, 2, 5);

        expect(comp.columns[0].color).toBe('#ffc107');
        expect(comp.columns[1].color).toBe('#ffc107');
        expect(comp.columns[2].color).toBe('#dc3545');
        expect(comp.columns[3].color).toBe('#28a745');
    }));

    it('should have the right tooltip width', fakeAsync(() => {
        category = configureCategory();

        initComponent(category, 0, 0, 11);

        expect(comp.columns[0].w).toBe('6.5%');
        expect(comp.columns[1].w).toBe('6.5%');
        expect(comp.columns[2].w).toBe('6.5%');
        expect(comp.columns[3].w).toBe('6.5%');
    }));

    it('should have the right tooltip height', fakeAsync(() => {
        category = configureCategory();

        initComponent(category, 0, 10, 5);

        expect(comp.columns[0].h).toBe('23%');
        expect(comp.columns[1].h).toBe('32.5%');
        expect(comp.columns[2].h).toBe('42%');
        expect(comp.columns[3].h).toBe('4%');
    }));
});
