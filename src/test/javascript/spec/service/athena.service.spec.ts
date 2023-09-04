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

describe('AthenaService', () => {
    let athenaService: AthenaService;
    let profileService: ProfileService;
    let httpTestingController: HttpTestingController;
    const textExercise = {
        id: 1,
        type: 'text',
        feedbackSuggestionsEnabled: true,
    } as Exercise;
    const programmingExercise = {
        id: 2,
        type: 'programming',
        feedbackSuggestionsEnabled: true,
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
        const textFeedbackSuggestions: TextFeedbackSuggestion[] = [{ title: 'Test Text', indexStart: 5 } as TextFeedbackSuggestion];
        const programmingFeedbackSuggestions: ProgrammingFeedbackSuggestion[] = [{ title: 'Test Programming', lineStart: 4 } as ProgrammingFeedbackSuggestion];
        let textResponse: TextFeedbackSuggestion[] | null = null;
        let programmingResponse: ProgrammingFeedbackSuggestion[] | null = null;

        const mockProfileInfo = { activeProfiles: ['athena'] } as ProfileInfo;
        jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(of(mockProfileInfo));

        athenaService.getTextFeedbackSuggestions(textExercise, 2).subscribe((suggestions: TextFeedbackSuggestion[]) => {
            textResponse = suggestions;
        });
        const requestWrapperText = httpTestingController.expectOne({ url: 'api/athena/text-exercises/1/submissions/2/feedback-suggestions' });
        requestWrapperText.flush(textFeedbackSuggestions);

        tick();

        athenaService.getProgrammingFeedbackSuggestions(programmingExercise, 2).subscribe((suggestions: ProgrammingFeedbackSuggestion[]) => {
            programmingResponse = suggestions;
        });
        const requestWrapperProgramming = httpTestingController.expectOne({ url: 'api/athena/programming-exercises/2/submissions/2/feedback-suggestions' });
        requestWrapperProgramming.flush(programmingFeedbackSuggestions);

        tick();

        expect(requestWrapperText.request.method).toBe('GET');
        expect(textResponse).toEqual(textFeedbackSuggestions);
        expect(requestWrapperProgramming.request.method).toBe('GET');
        expect(programmingResponse).toEqual(programmingFeedbackSuggestions);
    }));

    it('should return no feedback suggestions when feedback suggestions are disabled on the exercise', fakeAsync(() => {
        let response: TextFeedbackSuggestion[] | null = null;

        const mockProfileInfo = { activeProfiles: ['athena'] } as ProfileInfo;
        jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(of(mockProfileInfo));

        const exerciseWithoutFeedbackSuggestions = { ...textExercise, feedbackSuggestionsEnabled: false } as Exercise;

        athenaService.getTextFeedbackSuggestions(exerciseWithoutFeedbackSuggestions, 2).subscribe((suggestions: TextFeedbackSuggestion[]) => {
            response = suggestions;
        });

        tick();

        expect(response).toEqual([]);
    }));

    it('should return no feedback suggestions when athena is disabled on the server', fakeAsync(() => {
        let response: TextFeedbackSuggestion[] | null = null;

        const mockProfileInfo = { activeProfiles: ['something'] } as ProfileInfo;
        jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(of(mockProfileInfo));

        athenaService.getTextFeedbackSuggestions(textExercise, 2).subscribe((suggestions: TextFeedbackSuggestion[]) => {
            response = suggestions;
        });

        tick();

        expect(response).toEqual([]);
    }));
});
