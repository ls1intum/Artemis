import { expect } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import dayjs from 'dayjs/esm';
import { AssessmentProgressLabelComponent } from 'app/exercise/assessment-progress-label/assessment-progress-label.component';

describe('Assessment progress label test', () => {
    setupTestBed({ zoneless: true });
    let comp: AssessmentProgressLabelComponent;
    let fixture: ComponentFixture<AssessmentProgressLabelComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [AssessmentProgressLabelComponent],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AssessmentProgressLabelComponent);
                comp = fixture.componentInstance;
            });
    });
    it('should show no submission when the array is empty', () => {
        comp.submissions = [];
        comp.ngOnChanges();

        expect(comp.numberAssessedSubmissions).toBe(0);
    });

    it('should ignore no assessed submissions', () => {
        const result = { id: 44, rated: false } as Result;
        const submission = { id: 77, results: [result] } as Submission;

        comp.submissions = [submission];
        comp.ngOnChanges();

        expect(comp.numberAssessedSubmissions).toBe(0);
    });

    it('should ignore automatic assessment submissions', () => {
        const result = { id: 44, rated: true, assessmentType: AssessmentType.AUTOMATIC } as Result;
        const submission = { id: 77, results: [result] } as Submission;

        comp.submissions = [submission];
        comp.ngOnChanges();

        expect(comp.numberAssessedSubmissions).toBe(0);
    });

    it('should count the manual submissions', () => {
        const result = { id: 44, rated: true, assessmentType: AssessmentType.MANUAL, completionDate: dayjs() } as Result;
        const submission = { id: 77, results: [result] } as Submission;

        comp.submissions = [submission];
        comp.ngOnChanges();

        expect(comp.numberAssessedSubmissions).toBe(1);
    });
});
