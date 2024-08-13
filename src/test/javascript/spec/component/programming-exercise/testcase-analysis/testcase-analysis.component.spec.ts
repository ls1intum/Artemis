import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TestcaseAnalysisComponent } from 'app/exercises/programming/manage/grading/testcase-analysis/testcase-analysis.component';

describe('TestcaseAnalysisComponent', () => {
    let component: TestcaseAnalysisComponent;
    let fixture: ComponentFixture<TestcaseAnalysisComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TestcaseAnalysisComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(TestcaseAnalysisComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
