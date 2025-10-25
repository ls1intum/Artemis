import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { Competency } from 'app/atlas/shared/entities/competency.model';
import { delay, of, throwError } from 'rxjs';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MODULE_FEATURE_ATLAS } from 'app/app.constants';
import { CompetencySelectionComponent } from 'app/atlas/shared/competency-selection/competency-selection.component';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { MockFeatureToggleService } from 'test/helpers/mocks/service/mock-feature-toggle.service';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';

/**
 * Integration test suite for AtlasML Competency Suggestion Feature
 *
 * This test suite covers the user stories:
 * 1. When creating an exercise, show a lightbulb button for AI suggestions
 * 2. When clicked, suggest competencies based on exercise description
 * 3. Show lightbulb icons next to suggested competencies
 * 4. Handle various edge cases and error scenarios
 */
describe('AtlasML Competency Suggestions Integration Tests', () => {
    let fixture: ComponentFixture<CompetencySelectionComponent>;
    let component: CompetencySelectionComponent;
    let courseStorageService: CourseStorageService;
    let httpClient: HttpClient;
    let profileService: ProfileService;
    let mockFeatureToggleService: MockFeatureToggleService;

    // Sample competencies for testing
    const sampleCompetencies: Competency[] = [
        { id: 1, title: 'Programming Fundamentals', description: 'Basic programming concepts', optional: false } as Competency,
        { id: 2, title: 'Data Structures', description: 'Arrays, lists, trees, etc.', optional: false } as Competency,
        { id: 3, title: 'Algorithms', description: 'Sorting and searching algorithms', optional: false } as Competency,
        { id: 4, title: 'Object-Oriented Programming', description: 'OOP concepts and patterns', optional: false } as Competency,
        { id: 5, title: 'Database Design', description: 'Relational database concepts', optional: false } as Competency,
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: {
                            paramMap: convertToParamMap({ courseId: 1 }),
                        },
                    } as any as ActivatedRoute,
                },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: FeatureToggleService, useClass: MockFeatureToggleService },
                MockProvider(CourseStorageService),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        // Initialize feature toggles subject in mock BEFORE component creation so directives can subscribe safely
        mockFeatureToggleService = TestBed.inject(FeatureToggleService) as unknown as MockFeatureToggleService;
        mockFeatureToggleService.getFeatureToggles();

        fixture = TestBed.createComponent(CompetencySelectionComponent);
        component = fixture.componentInstance;
        courseStorageService = fixture.debugElement.injector.get(CourseStorageService);
        httpClient = fixture.debugElement.injector.get(HttpClient);
        profileService = fixture.debugElement.injector.get(ProfileService);

        // Enable Atlas module feature
        const profileInfo = new ProfileInfo();
        profileInfo.activeModuleFeatures = [MODULE_FEATURE_ATLAS];
        const getProfileInfoMock = jest.spyOn(profileService, 'getProfileInfo');
        getProfileInfoMock.mockReturnValue(profileInfo);

        // Setup mock competencies
        jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: sampleCompetencies });

        // Setup exercise description for suggestions
        fixture.componentRef.setInput('exerciseDescription', 'Create a Java program that implements a binary search tree with insertion and deletion operations');
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('UI Component Visibility', () => {
        it('should display lightbulb suggestion button when AtlasML is enabled', () => {
            const btnDe = fixture.debugElement.query(By.directive(ButtonComponent));
            expect(btnDe).toBeTruthy();
            const btn = btnDe.componentInstance as ButtonComponent;
            expect(btn.disabled).toBeFalsy();
        });

        it('should hide lightbulb button when AtlasML feature is disabled', () => {
            // Disable AtlasML feature toggle
            mockFeatureToggleService.setFeatureToggleState(FeatureToggle.AtlasML, false).subscribe();
            fixture.detectChanges();

            const btnDe = fixture.debugElement.query(By.directive(ButtonComponent));
            expect(btnDe.nativeElement.classList.contains('d-none')).toBeTruthy();
        });

        it('should show proper tooltip for lightbulb button', () => {
            const btnDe = fixture.debugElement.query(By.directive(ButtonComponent));
            const btn = btnDe.componentInstance as ButtonComponent;
            expect(btn.tooltip).toBe('artemisApp.courseCompetency.relations.suggestions.getAiSuggestionsTooltip');
        });

        it('should disable lightbulb button when exercise description is empty', () => {
            fixture.componentRef.setInput('exerciseDescription', '');
            fixture.detectChanges();

            const btnDe = fixture.debugElement.query(By.directive(ButtonComponent));
            const btn = btnDe.componentInstance as ButtonComponent;
            expect(btn.disabled).toBeTruthy();
        });

        it('should disable lightbulb button when exercise description contains only whitespace', () => {
            fixture.componentRef.setInput('exerciseDescription', '   \n\t  ');
            fixture.detectChanges();

            const btnDe = fixture.debugElement.query(By.directive(ButtonComponent));
            const btn = btnDe.componentInstance as ButtonComponent;
            expect(btn.disabled).toBeTruthy();
        });
    });

    describe('API Integration', () => {
        it('should call AtlasML API with correct parameters', () => {
            const mockResponse = { competencies: [{ id: 1, title: 'Programming Fundamentals' }] };
            const httpPostSpy = jest.spyOn(httpClient, 'post').mockReturnValue(of(mockResponse));

            component.suggestCompetencies();

            expect(httpPostSpy).toHaveBeenCalledWith('/api/atlas/competencies/suggest', {
                description: 'Create a Java program that implements a binary search tree with insertion and deletion operations',
                course_id: '1',
            });
        });

        it('should handle successful API response', fakeAsync(() => {
            const mockResponse = {
                competencies: [
                    { id: 1, title: 'Programming Fundamentals' },
                    { id: 2, title: 'Data Structures' },
                ],
            };
            jest.spyOn(httpClient, 'post').mockReturnValue(of(mockResponse).pipe(delay(100)));

            component.suggestCompetencies();
            expect(component.isSuggesting).toBeTruthy();

            tick(100);
            fixture.detectChanges();

            expect(component.isSuggesting).toBeFalsy();
            expect(component.suggestedCompetencyIds.has(1)).toBeTruthy();
            expect(component.suggestedCompetencyIds.has(2)).toBeTruthy();
            expect(component.suggestedCompetencyIds.has(3)).toBeFalsy();
        }));

        it('should handle API errors gracefully', fakeAsync(() => {
            const consoleSpy = jest.spyOn(console, 'error').mockImplementation();
            jest.spyOn(httpClient, 'post').mockReturnValue(throwError(() => ({ status: 500, message: 'Server Error' })));

            component.suggestCompetencies();
            // After error, isSuggesting should be false due to finalize operator
            tick();
            fixture.detectChanges();

            expect(component.isSuggesting).toBeFalsy();
            expect(component.suggestedCompetencyIds.size).toBe(0);
            consoleSpy.mockRestore();
        }));

        it('should handle network timeout', fakeAsync(() => {
            jest.spyOn(httpClient, 'post').mockReturnValue(throwError(() => ({ name: 'TimeoutError' })));

            component.suggestCompetencies();
            tick();

            expect(component.isSuggesting).toBeFalsy();
            expect(component.suggestedCompetencyIds.size).toBe(0);
        }));
    });

    describe('Suggestion Display and Interaction', () => {
        beforeEach(() => {
            const mockResponse = {
                competencies: [
                    { id: 2, title: 'Data Structures' },
                    { id: 3, title: 'Algorithms' },
                ],
            };
            jest.spyOn(httpClient, 'post').mockReturnValue(of(mockResponse));
            component.suggestCompetencies();
            fixture.detectChanges();
        });

        it('should display lightbulb icons next to suggested competencies', () => {
            const suggestedIcons = fixture.debugElement.queryAll(By.css('fa-icon.text-warning.ms-2'));
            expect(suggestedIcons).toHaveLength(2); // Two suggested competencies

            // Verify tooltips
            suggestedIcons.forEach((icon) => {
                const tooltip = icon.injector.get(NgbTooltip, null);
                // Expect translation key to be present (translated by pipe in template)
                expect(tooltip?.ngbTooltip).toBe('artemisApp.competency.suggestion.tooltip');
            });
        });

        it('should sort suggested competencies to the top of the list', () => {
            // Verify that suggested competencies (id 2 and 3) are at the top
            const competencyCheckboxes = fixture.debugElement.queryAll(By.css('input[type="checkbox"]'));

            expect(competencyCheckboxes[0].attributes['id']).toContain('competency-2'); // Data Structures
            expect(competencyCheckboxes[1].attributes['id']).toContain('competency-3'); // Algorithms
        });

        it('should maintain suggested status when toggling competencies', () => {
            const dataStructuresLink = component.competencyLinks?.find((link) => link.competency?.id === 2);
            expect(dataStructuresLink).toBeTruthy();

            // Toggle competency on/off - suggestion status should persist
            if (dataStructuresLink) {
                component.toggleCompetency(dataStructuresLink);
                expect(component.isSuggested(2)).toBeTruthy();

                component.toggleCompetency(dataStructuresLink);
                expect(component.isSuggested(2)).toBeTruthy();
            }
        });

        it('should clear previous suggestions when making new request', () => {
            expect(component.suggestedCompetencyIds.has(2)).toBeTruthy();
            expect(component.suggestedCompetencyIds.has(3)).toBeTruthy();

            // Mock new response with different suggestions
            const newMockResponse = { competencies: [{ id: 1, title: 'Programming Fundamentals' }] };
            jest.spyOn(httpClient, 'post').mockReturnValue(of(newMockResponse));

            component.suggestCompetencies();

            expect(component.suggestedCompetencyIds.has(1)).toBeTruthy();
            expect(component.suggestedCompetencyIds.has(2)).toBeFalsy(); // Cleared
            expect(component.suggestedCompetencyIds.has(3)).toBeFalsy(); // Cleared
        });
    });

    describe('Loading States', () => {
        it('should show spinner during API call', fakeAsync(() => {
            jest.spyOn(httpClient, 'post').mockReturnValue(of({ competencies: [] }).pipe(delay(200)));

            component.suggestCompetencies();
            fixture.detectChanges();

            // Should show spinner
            // Loading is now handled by jhi-button isLoading; spinner element is not directly in DOM here
            const btnDe = fixture.debugElement.query(By.directive(ButtonComponent));
            const btn = btnDe.componentInstance as ButtonComponent;
            expect(btn.isLoading).toBeTruthy();

            // Should not show lightbulb icon (hidden when loading)
            const lightbulbIcon = btnDe.query(By.css('.jhi-btn__icon'));
            expect(lightbulbIcon).toBeFalsy();
            // Spinner should be visible on the button while loading
            const spinnerIcon = btnDe.query(By.css('.jhi-btn__loading'));
            expect(spinnerIcon).toBeTruthy();

            tick(200);
            fixture.detectChanges();

            // After completion, spinner should be gone
            const spinnerAfter = fixture.debugElement.query(By.css('.spinner-border-sm'));
            expect(spinnerAfter).toBeFalsy();
        }));

        it('should disable button during API call', fakeAsync(() => {
            jest.spyOn(httpClient, 'post').mockReturnValue(of({ competencies: [] }).pipe(delay(100)));

            let btnDe = fixture.debugElement.query(By.directive(ButtonComponent));
            let btn = btnDe.componentInstance as ButtonComponent;
            expect(btn.disabled).toBeFalsy();

            component.suggestCompetencies();
            fixture.detectChanges();

            btnDe = fixture.debugElement.query(By.directive(ButtonComponent));
            btn = btnDe.componentInstance as ButtonComponent;
            expect(btn.disabled).toBeTruthy();

            tick(100);
            fixture.detectChanges();

            btnDe = fixture.debugElement.query(By.directive(ButtonComponent));
            btn = btnDe.componentInstance as ButtonComponent;
            expect(btn.disabled).toBeFalsy();
        }));
    });

    describe('Exercise Type Integration', () => {
        const exerciseScenarios = [
            {
                type: 'Programming',
                description: 'Implement a hash table with collision resolution using chaining',
                expectedSuggestions: [1, 2], // Programming + Data Structures
            },
            {
                type: 'Text',
                description: 'Write an essay comparing different sorting algorithms and their time complexities',
                expectedSuggestions: [3], // Algorithms
            },
            {
                type: 'Modeling',
                description: 'Create a UML class diagram for a library management system using object-oriented principles',
                expectedSuggestions: [4], // Object-Oriented Programming
            },
            {
                type: 'Quiz',
                description: 'Multiple choice questions about database normalization and entity-relationship modeling',
                expectedSuggestions: [5], // Database Design
            },
        ];

        exerciseScenarios.forEach((scenario) => {
            it(`should work correctly for ${scenario.type} exercises`, fakeAsync(() => {
                fixture.componentRef.setInput('exerciseDescription', scenario.description);

                const mockResponse = {
                    competencies: scenario.expectedSuggestions.map((id) => ({ id, title: sampleCompetencies[id - 1].title })),
                };
                jest.spyOn(httpClient, 'post').mockReturnValue(of(mockResponse));

                component.suggestCompetencies();
                tick();
                fixture.detectChanges();

                // Verify correct competencies are suggested
                scenario.expectedSuggestions.forEach((competencyId) => {
                    expect(component.isSuggested(competencyId)).toBeTruthy();
                });

                // Verify API was called with correct description
                expect(httpClient.post).toHaveBeenCalledWith('/api/atlas/competencies/suggest', {
                    description: scenario.description,
                    course_id: '1',
                });
            }));
        });
    });

    describe('Edge Cases and Error Handling', () => {
        it('should handle empty competency list from API', () => {
            const mockResponse = { competencies: [] };
            jest.spyOn(httpClient, 'post').mockReturnValue(of(mockResponse));

            component.suggestCompetencies();

            expect(component.suggestedCompetencyIds.size).toBe(0);
            expect(component.isSuggesting).toBeFalsy();
        });

        it('should handle malformed API response', () => {
            const mockResponse = { invalid: 'response' };
            jest.spyOn(httpClient, 'post').mockReturnValue(of(mockResponse));

            expect(() => component.suggestCompetencies()).not.toThrow();
            expect(component.suggestedCompetencyIds.size).toBe(0);
        });

        it('should handle competency suggestions with non-existent IDs', () => {
            const mockResponse = {
                competencies: [
                    { id: 999, title: 'Non-existent Competency' },
                    { id: 1, title: 'Valid Competency' },
                    { id: 888, title: 'Another Non-existent' },
                ],
            };
            jest.spyOn(httpClient, 'post').mockReturnValue(of(mockResponse));

            component.suggestCompetencies();

            // Only valid competency should be marked as suggested
            expect(component.isSuggested(1)).toBeTruthy();
            expect(component.isSuggested(999)).toBeFalsy();
            expect(component.isSuggested(888)).toBeFalsy();
        });

        it('should not make API call if description is null or undefined', () => {
            const httpPostSpy = jest.spyOn(httpClient, 'post');

            fixture.componentRef.setInput('exerciseDescription', null as any);
            component.suggestCompetencies();
            expect(httpPostSpy).not.toHaveBeenCalled();

            fixture.componentRef.setInput('exerciseDescription', undefined);
            component.suggestCompetencies();
            expect(httpPostSpy).not.toHaveBeenCalled();
        });

        it('should handle very long exercise descriptions', () => {
            const longDescription = 'A'.repeat(10000); // 10k character description
            fixture.componentRef.setInput('exerciseDescription', longDescription);

            const mockResponse = { competencies: [{ id: 1, title: 'Test' }] };
            const httpPostSpy = jest.spyOn(httpClient, 'post').mockReturnValue(of(mockResponse));

            component.suggestCompetencies();

            expect(httpPostSpy).toHaveBeenCalledWith('/api/atlas/competencies/suggest', {
                description: longDescription,
                course_id: '1',
            });
        });
    });

    describe('User Workflow Integration', () => {
        it('should maintain user selections when suggestions are made', () => {
            // User selects some competencies first
            const competency1 = component.competencyLinks?.[0];
            const competency4 = component.competencyLinks?.[3];

            if (competency1 && competency4) {
                component.toggleCompetency(competency1);
                component.toggleCompetency(competency4);

                const initialSelections = component.selectedCompetencyLinks?.length || 0;
                expect(initialSelections).toBe(2);

                // Then get suggestions
                const mockResponse = { competencies: [{ id: 2, title: 'Data Structures' }] };
                jest.spyOn(httpClient, 'post').mockReturnValue(of(mockResponse));
                component.suggestCompetencies();

                // User selections should be preserved
                expect(component.selectedCompetencyLinks?.length).toBe(2);
                expect(component.checkboxStates[1]).toBeTruthy();
                expect(component.checkboxStates[4]).toBeTruthy();

                // But suggestions should still be shown
                expect(component.isSuggested(2)).toBeTruthy();
            }
        });

        it('should allow users to select suggested competencies', () => {
            // Get suggestions first
            const mockResponse = { competencies: [{ id: 2, title: 'Data Structures' }] };
            jest.spyOn(httpClient, 'post').mockReturnValue(of(mockResponse));
            component.suggestCompetencies();
            fixture.detectChanges();

            // User clicks on suggested competency
            const suggestedCompetency = component.competencyLinks?.find((link) => link.competency?.id === 2);
            if (suggestedCompetency) {
                component.toggleCompetency(suggestedCompetency);
                expect(component.checkboxStates[2]).toBeTruthy();
                expect(component.selectedCompetencyLinks).toContainEqual(suggestedCompetency);
                expect(component.isSuggested(2)).toBeTruthy(); // Should still be marked as suggested
            }
        });
    });
});
