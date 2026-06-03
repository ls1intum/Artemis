import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { Subject, of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

import { ExampleSubmissionService } from 'app/assessment/shared/services/example-submission.service';
import { ExampleSubmissionImportComponent } from 'app/exercise/example-submission/example-submission-import/example-submission-import.component';
import { ExampleSubmissionImportPagingService } from 'app/exercise/example-submission/example-submission-import/example-submission-import-paging.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Submission, SubmissionType } from 'app/exercise/shared/entities/submission/submission.model';
import { SearchResult } from 'app/foundation/pagination/pageable-table';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ExampleSubmissionImportComponent', () => {
    setupTestBed({ zoneless: true });

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
                { provide: Router, useValue: { navigate: vi.fn() } },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideTemplate(ExampleSubmissionImportComponent, '')
            .compileComponents();

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

        expect(searchSpy).toHaveBeenCalledExactlyOnceWith(component.state, { exerciseId: 3 });
    });

    it('should set the submission size when retrieving search results', async () => {
        vi.useFakeTimers();

        component.searchTerm = 'search';
        await vi.advanceTimersByTimeAsync(300);

        expect(getSubmissionSizeSpy).toHaveBeenCalledExactlyOnceWith(submission, exercise);
        expect(component.content.resultsOnPage[0].submissionSize).toBe(2);
    });

    it('should close the dialog on dismiss', () => {
        component.dismiss();

        expect(dialogRef.close).toHaveBeenCalledOnce();
    });
});
