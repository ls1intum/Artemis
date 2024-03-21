import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NgModel } from '@angular/forms';
import { NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Submission, SubmissionType } from 'app/entities/submission.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { ExampleSubmissionImportPagingService } from 'app/exercises/shared/example-submission/example-submission-import/example-submission-import-paging.service';
import { ExampleSubmissionImportComponent } from 'app/exercises/shared/example-submission/example-submission-import/example-submission-import.component';
import { ExampleSubmissionService } from 'app/exercises/shared/example-submission/example-submission.service';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { SearchResult } from 'app/shared/table/pageable-table';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { of } from 'rxjs';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../test.module';

describe('ExampleSubmissionImportComponent', () => {
    let component: ExampleSubmissionImportComponent;
    let fixture: ComponentFixture<ExampleSubmissionImportComponent>;
    let pagingService: ExampleSubmissionImportPagingService;
    let searchSpy: jest.SpyInstance;
    let searchResult: SearchResult<Submission>;
    let submission: Submission;
    let exampleSubmissionService: ExampleSubmissionService;
    let getSubmissionSizeSpy: jest.SpyInstance;
    let exercise: Exercise;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockComponent(NgbPagination)],
            declarations: [
                ExampleSubmissionImportComponent,
                MockComponent(ButtonComponent),
                MockComponent(ResultComponent),
                MockDirective(SortByDirective),
                MockDirective(SortDirective),
                MockDirective(NgModel),
                MockPipe(ArtemisDatePipe),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExampleSubmissionImportComponent);
                component = fixture.componentInstance;
                pagingService = TestBed.inject(ExampleSubmissionImportPagingService);
                exampleSubmissionService = TestBed.inject(ExampleSubmissionService);
                searchSpy = jest.spyOn(pagingService, 'search');
                getSubmissionSizeSpy = jest.spyOn(exampleSubmissionService, 'getSubmissionSize').mockReturnValue(2);
            });
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    beforeEach(() => {
        fixture.detectChanges();
        submission = {
            id: 1,
            submitted: true,
            type: SubmissionType.MANUAL,
            text: 'Test\n\nTest\n\nTest',
        } as TextSubmission;
        exercise = {
            id: 3,
            problemStatement: 'problem statement',
            title: 'title',
            shortName: 'titleShort',
            type: ExerciseType.TEXT,
        } as Exercise;
        component.exercise = exercise;
        searchResult = { numberOfPages: 3, resultsOnPage: [submission] };
        searchSpy.mockReturnValue(of(searchResult));
    });

    it('should pass the exercise id to the paging service', fakeAsync(() => {
        component.searchTerm = 'search';
        tick(300);
        expect(searchSpy).toHaveBeenCalledExactlyOnceWith(component.state, { exerciseId: 3 });
    }));

    it('should set the submission size when retrieving search results', fakeAsync(() => {
        component.searchTerm = 'search';
        tick(300);
        expect(getSubmissionSizeSpy).toHaveBeenCalledExactlyOnceWith(submission, exercise);
        expect(component.content.resultsOnPage[0].submissionSize).toBe(2);
    }));
});
