import { ArtemisTestModule } from '../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ScaCategoryDistributionChartComponent } from 'app/exercises/programming/manage/grading/charts/sca-category-distribution-chart.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockModule, MockPipe } from 'ng-mocks';
import { BarChartModule } from '@swimlane/ngx-charts';
import { TranslateService } from '@ngx-translate/core';
import { StaticCodeAnalysisCategory, StaticCodeAnalysisCategoryState } from 'app/entities/static-code-analysis-category.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { CategoryIssuesMap } from 'app/entities/programming-exercise-test-case-statistics.model';

describe('SCA category distribution chart', () => {
    let component: ScaCategoryDistributionChartComponent;
    let fixture: ComponentFixture<ScaCategoryDistributionChartComponent>;

    const category1 = {
        id: 1,
        name: 'category1',
        state: StaticCodeAnalysisCategoryState.Inactive,
        penalty: 1,
        maxPenalty: 1,
    } as StaticCodeAnalysisCategory;

    const category2 = {
        id: 2,
        name: 'category2',
        state: StaticCodeAnalysisCategoryState.Feedback,
        penalty: 2,
        maxPenalty: 2,
    } as StaticCodeAnalysisCategory;

    const category3 = {
        id: 3,
        name: 'category3',
        state: StaticCodeAnalysisCategoryState.Graded,
        penalty: 3,
        maxPenalty: 3,
    } as StaticCodeAnalysisCategory;

    const category4 = {
        id: 4,
        name: 'category4',
        state: StaticCodeAnalysisCategoryState.Graded,
        penalty: 3,
        maxPenalty: 3,
    } as StaticCodeAnalysisCategory;

    const programmingExercerise = new ProgrammingExercise(undefined, undefined);
    programmingExercerise.maxStaticCodeAnalysisPenalty = 9;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(BarChartModule)],
            declarations: [ScaCategoryDistributionChartComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ScaCategoryDistributionChartComponent);
        component = fixture.componentInstance;
    });

    it('should process the categories correctly', () => {
        component.exercise = programmingExercerise;
        component.categories = [category1, category2, category3, category4];

        component.categoryIssuesMap = {
            category1: {
                '0': 0,
                '1': 1,
            },
            category4: {
                '0': 0,
                '1': 4,
            },
        } as CategoryIssuesMap;

        component.ngOnChanges();

        const categoryNames = ['category1', 'category2', 'category3', 'category4'];
        const penalties = [0, 0, 50, 50];
        expect(component.ngxData[0].name).toBe('artemisApp.programmingAssessment.penalty');
        penalties.forEach((penalty, index) => {
            expect(component.ngxData[0].series[index].name).toBe(categoryNames[index]);
            expect(component.ngxData[0].series[index].value).toBe(penalty);
        });

        const issues = [20, 0, 0, 80];
        expect(component.ngxData[1].name).toBe('artemisApp.programmingAssessment.issues');
        issues.forEach((penalty, index) => {
            expect(component.ngxData[1].series[index].name).toBe(categoryNames[index]);
            expect(component.ngxData[1].series[index].value).toBe(penalty);
        });

        const deductions = [0, 0, 0, 100];
        expect(component.ngxData[2].name).toBe('artemisApp.programmingAssessment.deductions');
        deductions.forEach((penalty, index) => {
            expect(component.ngxData[2].series[index].name).toBe(categoryNames[index]);
            expect(component.ngxData[2].series[index].value).toBe(penalty);
        });
    });

    it('should handle negative penalties', () => {
        const negativeCategory = {
            id: 5,
            name: 'negative category',
            state: StaticCodeAnalysisCategoryState.Graded,
            penalty: -5,
            maxPenalty: 1,
        } as StaticCodeAnalysisCategory;

        component.exercise = programmingExercerise;
        component.categories = [negativeCategory];

        component.ngOnChanges();

        expect(component.ngxData[0].series[0].name).toBe('negative category');
        expect(component.ngxData[0].series[0].value).toBe(0);

        expect(component.ngxData[1].series[0].name).toBe('negative category');
        expect(component.ngxData[1].series[0].value).toBe(0);

        expect(component.ngxData[2].series[0].name).toBe('negative category');
        expect(component.ngxData[2].series[0].value).toBe(0);
    });
});
