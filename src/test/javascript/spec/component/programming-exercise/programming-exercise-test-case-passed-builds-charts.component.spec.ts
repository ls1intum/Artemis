import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';

import { TestCasePassedBuildsChartComponent } from 'app/exercises/programming/manage/grading/charts/test-case-passed-builds-chart.component';
import { TestCaseStats } from 'app/entities/programming-exercise-test-case-statistics.model';

describe('TestCasePassedBuildsChartComponent', () => {
    let comp: TestCasePassedBuildsChartComponent;
    let fixture: ComponentFixture<TestCasePassedBuildsChartComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [NgbModule],
            declarations: [TestCasePassedBuildsChartComponent],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TestCasePassedBuildsChartComponent);
                comp = fixture.componentInstance as TestCasePassedBuildsChartComponent;
            });
    });

    const initComponent = (testCaseStats: TestCaseStats | undefined, totalParticipations: number | undefined) => {
        comp.testCaseStats = testCaseStats;
        comp.totalParticipations = totalParticipations;

        comp.ngOnChanges();
        tick();
    };

    it('should show a tooltip with "0% passed, 100% not executed" when receiving no stats', fakeAsync(() => {
        initComponent(undefined, 5);

        expect(comp.tooltip).toBe('0% passed, 0% failed, 100% not executed of 5 students.');
    }));

    it('should not update the tooltip when receiving an invalid amount of participants', fakeAsync(() => {
        initComponent({ numPassed: -1, numFailed: 0 }, -1);

        expect(comp.tooltip).toBe('');
    }));

    it('should show a tooltip with "40% passed, 60% failed" when receiving corresponding stats', fakeAsync(() => {
        initComponent({ numPassed: 2, numFailed: 3 }, 5);

        expect(comp.tooltip).toBe('40% passed, 60% failed of 5 students.');
    }));

    it('should show a tooltip with "100% passed, 0% failed" when receiving corresponding stats', fakeAsync(() => {
        initComponent({ numPassed: 5, numFailed: 0 }, 5);

        expect(comp.tooltip).toBe('100% passed, 0% failed of 5 students.');
    }));
});
