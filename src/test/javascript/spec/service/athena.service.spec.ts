import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { AthenaService } from 'app/assessment/athena.service';
import { ArtemisTestModule } from '../test.module';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { MockProfileService } from '../helpers/mocks/service/mock-profile.service';
import { of } from 'rxjs';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { Exercise } from 'app/entities/exercise.model';
import { ProgrammingFeedbackSuggestion, TextFeedbackSuggestion } from 'app/entities/feedback-suggestion.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { TextBlockRef } from 'app/entities/text-block-ref.model';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';

describe('AthenaService', () => {
    let athenaService: AthenaService;
    let profileService: ProfileService;
    let httpTestingController: HttpTestingController;
    const gradingCriteria = [
        {
            id: 1,
            title: 'Test Criteria',
            structuredGradingInstructions: [
                {
                    id: 4321,
                    credits: 1.0,
                    instructionDescription: 'Test Instruction',
                    usageCount: 0,
                },
            ],
        },
    ];
    const textExercise = {
        id: 1,
        type: 'text',
        feedbackSuggestionsEnabled: true,
        gradingCriteria,
    } as Exercise;
    const programmingExercise = {
        id: 2,
        type: 'programming',
        feedbackSuggestionsEnabled: true,
        gradingCriteria,
    } as Exercise;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
            providers: [{ provide: ProfileService, useClass: MockProfileService }],
        })
            .compileComponents()
            .then(() => {
                athenaService = TestBed.inject(AthenaService);
                httpTestingController = TestBed.inject(HttpTestingController);
                profileService = TestBed.inject(ProfileService);
            });
    });

    it('should get feedback suggestions when athena is enabled', fakeAsync(() => {
        const textFeedbackSuggestions = [new TextFeedbackSuggestion(0, 1, 2, 'Test Text', 'Test Text Description', 0.0, 4321, 5, 10)];
        const programmingFeedbackSuggestions: ProgrammingFeedbackSuggestion[] = [
            new ProgrammingFeedbackSuggestion(0, 2, 2, 'Test Programming', 'Test Programming Description', -1.0, 4321, 'src/Test.java', 4, undefined),
        ];
        let textResponse: TextBlockRef[] | null = null;
        let programmingResponse: Feedback[] | null = null;

        const mockProfileInfo = { activeProfiles: ['athena'] } as ProfileInfo;
        jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(of(mockProfileInfo));

        athenaService.getTextFeedbackSuggestions(textExercise, { id: 2, text: 'Hello world, this is a test' } as TextSubmission).subscribe((suggestions: TextBlockRef[]) => {
            textResponse = suggestions;
        });
        const requestWrapperText = httpTestingController.expectOne({ url: 'api/athena/text-exercises/1/submissions/2/feedback-suggestions' });
        requestWrapperText.flush(textFeedbackSuggestions);

        tick();

        athenaService.getProgrammingFeedbackSuggestions(programmingExercise, 2).subscribe((suggestions: Feedback[]) => {
            programmingResponse = suggestions;
        });
        const requestWrapperProgramming = httpTestingController.expectOne({ url: 'api/athena/programming-exercises/2/submissions/2/feedback-suggestions' });
        requestWrapperProgramming.flush(programmingFeedbackSuggestions);

        tick();

        expect(requestWrapperText.request.method).toBe('GET');
        expect(textResponse![0].feedback!.type).toEqual(FeedbackType.MANUAL);
        expect(textResponse![0].feedback!.text).toBe('FeedbackSuggestion:accepted:Test Text');
        expect(textResponse![0].feedback!.detailText).toBe('Test Text Description');
        expect(textResponse![0].block!.startIndex).toBe(5);
        expect(textResponse![0].block!.id).toEqual(textResponse![0].feedback!.reference);
        expect(requestWrapperProgramming.request.method).toBe('GET');
        expect(programmingResponse![0].type).toEqual(FeedbackType.MANUAL);
        expect(programmingResponse![0].text).toBe('FeedbackSuggestion:Test Programming');
        expect(programmingResponse![0].detailText).toBe('Test Programming Description');
        expect(programmingResponse![0].credits).toBe(-1.0);
        expect(programmingResponse![0].reference).toBe('file:src/Test.java_line:4');
    }));

    it('should return no feedback suggestions when feedback suggestions are disabled on the exercise', fakeAsync(() => {
        let response: TextBlockRef[] | null = null;

        const mockProfileInfo = { activeProfiles: ['athena'] } as ProfileInfo;
        jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(of(mockProfileInfo));

        const exerciseWithoutFeedbackSuggestions = { ...textExercise, feedbackSuggestionsEnabled: false } as Exercise;

        athenaService.getTextFeedbackSuggestions(exerciseWithoutFeedbackSuggestions, { id: 2, text: '' } as TextSubmission).subscribe((suggestions: TextBlockRef[]) => {
            response = suggestions;
        });

        tick();

        expect(response).toEqual([]);
    }));

    it('should return no feedback suggestions when athena is disabled on the server', fakeAsync(() => {
        let response: TextBlockRef[] | null = null;

        const mockProfileInfo = { activeProfiles: ['something'] } as ProfileInfo;
        jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(of(mockProfileInfo));

        athenaService.getTextFeedbackSuggestions(textExercise, { id: 2, text: '' } as TextSubmission).subscribe((suggestions: TextBlockRef[]) => {
            response = suggestions;
        });

        tick();

        expect(response).toEqual([]);
    }));
});
