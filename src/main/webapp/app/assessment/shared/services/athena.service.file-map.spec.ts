import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { AthenaService } from 'app/assessment/shared/services/athena.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { lastValueFrom } from 'rxjs';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { ModelingFeedbackSuggestion, ProgrammingFeedbackSuggestion, TextFeedbackSuggestion } from 'app/assessment/shared/entities/feedback-suggestion.model';
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
        feedbackSuggestionModule: 'module-A',
    } as Exercise;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [AthenaService, provideHttpClient(), provideHttpClientTesting(), { provide: ProfileService, useClass: MockProfileService }],
        });

        service = TestBed.inject(AthenaService);
        httpMock = TestBed.inject(HttpTestingController);
        profileService = TestBed.inject(ProfileService);
        jest.spyOn(profileService, 'isProfileActive').mockReturnValue(true);
    });

    afterEach(() => {
        httpMock.verify();
        jest.restoreAllMocks();
    });

    it('should rehydrate grading instructions for text suggestions', async () => {
        const exercise = Object.assign({}, exerciseBase, { type: 'text' }) as Exercise;
        const submission = { id: 5, text: 'Hello World' } as TextSubmission;
        const suggestion = new TextFeedbackSuggestion(1, exercise.id!, submission.id!, 'Title', 'Description', 2, 99, undefined, undefined);
        const suggestionsPromise = lastValueFrom(service.getTextFeedbackSuggestions(exercise, submission));
        httpMock.expectOne('api/athena/text-exercises/10/submissions/5/feedback-suggestions').flush([suggestion]);
        const suggestions = await suggestionsPromise;
        const feedback = suggestions[0] as Feedback;

        expect(feedback).toBeDefined();
        expect(feedback.type).toBe(FeedbackType.MANUAL_UNREFERENCED);
        expect(feedback.gradingInstruction?.id).toBe(99);
    });

    it('should use lineStart fallback when programming suggestion lacks an end line', async () => {
        const exercise = Object.assign({}, exerciseBase, { type: 'programming' }) as Exercise;
        const programmingSuggestion = new ProgrammingFeedbackSuggestion(2, exercise.id!, 9, 'Issue', 'Fix it', 0.5, 99, 'src/Main.java', 4, undefined);
        const suggestionsPromise = lastValueFrom(service.getProgrammingFeedbackSuggestions(exercise, 9));
        httpMock.expectOne('api/athena/programming-exercises/10/submissions/9/feedback-suggestions').flush([programmingSuggestion]);
        const [feedback] = await suggestionsPromise;

        expect(feedback.reference).toBe('file:src/Main.java_line:4');
        expect(feedback.type).toBe(FeedbackType.MANUAL);
        expect(feedback.gradingInstruction?.instructionDescription).toBe('Important');
    });

    it('should mark programming suggestions without location as unreferenced', async () => {
        const exercise = Object.assign({}, exerciseBase, { type: 'programming' }) as Exercise;
        const suggestion = new ProgrammingFeedbackSuggestion(3, exercise.id!, 9, 'Hint', 'Think about edge cases', 1, undefined, '', undefined, undefined);
        const suggestionsPromise = lastValueFrom(service.getProgrammingFeedbackSuggestions(exercise, 9));
        httpMock.expectOne('api/athena/programming-exercises/10/submissions/9/feedback-suggestions').flush([suggestion]);
        const [feedback] = await suggestionsPromise;

        expect(feedback.type).toBe(FeedbackType.MANUAL_UNREFERENCED);
        expect(feedback.reference).toBeUndefined();
    });

    it('should split modeling references into type and id and expose positivity', async () => {
        const exercise = Object.assign({}, exerciseBase, { type: 'modeling' }) as Exercise;
        const modelingSuggestion = new ModelingFeedbackSuggestion(4, exercise.id!, 11, 'Model', 'Looks good', 2, 99, 'element:123');
        const submission = { id: 11 } as ModelingSubmission;
        const suggestionsPromise = lastValueFrom(service.getModelingFeedbackSuggestions(exercise, submission));
        httpMock.expectOne('api/athena/modeling-exercises/10/submissions/11/feedback-suggestions').flush([modelingSuggestion]);
        const [feedback] = await suggestionsPromise;

        expect(feedback.type).toBe(FeedbackType.AUTOMATIC);
        expect(feedback.reference).toBe('element:123');
        expect(feedback.referenceType).toBe('element');
        expect(feedback.referenceId).toBe('123');
        expect(feedback.positive).toBeTrue();
        expect(feedback.gradingInstruction?.id).toBe(99);
    });

    it('should fallback to suggestion metadata when modeling reference is missing', async () => {
        const exercise = Object.assign({}, exerciseBase, { type: 'modeling' }) as Exercise;
        const modelingSuggestion = new ModelingFeedbackSuggestion(5, exercise.id!, 11, 'Model', 'Needs work', 0, undefined, undefined);
        const submission = { id: 11 } as ModelingSubmission;
        const suggestionsPromise = lastValueFrom(service.getModelingFeedbackSuggestions(exercise, submission));
        httpMock.expectOne('api/athena/modeling-exercises/10/submissions/11/feedback-suggestions').flush([modelingSuggestion]);
        const [feedback] = await suggestionsPromise;

        expect(feedback.type).toBe(FeedbackType.MANUAL_UNREFERENCED);
        expect(feedback.text).toBe('FeedbackSuggestion:Model');
        expect(feedback.detailText).toBe('Needs work');
        expect(feedback.reference).toBeUndefined();
    });
});
