import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { AthenaService } from 'app/assessment/athena.service';
import { ArtemisTestModule } from '../test.module';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { MockProfileService } from '../helpers/mocks/service/mock-profile.service';
import { of } from 'rxjs';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { Exercise } from 'app/entities/exercise.model';
import { ModelingFeedbackSuggestion, ProgrammingFeedbackSuggestion, TextFeedbackSuggestion } from 'app/entities/feedback-suggestion.model';
import { TextSubmission } from 'app/entities/text/text-submission.model';
import { TextBlockRef } from 'app/entities/text/text-block-ref.model';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { provideHttpClient } from '@angular/common/http';

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
        feedbackSuggestionModule: 'text_module',
        gradingCriteria,
    } as Exercise;
    const programmingExercise = {
        id: 2,
        type: 'programming',
        feedbackSuggestionModule: 'programming_module',
        gradingCriteria,
    } as Exercise;
    const modelingExercise = {
        id: 2,
        type: 'modeling',
        feedbackSuggestionModule: 'modeling_module',
        gradingCriteria,
    } as Exercise;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: ProfileService, useClass: MockProfileService }],
        })
            .compileComponents()
            .then(() => {
                athenaService = TestBed.inject(AthenaService);
                httpTestingController = TestBed.inject(HttpTestingController);
                profileService = TestBed.inject(ProfileService);
            });
    });

    const elementID = 'd3184916-e518-45ac-87ca-259ad61e2562';

    const model = {
        version: '3.0.0',
        type: 'BPMN',
        size: {
            width: 1740,
            height: 960,
        },
        interactive: {
            elements: {},
            relationships: {},
        },
        elements: {
            [elementID]: {
                id: elementID,
                name: 'Task',
                type: 'BPMNTask',
                owner: null,
                bounds: {
                    x: 290,
                    y: 580,
                    width: 180,
                    height: 60,
                },
                taskType: 'default',
                marker: 'none',
            },
        },
        relationships: {},
        assessments: {},
    };

    it('should return true if athena is enabled', fakeAsync(async () => {
        const mockProfileInfo = { activeProfiles: ['athena'] } as ProfileInfo;
        jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(of(mockProfileInfo));

        let isAthenaEnabled: boolean | null = null;

        athenaService.isEnabled().subscribe((isEnabled: boolean) => {
            isAthenaEnabled = isEnabled;
        });

        tick();

        expect(isAthenaEnabled).toBeTrue();
    }));

    it('should return false if athena is disabled', fakeAsync(() => {
        const mockProfileInfo = { activeProfiles: ['something'] } as ProfileInfo;
        jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(of(mockProfileInfo));

        let isAthenaEnabled: boolean | null = null;

        athenaService.isEnabled().subscribe((isEnabled: boolean) => {
            isAthenaEnabled = isEnabled;
        });

        tick();

        expect(isAthenaEnabled).toBeFalse();
    }));

    it('should get feedback suggestions when athena is enabled', fakeAsync(() => {
        const textFeedbackSuggestions = [new TextFeedbackSuggestion(0, 1, 2, 'Test Text', 'Test Text Description', 0.0, 4321, 5, 10)];
        const programmingFeedbackSuggestions: ProgrammingFeedbackSuggestion[] = [
            new ProgrammingFeedbackSuggestion(0, 2, 2, 'Test Programming', 'Test Programming Description', -1.0, 4321, 'src/Test.java', 4, undefined),
        ];
        const modelingFeedbackSuggestions: ModelingFeedbackSuggestion[] = [
            new ModelingFeedbackSuggestion(0, 2, 2, 'Test Modeling 1', 'Test Modeling Description 1', 0.0, 4321, [elementID]),
            new ModelingFeedbackSuggestion(0, 2, 2, 'Test Modeling 2', 'Test Modeling Description 2', 1.0, 4321, []),
        ];
        let textResponse: TextBlockRef[] | null = null;
        let programmingResponse: Feedback[] | null = null;
        let modelingResponse: Feedback[] | null = null;

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

        athenaService.getModelingFeedbackSuggestions(modelingExercise, { id: 2, model: JSON.stringify(model) } as ModelingSubmission).subscribe((suggestions: Feedback[]) => {
            modelingResponse = suggestions;
        });
        const requestWrapperModeling = httpTestingController.expectOne({ url: 'api/athena/modeling-exercises/2/submissions/2/feedback-suggestions' });
        requestWrapperModeling.flush(modelingFeedbackSuggestions);

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
        expect(requestWrapperModeling.request.method).toBe('GET');

        // Referenced feedback
        expect(modelingResponse![0].type).toEqual(FeedbackType.AUTOMATIC);
        expect(modelingResponse![0].text).toBe('Test Modeling Description 1');
        expect(modelingResponse![0].credits).toBe(0.0);
        expect(modelingResponse![0].reference).toBe(`BPMNTask:${elementID}`);

        // Unreferenced feedback
        expect(modelingResponse![1].type).toEqual(FeedbackType.MANUAL_UNREFERENCED);
        expect(modelingResponse![1].text).toBe('FeedbackSuggestion:Test Modeling 2');
        expect(modelingResponse![1].detailText).toBe('Test Modeling Description 2');
        expect(modelingResponse![1].credits).toBe(1.0);
        expect(modelingResponse![1].reference).toBeUndefined();
    }));

    it('should return no feedback suggestions when feedback suggestions are disabled on the exercise', fakeAsync(() => {
        let response: TextBlockRef[] | null = null;

        const mockProfileInfo = { activeProfiles: ['athena'] } as ProfileInfo;
        jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(of(mockProfileInfo));

        const exerciseWithoutFeedbackSuggestions = { ...textExercise, feedbackSuggestionModule: undefined } as Exercise;

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

    it('should return no modules when athena is disabled on the server', fakeAsync(() => {
        let response: string[] | null = null;

        const mockProfileInfo = { activeProfiles: ['something'] } as ProfileInfo;
        jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(of(mockProfileInfo));

        athenaService.getAvailableModules(1, textExercise).subscribe((modules: string[]) => {
            response = modules;
        });

        tick();

        expect(response).toEqual([]);
    }));

    it('should get available modules when athena is enabled', fakeAsync(() => {
        const textModules = ['module_text_1', 'module_text_2'];
        const programmingModules = ['module_programming_1', 'module_programming_2'];
        const modelingModules = ['module_modeling_1', 'module_modeling_2'];

        let textResponse: string[] | null = null;
        let programmingResponse: string[] | null = null;
        let modelingResponse: string[] | null = null;

        const mockProfileInfo = { activeProfiles: ['athena'] } as ProfileInfo;
        jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(of(mockProfileInfo));

        athenaService.getAvailableModules(1, textExercise).subscribe((modules: string[]) => {
            textResponse = modules;
        });
        const requestWrapperText = httpTestingController.expectOne({ url: 'api/athena/courses/1/text-exercises/available-modules' });
        requestWrapperText.flush(textModules);

        tick();

        athenaService.getAvailableModules(1, programmingExercise).subscribe((modules: string[]) => {
            programmingResponse = modules;
        });
        const requestWrapperProgramming = httpTestingController.expectOne({ url: 'api/athena/courses/1/programming-exercises/available-modules' });
        requestWrapperProgramming.flush(programmingModules);

        tick();

        athenaService.getAvailableModules(1, modelingExercise).subscribe((modules: string[]) => {
            modelingResponse = modules;
        });
        const requestWrapperModeling = httpTestingController.expectOne({ url: 'api/athena/courses/1/modeling-exercises/available-modules' });
        requestWrapperModeling.flush(modelingModules);

        tick();

        expect(requestWrapperText.request.method).toBe('GET');
        expect(textResponse!).toEqual(textModules);

        expect(requestWrapperProgramming.request.method).toBe('GET');
        expect(programmingResponse!).toEqual(programmingModules);

        expect(requestWrapperModeling.request.method).toBe('GET');
        expect(modelingResponse!).toEqual(modelingModules);
    }));
});
