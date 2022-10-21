import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { Result } from 'app/entities/result.model';
import { Submission } from 'app/entities/submission.model';
import { AssessmentProgressLabelComponent } from 'app/exercises/shared/assessment-progress-label/assessment-progress-label.component';
import dayjs from 'dayjs/esm';
import { ArtemisTestModule } from '../../test.module';

describe('Assessment progress label test', () => {
    let comp: AssessmentProgressLabelComponent;
    let fixture: ComponentFixture<AssessmentProgressLabelComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [AssessmentProgressLabelComponent],
            providers: [],
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
