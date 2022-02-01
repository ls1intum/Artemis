import { ArtemisTestModule } from '../../test.module';
import { AssessmentFiltersComponent } from 'app/assessment/assessment-filters/assessment-filters.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockDirective, MockPipe } from 'ng-mocks';
import { NgModel } from '@angular/forms';
import { TextSubmission } from 'app/entities/text-submission.model';
import { SubmissionExerciseType } from 'app/entities/submission.model';
import { Result } from 'app/entities/result.model';

describe('AssessmentFiltersComponent', () => {
    let component: AssessmentFiltersComponent;
    let fixture: ComponentFixture<AssessmentFiltersComponent>;
    let textSubmission1: TextSubmission;
    let textSubmission2: TextSubmission;

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
                textSubmission1 = { id: 23, participation: { exercise: { id: 1 }, id: 2 }, submissionExerciseType: SubmissionExerciseType.TEXT } as TextSubmission;
                textSubmission2 = { id: 24, participation: { exercise: { id: 1 }, id: 2 }, submissionExerciseType: SubmissionExerciseType.TEXT } as TextSubmission;
            });
    });
    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set filter properly when being called', () => {
        component.submissions = [textSubmission1, textSubmission2];
        const submissions = [textSubmission1, textSubmission2];
        component.submissions = submissions;
        component.updateFilter(component.FilterProp.ALL);
        expect(component.filterProp).toBe(component.FilterProp.ALL);
        expect(component.submissions).toBe(submissions);
    });

    it('emitting the changes should not change the underlying submission list', () => {
        const submissions = [textSubmission1, textSubmission2];
        textSubmission1.results = [];
        textSubmission2.results = [];
        textSubmission1.results = [new Result()];
        component.submissions = submissions;
        const emitSpy = jest.spyOn(component.filterChange, 'emit');
        component.updateFilter(component.FilterProp.LOCKED);
        expect(component.filterProp).toBe(component.FilterProp.LOCKED);
        expect(component.submissions).toBe(submissions);
        expect(emitSpy).toHaveBeenCalledTimes(1);
    });
});
