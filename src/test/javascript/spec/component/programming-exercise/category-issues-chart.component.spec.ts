import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { CategoryIssuesChartComponent } from 'app/exercises/programming/manage/grading/charts/category-issues-chart.component';
import { StaticCodeAnalysisCategory, StaticCodeAnalysisCategoryState } from 'app/entities/static-code-analysis-category.model';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MockDirective } from 'ng-mocks';

describe('CategoryIssuesChartComponent', () => {
    let fixture: ComponentFixture<CategoryIssuesChartComponent>;
    let comp: CategoryIssuesChartComponent;
    beforeEach(async () => {
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

    it('should display the right tooltip', fakeAsync(() => {
        const staticCodeAnalysisCategory = new StaticCodeAnalysisCategory();
        staticCodeAnalysisCategory.penalty = 2;
        staticCodeAnalysisCategory.maxPenalty = 4;
        staticCodeAnalysisCategory.state = StaticCodeAnalysisCategoryState.Graded;
        let issuesMap = {
            '1': 2,
            '2': 3,
            '3': 4,
        };

        comp.category = staticCodeAnalysisCategory;
        comp.maxNumberOfIssues = 6;
        comp.issuesMap = issuesMap;

        comp.ngOnChanges();
        tick();

        expect(comp.columns).toBeDefined();
        expect(comp.columns).toHaveLength(11);

        expect(comp.columns[0].tooltip).toBe('2 students have 1 issue.');
        expect(comp.columns[1].tooltip).toBe('3 students have 2 issues.');
        expect(comp.columns[2].tooltip).toBe('4 students have 3 issues.');
        expect(comp.columns[0].color).toBe('#ffc107');

        issuesMap = {
            '1': 2,
            '2': 3,
            '3': 6,
        };
        comp.issuesMap = issuesMap;
        comp.ngOnChanges();
        tick();

        expect(comp.columns[2].tooltip).toBe('6 students have 3 issues.');
        expect(comp.columns[2].color).toBe('#dc3545');

        staticCodeAnalysisCategory.state = StaticCodeAnalysisCategoryState.Feedback;

        comp.ngOnChanges();
        tick();

        expect(comp.columns[1].color).toBe('#28a745');
    }));
});
