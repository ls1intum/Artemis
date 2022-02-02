import { ArtemisTestModule } from '../../test.module';
import { AssessmentFiltersComponent } from 'app/assessment/assessment-filters/assessment-filters.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockDirective, MockPipe } from 'ng-mocks';
import { NgModel } from '@angular/forms';
import { TextSubmission } from 'app/entities/text-submission.model';
import { Submission, SubmissionExerciseType } from 'app/entities/submission.model';
import { Result } from 'app/entities/result.model';
import dayjs from 'dayjs/esm';

describe('AssessmentFiltersComponent', () => {
    let component: AssessmentFiltersComponent;
    let fixture: ComponentFixture<AssessmentFiltersComponent>;
    let textSubmissionWithLock: TextSubmission;
    let textSubmissionWithoutResult: TextSubmission;
    let textSubmissionWithResult: TextSubmission;
    let submissions: Submission[];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [AssessmentFiltersComponent, MockPipe(ArtemisTranslatePipe), MockDirective(NgModel)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AssessmentFiltersComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();
                textSubmissionWithLock = { id: 23, participation: { exercise: { id: 1 }, id: 2 }, submissionExerciseType: SubmissionExerciseType.TEXT } as TextSubmission;
                textSubmissionWithoutResult = { id: 24, participation: { exercise: { id: 1 }, id: 3 }, submissionExerciseType: SubmissionExerciseType.TEXT } as TextSubmission;
                textSubmissionWithResult = { id: 25, participation: { exercise: { id: 1 }, id: 4 }, submissionExerciseType: SubmissionExerciseType.TEXT } as TextSubmission;
                submissions = [textSubmissionWithLock, textSubmissionWithoutResult, textSubmissionWithResult];
            });
    });
    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set filter properly when being called', () => {
        component.submissions = submissions;
        const emitSpy = jest.spyOn(component.filterChange, 'emit');
        component.updateFilter(component.FilterProp.ALL);
        expect(component.filterProp).toBe(component.FilterProp.ALL);
        expect(component.submissions).toBe(submissions);
        expect(emitSpy).toHaveBeenCalledTimes(1);
        expect(emitSpy).toHaveBeenCalledWith(submissions);
    });

    it('should filter for the right submissions', () => {
        textSubmissionWithLock.results = [];
        textSubmissionWithoutResult.results = [];
        textSubmissionWithResult.results = [];
        textSubmissionWithLock.results = [new Result()];
        const result3 = new Result();
        result3.completionDate = dayjs().add(5, 'minutes');
        textSubmissionWithResult.results.push(result3);
        component.submissions = submissions;
        const expectedResult = [textSubmissionWithLock];
        const emitSpy = jest.spyOn(component.filterChange, 'emit');
        component.updateFilter(component.FilterProp.LOCKED);
        expect(component.filterProp).toBe(component.FilterProp.LOCKED);
        expect(component.submissions).toBe(submissions);
        expect(emitSpy).toHaveBeenCalledTimes(1);
        expect(emitSpy).toHaveBeenCalledWith(expectedResult);
    });
});
