import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal, NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Submission, SubmissionType } from 'app/exercise/shared/entities/submission/submission.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { ExampleSubmissionImportPagingService } from 'app/exercise/example-submission/example-submission-import/example-submission-import-paging.service';
import { ExampleSubmissionImportComponent } from 'app/exercise/example-submission/example-submission-import/example-submission-import.component';
import { ExampleParticipationService } from 'app/assessment/shared/services/example-participation.service';
import { ResultComponent } from 'app/exercise/result/result.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { SearchResult } from 'app/shared/table/pageable-table';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { Subject, of } from 'rxjs';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

describe('ExampleSubmissionImportComponent', () => {
    let component: ExampleSubmissionImportComponent;
    let fixture: ComponentFixture<ExampleSubmissionImportComponent>;
    let pagingService: ExampleSubmissionImportPagingService;
    let searchSpy: jest.SpyInstance;
    let searchResult: SearchResult<Submission>;
    let submission: Submission;
    let exampleParticipationService: ExampleParticipationService;
    let getSubmissionSizeSpy: jest.SpyInstance;
    let exercise: Exercise;
    let dialogRef: DynamicDialogRef;

    beforeEach(() => {
        dialogRef = {
            close: jest.fn(),
            onClose: new Subject<any>(),
        } as unknown as DynamicDialogRef;

        TestBed.configureTestingModule({
            imports: [MockComponent(NgbPagination)],
            declarations: [
                ExampleSubmissionImportComponent,
                MockComponent(ButtonComponent),
                MockComponent(ResultComponent),
                MockDirective(SortByDirective),
                MockDirective(SortDirective),
                MockPipe(ArtemisDatePipe),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DynamicDialogRef, useValue: dialogRef },
                { provide: NgbActiveModal, useValue: { dismiss: jest.fn() } },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExampleSubmissionImportComponent);
                component = fixture.componentInstance;
                pagingService = TestBed.inject(ExampleSubmissionImportPagingService);
                exampleParticipationService = TestBed.inject(ExampleParticipationService);
                searchSpy = jest.spyOn(pagingService, 'search');
                getSubmissionSizeSpy = jest.spyOn(exampleParticipationService, 'getSubmissionSize').mockReturnValue(2);
            });
    });

    afterEach(() => {
        jest.clearAllMocks();
        if (dialogRef?.onClose && typeof (dialogRef.onClose as Subject<any>).complete === 'function') {
            (dialogRef.onClose as Subject<any>).complete();
        }
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
