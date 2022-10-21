import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgModel } from '@angular/forms';
import { AssessmentFiltersComponent } from 'app/assessment/assessment-filters/assessment-filters.component';
import { Result } from 'app/entities/result.model';
import { Submission } from 'app/entities/submission.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';
import { MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTestModule } from '../../test.module';

describe('AssessmentFiltersComponent', () => {
    let component: AssessmentFiltersComponent;
    let fixture: ComponentFixture<AssessmentFiltersComponent>;

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
            });
    });
    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set filter properly when being called', () => {
        submissions = [];
        component.submissions = submissions;
        const emitSpy = jest.spyOn(component.filterChange, 'emit');
        component.updateFilter(component.FilterProp.LOCKED);
        expect(component.filterProp).toBe(component.FilterProp.LOCKED);
        component.updateFilter(component.FilterProp.ALL);
        expect(component.filterProp).toBe(component.FilterProp.ALL);
        expect(component.submissions).toBe(submissions);
        expect(emitSpy).toHaveBeenCalledTimes(2);
        expect(emitSpy).toHaveBeenCalledWith(submissions);
    });

    it('should filter for the right submissions', () => {
        const submissionWithLock = { results: [new Result()] } as Submission;
        const resultWithCompletionDate = new Result();
        resultWithCompletionDate.completionDate = dayjs();
        const unlockedSubmissionWithResult = { results: [resultWithCompletionDate] } as Submission;
        const submissionWithoutResult = {} as Submission;
        submissions = [unlockedSubmissionWithResult, submissionWithLock, submissionWithoutResult];
        component.submissions = submissions;
        const expectedResult = [submissionWithLock];
        const emitSpy = jest.spyOn(component.filterChange, 'emit');
        component.updateFilter(component.FilterProp.LOCKED);
        expect(component.filterProp).toBe(component.FilterProp.LOCKED);
        expect(component.submissions).toBe(submissions);
        expect(emitSpy).toHaveBeenCalledOnce();
        expect(emitSpy).toHaveBeenCalledWith(expectedResult);
    });
});
