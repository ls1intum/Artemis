import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { CUSTOM_ELEMENTS_SCHEMA, Component, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { of, throwError } from 'rxjs';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { CompetencySelectionComponent } from 'app/atlas/shared/competency-selection/competency-selection.component';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';

import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockProvider } from 'ng-mocks';
import { MODULE_FEATURE_ATLAS } from 'app/app.constants';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { Competency } from 'app/atlas/shared/entities/competency.model';

/**
 * Test component that simulates an exercise creation form
 * This mimics how the competency selection component is used in real exercise forms
 */
@Component({
    imports: [FormsModule, CompetencySelectionComponent],
    template: `
        <form #exerciseForm="ngForm">
            <div class="form-group">
                <label for="title">Exercise Title</label>
                <input type="text" id="title" name="title" class="form-control" [(ngModel)]="exercise.title" required />
            </div>

            <div class="form-group">
                <label for="description">Exercise Description</label>
                <textarea id="description" name="description" class="form-control" [(ngModel)]="exercise.description" rows="4" required></textarea>
            </div>

            <!-- This is where the competency selection component is integrated -->
            <jhi-competency-selection
                [exerciseDescription]="exercise.description"
                [labelName]="'Select Competencies'"
                [labelTooltip]="'Choose competencies that this exercise will assess'"
                [(ngModel)]="exercise.competencies"
                name="competencies"
            >
            </jhi-competency-selection>

            <div class="form-actions">
                <button type="submit" class="btn btn-primary" [disabled]="!exerciseForm.valid" (click)="saveExercise()">Save Exercise</button>
            </div>
        </form>
    `,
})
class TestExerciseFormComponent {
    competencySelection = viewChild.required<CompetencySelectionComponent>(CompetencySelectionComponent);

    exercise = {
        title: '',
        description: '',
        competencies: [],
    };

    saveExercise(): void {
        // Mock save functionality
    }
}

/**
 * End-to-End test suite for competency suggestions in exercise creation workflow
 *
 * Tests the complete user journey:
 * 1. User creates an exercise
 * 2. Enters exercise description
 * 3. Clicks lightbulb to get AI suggestions
 * 4. Views suggested competencies with lightbulb icons
 * 5. Selects competencies (including suggested ones)
 * 6. Saves the exercise
 */
describe('Exercise Creation with Competency Suggestions - E2E', () => {
    let consoleErrorSpy: jest.SpyInstance;
    let fixture: ComponentFixture<TestExerciseFormComponent>;
    let component: TestExerciseFormComponent;
    let httpClient: HttpClient;
    let courseStorageService: CourseStorageService;

    const sampleCompetencies: Competency[] = [
        { id: 1, title: 'Java Programming', description: 'Object-oriented programming in Java', optional: false } as Competency,
        { id: 2, title: 'Data Structures', description: 'Arrays, LinkedLists, Trees, Graphs', optional: false } as Competency,
        { id: 3, title: 'Algorithm Analysis', description: 'Big-O notation and complexity analysis', optional: false } as Competency,
        { id: 4, title: 'Software Testing', description: 'Unit testing and test-driven development', optional: false } as Competency,
        { id: 5, title: 'Design Patterns', description: 'Common software design patterns', optional: false } as Competency,
    ];

    beforeEach(() => {
        consoleErrorSpy = jest.spyOn(console, 'error').mockImplementation(() => {});
    });

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TestExerciseFormComponent],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: {
                            paramMap: convertToParamMap({ courseId: 123 }),
                        },
                    },
                },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                MockProvider(CourseStorageService),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
            schemas: [CUSTOM_ELEMENTS_SCHEMA],
        }).compileComponents();

        fixture = TestBed.createComponent(TestExerciseFormComponent);
        component = fixture.componentInstance;
        httpClient = TestBed.inject(HttpClient);
        courseStorageService = TestBed.inject(CourseStorageService);

        // Setup Atlas feature enabled
        const profileService = TestBed.inject(ProfileService);
        const profileInfo = new ProfileInfo();
        profileInfo.activeModuleFeatures = [MODULE_FEATURE_ATLAS];
        jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(profileInfo);

        // Mock course with competencies
        jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({
            id: 123,
            competencies: sampleCompetencies,
        });
    });

    afterEach(() => {
        consoleErrorSpy?.mockRestore();
        jest.restoreAllMocks();
    });

    describe('Template compatibility', () => {
        it('should render without template binding errors', () => {
            expect(() => fixture.detectChanges()).not.toThrow();
            expect(consoleErrorSpy).not.toHaveBeenCalled();
        });
    });

    describe('Complete Exercise Creation Workflow', () => {
        it('should allow user to create programming exercise with AI-suggested competencies', fakeAsync(() => {
            // Step 1: User fills out basic exercise information
            component.exercise.title = 'Binary Search Tree Implementation';
            component.exercise.description = 'Implement a binary search tree class in Java with insert, delete, and search methods. Include proper error handling and unit tests.';

            fixture.detectChanges();
            tick(); // Allow form to update

            // Step 2: Verify competency selection component is rendered
            const competencySelection = fixture.debugElement.query(By.directive(CompetencySelectionComponent));
            expect(competencySelection).toBeTruthy();

            // Step 3: Verify lightbulb button host (jhi-button) is present
            const lightbulbButton = fixture.debugElement.query(By.css('jhi-button'));
            expect(lightbulbButton).toBeTruthy();

            // Step 4: Mock API response for suggestions
            const suggestionResponse = {
                competencies: [
                    { id: 1, title: 'Java Programming' },
                    { id: 2, title: 'Data Structures' },
                    { id: 3, title: 'Algorithm Analysis' },
                ],
            };
            const httpSpy = jest.spyOn(httpClient, 'post').mockReturnValue(of(suggestionResponse));

            // Step 5: Trigger suggestions via component API to avoid jhi-button internals
            component.competencySelection().suggestCompetencies();
            tick();
            fixture.detectChanges();

            // Step 6: Verify API was called with correct parameters
            expect(httpSpy).toHaveBeenCalledWith('/api/atlas/competencies/suggest', {
                description: component.exercise.description,
                course_id: '123',
            });

            // Step 7: Verify suggested competencies are highlighted
            const suggestedIcons = fixture.debugElement.queryAll(By.css('fa-icon.text-warning.ms-2'));
            expect(suggestedIcons).toHaveLength(3);

            // Step 8: Verify suggested competencies are sorted to top
            const competencyCheckboxes = fixture.debugElement.queryAll(By.css('input[type="checkbox"]'));
            expect(competencyCheckboxes[0].attributes['id']).toContain('competency-1');
            expect(competencyCheckboxes[1].attributes['id']).toContain('competency-2');
            expect(competencyCheckboxes[2].attributes['id']).toContain('competency-3');

            // Step 9: Select suggested competencies via component API for reliability
            const compForSelect = component.competencySelection();
            const linkJava = compForSelect.competencyLinks?.find((l) => l.competency?.id === 1);
            const linkDS = compForSelect.competencyLinks?.find((l) => l.competency?.id === 2);
            expect(linkJava).toBeTruthy();
            expect(linkDS).toBeTruthy();
            if (linkJava) {
                compForSelect.toggleCompetency(linkJava);
                tick();
            }
            if (linkDS) {
                compForSelect.toggleCompetency(linkDS);
                tick();
            }
            fixture.detectChanges();

            // Step 10: Verify form is valid and save button is enabled
            const form = fixture.debugElement.query(By.css('form'));
            const saveButton = fixture.debugElement.query(By.css('button[type="submit"]'));
            expect(form).toBeTruthy();
            expect(saveButton.nativeElement.disabled).toBeFalsy();

            // Step 11: Verify competency selection component has correct value
            const competencyComponent = component.competencySelection();
            expect(competencyComponent.selectedCompetencyLinks?.length).toBe(2);
            expect(competencyComponent.isSuggested(1)).toBeTruthy();
            expect(competencyComponent.isSuggested(2)).toBeTruthy();
        }));

        it('should handle mixed selection of suggested and non-suggested competencies', fakeAsync(() => {
            component.exercise.description = 'Create a comprehensive testing strategy for a Java application';
            fixture.detectChanges();
            tick();

            // Mock suggestions for testing-related competencies
            const suggestionResponse = {
                competencies: [
                    { id: 4, title: 'Software Testing' }, // Only testing is suggested
                ],
            };
            jest.spyOn(httpClient, 'post').mockReturnValue(of(suggestionResponse));

            // Get suggestions via component API
            const comp = component.competencySelection();
            comp.suggestCompetencies();
            tick();
            fixture.detectChanges();

            // Select both suggested and non-suggested via component API
            const compMixed = component.competencySelection();
            const linkTesting = compMixed.competencyLinks?.find((l) => l.competency?.id === 4);
            const linkPatterns = compMixed.competencyLinks?.find((l) => l.competency?.id === 5);
            expect(linkTesting).toBeTruthy();
            expect(linkPatterns).toBeTruthy();
            if (linkTesting) {
                compMixed.toggleCompetency(linkTesting);
                tick();
            }
            if (linkPatterns) {
                compMixed.toggleCompetency(linkPatterns);
                tick();
            }
            fixture.detectChanges();

            // Verify both are selected but only one is marked as suggested
            const competencyComponent = component.competencySelection();
            expect(competencyComponent.selectedCompetencyLinks?.length).toBe(2);
            expect(competencyComponent.isSuggested(4)).toBeTruthy(); // Suggested
            expect(competencyComponent.isSuggested(5)).toBeFalsy(); // Not suggested

            // Verify lightbulb icon only appears next to suggested competency
            const suggestedIcons = fixture.debugElement.queryAll(By.css('fa-icon.text-warning.ms-2'));
            expect(suggestedIcons).toHaveLength(1);
        }));
    });

    describe('Different Exercise Types Integration', () => {
        const exerciseTypes = [
            {
                type: 'Programming',
                title: 'Sorting Algorithm Implementation',
                description: 'Implement quicksort and mergesort algorithms in Python and compare their performance',
                expectedSuggestions: [1, 2, 3], // Programming, Data Structures, Algorithms
            },
            {
                type: 'Text',
                title: 'Software Architecture Essay',
                description: 'Write an analysis of different design patterns and their applications in modern software development',
                expectedSuggestions: [5], // Design Patterns
            },
            {
                type: 'Quiz',
                title: 'Java Fundamentals Quiz',
                description: 'Multiple choice questions covering object-oriented programming concepts, inheritance, and polymorphism in Java',
                expectedSuggestions: [1], // Java Programming
            },
        ];

        exerciseTypes.forEach((exerciseType) => {
            it(`should provide relevant suggestions for ${exerciseType.type} exercises`, fakeAsync(() => {
                // Setup exercise
                component.exercise.title = exerciseType.title;
                component.exercise.description = exerciseType.description;
                fixture.detectChanges();
                tick();

                // Mock API response
                const suggestionResponse = {
                    competencies: exerciseType.expectedSuggestions.map((id) => ({
                        id,
                        title: sampleCompetencies[id - 1].title,
                    })),
                };
                jest.spyOn(httpClient, 'post').mockReturnValue(of(suggestionResponse));

                // Request suggestions via component API
                const comp = component.competencySelection();
                comp.suggestCompetencies();
                tick();
                fixture.detectChanges();

                // Verify correct suggestions
                const competencyComponent = component.competencySelection();
                exerciseType.expectedSuggestions.forEach((competencyId) => {
                    expect(competencyComponent.isSuggested(competencyId)).toBeTruthy();
                });

                // Verify correct number of suggestion icons
                const suggestedIcons = fixture.debugElement.queryAll(By.css('fa-icon.text-warning.ms-2'));
                expect(suggestedIcons).toHaveLength(exerciseType.expectedSuggestions.length);
            }));
        });
    });

    describe('Error Handling in Exercise Creation', () => {
        beforeEach(() => {
            component.exercise.title = 'Test Exercise';
            component.exercise.description = 'Test description for suggestions';
            fixture.detectChanges();
        });

        it('should handle API errors gracefully without breaking exercise creation', fakeAsync(() => {
            // Mock API error
            jest.spyOn(httpClient, 'post').mockReturnValue(throwError(() => ({ status: 500, message: 'Server Error' })));

            const comp = component.competencySelection();
            comp.suggestCompetencies();
            tick();
            fixture.detectChanges();

            // Exercise creation should still work
            const saveButton = fixture.debugElement.query(By.css('button[type="submit"]'));
            expect(saveButton.nativeElement.disabled).toBeFalsy();

            // No suggestions should be shown, but component should still function
            const competencyComponent = component.competencySelection();
            expect(competencyComponent.suggestedCompetencyIds.size).toBe(0);
            expect(competencyComponent.isSuggesting).toBeFalsy();

            // User should still be able to manually select competencies
            const checkboxes = fixture.debugElement.queryAll(By.css('input[type="checkbox"]'));
            checkboxes[0].nativeElement.click();
            fixture.detectChanges();

            expect(competencyComponent.selectedCompetencyLinks?.length).toBe(1);
        }));

        it('should work when AtlasML feature is disabled', () => {
            // Disable Atlas feature
            const profileService = TestBed.inject(ProfileService);
            const profileInfo = new ProfileInfo();
            profileInfo.activeModuleFeatures = [];
            jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(profileInfo);

            // Recreate component with disabled feature
            component.competencySelection().ngOnInit();
            fixture.detectChanges();

            // Exercise creation should still work without suggestions
            const saveButton = fixture.debugElement.query(By.css('button[type="submit"]'));
            expect(saveButton.nativeElement.disabled).toBeFalsy();

            // Lightbulb button should be hidden but competency selection should work
            const checkboxes = fixture.debugElement.queryAll(By.css('input[type="checkbox"]'));
            expect(checkboxes.length).toBeGreaterThan(0);
        });
    });

    describe('Form Validation Integration', () => {
        it('should maintain form validation when using competency suggestions', fakeAsync(() => {
            // Start with valid form (both title and description provided)
            component.exercise.title = 'Valid Title';
            component.exercise.description = 'Valid description for testing';
            fixture.detectChanges();
            tick();

            let saveButton = fixture.debugElement.query(By.css('button[type="submit"]'));
            expect(saveButton.nativeElement.disabled).toBeFalsy(); // Should be enabled

            // Using suggestions shouldn't affect form validation
            const mockResponse = { competencies: [{ id: 1, title: 'Test' }] };
            jest.spyOn(httpClient, 'post').mockReturnValue(of(mockResponse));

            const comp = component.competencySelection();
            comp.suggestCompetencies();
            tick();
            fixture.detectChanges();

            saveButton = fixture.debugElement.query(By.css('button[type="submit"]'));
            expect(saveButton.nativeElement.disabled).toBeFalsy(); // Should still be enabled
        }));
    });
});
