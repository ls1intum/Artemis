import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Subject, of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ExampleSubmissionService } from 'app/assessment/shared/services/example-submission.service';
import { ExampleSubmissionImportComponent } from 'app/exercise/example-submission/example-submission-import/example-submission-import.component';
import { ExampleSubmissionImportPagingService } from 'app/exercise/example-submission/example-submission-import/example-submission-import-paging.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Submission, SubmissionType } from 'app/exercise/shared/entities/submission/submission.model';
import { SearchResult } from 'app/foundation/pagination/pageable-table';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { SortByDirective } from 'app/foundation/sort/directive/sort-by.directive';
import { ResultComponent } from 'app/exercise/result/result.component';
import dayjs from 'dayjs/esm';

describe('ExampleSubmissionImportComponent', () => {
    let component: ExampleSubmissionImportComponent;
    let fixture: ComponentFixture<ExampleSubmissionImportComponent>;
    let pagingService: ExampleSubmissionImportPagingService;
    let searchSpy: ReturnType<typeof vi.spyOn>;
    let submission: Submission;
    let exampleSubmissionService: ExampleSubmissionService;
    let getSubmissionSizeSpy: ReturnType<typeof vi.spyOn>;
    let exercise: Exercise;
    let dialogRef: DynamicDialogRef;

    beforeEach(() => {
        dialogRef = {
            close: vi.fn(),
            onClose: new Subject<Submission | undefined>(),
        } as unknown as DynamicDialogRef;

        TestBed.configureTestingModule({
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DynamicDialogRef, useValue: dialogRef },
                { provide: DialogService, useValue: { open: vi.fn() } },
                { provide: Router, useValue: { navigate: vi.fn() } },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExampleSubmissionImportComponent);
        component = fixture.componentInstance;
        pagingService = TestBed.inject(ExampleSubmissionImportPagingService);
        exampleSubmissionService = TestBed.inject(ExampleSubmissionService);
        searchSpy = vi.spyOn(pagingService, 'search');
        getSubmissionSizeSpy = vi.spyOn(exampleSubmissionService, 'getSubmissionSize').mockReturnValue(2);

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
        const searchResult: SearchResult<Submission> = { numberOfPages: 3, resultsOnPage: [submission] };
        searchSpy.mockReturnValue(of(searchResult));

        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
        vi.clearAllTimers();
        vi.useRealTimers();
        if (dialogRef?.onClose && typeof (dialogRef.onClose as Subject<Submission | undefined>).complete === 'function') {
            (dialogRef.onClose as Subject<Submission | undefined>).complete();
        }
    });

    it('should pass the exercise id to the paging service', async () => {
        vi.useFakeTimers();

        component.searchTerm = 'search';
        await vi.advanceTimersByTimeAsync(300);

        // The dialog also loads once on init (see the shared ImportComponent regression test), so the paging
        // service is called for both; assert the search-triggered call passes the exercise id.
        expect(searchSpy).toHaveBeenCalledWith(component.state, { exerciseId: 3 });
    });

    it('should set the submission size when retrieving search results', async () => {
        vi.useFakeTimers();

        component.searchTerm = 'search';
        await vi.advanceTimersByTimeAsync(300);

        expect(getSubmissionSizeSpy).toHaveBeenCalledWith(submission, exercise);
        expect(component.content().resultsOnPage[0].submissionSize).toBe(2);
    });

    it('should close the dialog on dismiss', () => {
        component.dismiss();

        expect(dialogRef.close).toHaveBeenCalledOnce();
    });

    it('only makes the student name header sortable', () => {
        component.content.set({ numberOfPages: 1, resultsOnPage: [submission] });
        fixture.detectChanges();

        const sortableHeaders = fixture.debugElement.queryAll(By.directive(SortByDirective));
        expect(sortableHeaders).toHaveLength(1);
        expect(sortableHeaders[0].injector.get(SortByDirective).jhiSortBy()).toBe('STUDENT_NAME');
    });

    // The import table renders <jhi-result [result]="getLatestResult(submission)" ...>. This component is the only
    // caller that resolves the displayed result itself (instead of passing one in), so guard that resolution: the
    // latest rated result of the submission's participation must be returned, and undefined when there is none.
    it('getLatestResult returns the latest rated result of the submission participation', () => {
        const ratedResult = { id: 7, score: 90, rated: true } as Result;
        const innerSubmission = { id: 5, results: [ratedResult] } as Submission;
        const participation = { id: 2, type: ParticipationType.STUDENT, submissions: [innerSubmission] } as StudentParticipation;
        const submissionWithResult = { id: 5, participation } as Submission;

        expect(component.getLatestResult(submissionWithResult)).toEqual(ratedResult);
    });

    it('links every listed result to its submission so the result badge can route to it', async () => {
        vi.useFakeTimers();
        const ratedResult = { id: 7, score: 90, rated: true } as Result;
        const sibling = { id: 5, results: [ratedResult] } as Submission;
        const listed = { id: 5, participation: { id: 2, type: ParticipationType.STUDENT, submissions: [sibling] } as StudentParticipation } as Submission;
        searchSpy.mockReturnValue(of({ numberOfPages: 1, resultsOnPage: [listed] }));

        component.searchTerm = 'search';
        await vi.advanceTimersByTimeAsync(300);

        expect(component.getLatestResult(component.content().resultsOnPage[0])?.submission?.id).toBe(5);
    });

    it('getLatestResult returns undefined when the submission has no participation', () => {
        expect(component.getLatestResult({ id: 9 } as Submission)).toBeUndefined();
    });

    it('does not offer the listed result as a link into the student exercise page', async () => {
        // #13921: this modal is instructor-facing and lists other students' participations. For a text or modeling
        // exercise the badge would otherwise deep-link into the student exercise page, which 403s for an exam
        // exercise and substitutes the instructor's own participation in a course.
        vi.useFakeTimers();
        const ratedResult = { id: 7, score: 90, rated: true, completionDate: dayjs().subtract(1, 'hour') } as Result;
        const sibling = { id: 5, results: [ratedResult] } as Submission;
        const listed = { id: 5, participation: { id: 2, type: ParticipationType.STUDENT, submissions: [sibling] } as StudentParticipation } as Submission;
        searchSpy.mockReturnValue(of({ numberOfPages: 1, resultsOnPage: [listed] }));

        component.searchTerm = 'search';
        await vi.advanceTimersByTimeAsync(300);
        fixture.detectChanges();

        const result = fixture.debugElement.query(By.directive(ResultComponent)).componentInstance as ResultComponent;
        expect(result.isOwnParticipation()).toBe(false);
        expect(result.canShowDetails()).toBe(false);
    });
});
