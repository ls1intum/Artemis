import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { AthenaService } from 'app/assessment/shared/services/athena.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { lastValueFrom } from 'rxjs';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { ModelingFeedback } from 'app/openapi/model/modeling-feedback';
import { ProgrammingFeedback } from 'app/openapi/model/programming-feedback';
import { TextFeedback } from 'app/openapi/model/text-feedback';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

describe('AthenaService file map behaviour', () => {
    let service: AthenaService;
    let httpMock: HttpTestingController;
    let profileService: ProfileService;

    const gradingCriteria = [
        {
            id: 1,
            title: 'Criterion',
            structuredGradingInstructions: [
                {
                    id: 99,
                    credits: 1,
                    usageCount: 0,
                    instructionDescription: 'Important',
                },
            ],
        },
    ];

    const exerciseBase = {
        id: 10,
        gradingCriteria,
    } as Exercise;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [AthenaService, provideHttpClient(), provideHttpClientTesting(), { provide: ProfileService, useClass: MockProfileService }],
        });

        service = TestBed.inject(AthenaService);
        httpMock = TestBed.inject(HttpTestingController);
        profileService = TestBed.inject(ProfileService);
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    it('should rehydrate grading instructions for text suggestions', async () => {
        const exercise = { ...exerciseBase, type: 'text' } as Exercise;
        const submission = { id: 5, text: 'Hello World' } as TextSubmission;
        const suggestion = {
            id: 1,
            exerciseId: exercise.id!,
            submissionId: submission.id!,
            title: 'Title',
            description: 'Description',
            credits: 2,
            structuredGradingInstructionId: 99,
        } satisfies TextFeedback;
        const suggestionsPromise = lastValueFrom(service.getTextFeedbackSuggestions(exercise, submission));
        httpMock.expectOne('/api/athena/text-exercises/10/submissions/5/feedback-suggestions').flush([suggestion]);
        const suggestions = await suggestionsPromise;
        const feedback = suggestions[0] as Feedback;

        expect(feedback).toBeDefined();
        expect(feedback.type).toBe(FeedbackType.MANUAL_UNREFERENCED);
        expect(feedback.gradingInstruction?.id).toBe(99);
    });

    it('should use lineStart fallback when programming suggestion lacks an end line', async () => {
        const exercise = { ...exerciseBase, type: 'programming' } as Exercise;
        const programmingSuggestion = {
            id: 2,
            exerciseId: exercise.id!,
            submissionId: 9,
            title: 'Issue',
            description: 'Fix it',
            credits: 0.5,
            structuredGradingInstructionId: 99,
            filePath: 'src/Main.java',
            lineStart: 4,
        } satisfies ProgrammingFeedback;
        const suggestionsPromise = lastValueFrom(service.getProgrammingFeedbackSuggestions(exercise, 9));
        httpMock.expectOne('/api/athena/programming-exercises/10/submissions/9/feedback-suggestions').flush([programmingSuggestion]);
        const [feedback] = await suggestionsPromise;

        expect(feedback.reference).toBe('file:src/Main.java_line:4');
        expect(feedback.type).toBe(FeedbackType.MANUAL);
        expect(feedback.gradingInstruction?.instructionDescription).toBe('Important');
    });

    it('should mark programming suggestions without location as unreferenced', async () => {
        const exercise = { ...exerciseBase, type: 'programming' } as Exercise;
        const suggestion = {
            id: 3,
            exerciseId: exercise.id!,
            submissionId: 9,
            title: 'Hint',
            description: 'Think about edge cases',
            credits: 1,
            filePath: '',
        } satisfies ProgrammingFeedback;
        const suggestionsPromise = lastValueFrom(service.getProgrammingFeedbackSuggestions(exercise, 9));
        httpMock.expectOne('/api/athena/programming-exercises/10/submissions/9/feedback-suggestions').flush([suggestion]);
        const [feedback] = await suggestionsPromise;

        expect(feedback.type).toBe(FeedbackType.MANUAL_UNREFERENCED);
        expect(feedback.reference).toBeUndefined();
    });

    it('should split modeling references into type and id and expose positivity', async () => {
        const exercise = { ...exerciseBase, type: 'modeling' } as Exercise;
        const modelingSuggestion = {
            id: 4,
            exerciseId: exercise.id!,
            submissionId: 11,
            title: 'Model',
            description: 'Looks good',
            credits: 2,
            structuredGradingInstructionId: 99,
            reference: 'element:123',
        } satisfies ModelingFeedback;
        const submission = { id: 11 } as ModelingSubmission;
        const suggestionsPromise = lastValueFrom(service.getModelingFeedbackSuggestions(exercise, submission));
        httpMock.expectOne('/api/athena/modeling-exercises/10/submissions/11/feedback-suggestions').flush([modelingSuggestion]);
        const [feedback] = await suggestionsPromise;

        expect(feedback.type).toBe(FeedbackType.AUTOMATIC);
        expect(feedback.reference).toBe('element:123');
        expect(feedback.referenceType).toBe('element');
        expect(feedback.referenceId).toBe('123');
        expect(feedback.positive).toBe(true);
        expect(feedback.gradingInstruction?.id).toBe(99);
    });

    it('should fallback to suggestion metadata when modeling reference is missing', async () => {
        const exercise = { ...exerciseBase, type: 'modeling' } as Exercise;
        const modelingSuggestion = { id: 5, exerciseId: exercise.id!, submissionId: 11, title: 'Model', description: 'Needs work', credits: 0 } satisfies ModelingFeedback;
        const submission = { id: 11 } as ModelingSubmission;
        const suggestionsPromise = lastValueFrom(service.getModelingFeedbackSuggestions(exercise, submission));
        httpMock.expectOne('/api/athena/modeling-exercises/10/submissions/11/feedback-suggestions').flush([modelingSuggestion]);
        const [feedback] = await suggestionsPromise;

        expect(feedback.type).toBe(FeedbackType.MANUAL_UNREFERENCED);
        expect(feedback.text).toBe('FeedbackSuggestion:accepted:Model');
        expect(feedback.detailText).toBe('Needs work');
        expect(feedback.reference).toBeUndefined();
    });
});
