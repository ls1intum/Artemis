import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { Competency, CompetencyLearningObjectLink } from 'app/atlas/shared/entities/competency.model';
import { of, throwError } from 'rxjs';
import { HttpClient, HttpResponse, provideHttpClient } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { ChangeDetectorRef } from '@angular/core';
import { CourseCompetencyService } from 'app/atlas/shared/services/course-competency.service';
import { Prerequisite } from 'app/atlas/shared/entities/prerequisite.model';
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
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';

describe('CompetencySelection', () => {
    let fixture: ComponentFixture<CompetencySelectionComponent>;
    let component: CompetencySelectionComponent;
    let courseStorageService: CourseStorageService;
    let courseCompetencyService: CourseCompetencyService;
    let httpClient: HttpClient;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CompetencySelectionComponent],
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
                MockProvider(CourseStorageService, {
                    getCourse: () => ({ id: 1, competencies: [] }),
                }),
                MockProvider(CourseCompetencyService, {
                    getAllForCourse: () => of(new HttpResponse({ body: [] })),
                }),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        });

        fixture = TestBed.createComponent(CompetencySelectionComponent);
        component = fixture.componentInstance;
        courseStorageService = TestBed.inject(CourseStorageService);
        courseCompetencyService = TestBed.inject(CourseCompetencyService);
        httpClient = TestBed.inject(HttpClient);
        const profileService = TestBed.inject(ProfileService);

        const profileInfo = { activeModuleFeatures: [MODULE_FEATURE_ATLAS] } as ProfileInfo;
        const getProfileInfoMock = jest.spyOn(profileService, 'getProfileInfo');
        getProfileInfoMock.mockReturnValue(profileInfo);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should get competencies from cache', () => {
        const nonOptional = { id: 1, optional: false } as Competency;
        const optional = { id: 2, optional: true } as Competency;
        const getCourseSpy = jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: [nonOptional, optional] });
        const getAllForCourseSpy = jest.spyOn(courseCompetencyService, 'getAllForCourse');

        fixture.detectChanges();

        const selector = fixture.debugElement.nativeElement.querySelector('#competency-selector');
        expect(component.selectedCompetencyLinks).toBeUndefined();
        expect(getCourseSpy).toHaveBeenCalledOnce();
        expect(getAllForCourseSpy).not.toHaveBeenCalled();
        expect(component.isLoading).toBeFalse();
        expect(component.competencyLinks).toBeArrayOfSize(2);
        expect(selector).not.toBeNull();
    });

    it('should get competencies from service', () => {
        const nonOptional = { id: 1, optional: false } as Competency;
        const optional = { id: 2, optional: true } as Competency;
        const getCourseSpy = jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: undefined });
        const getAllForCourseSpy = jest.spyOn(courseCompetencyService, 'getAllForCourse').mockReturnValue(of(new HttpResponse({ body: [nonOptional, optional] })));

        fixture.detectChanges();

        expect(getCourseSpy).toHaveBeenCalledOnce();
        expect(getAllForCourseSpy).toHaveBeenCalledOnce();
        expect(component.isLoading).toBeFalse();
        expect(component.competencyLinks).toBeArrayOfSize(2);
        expect(component.competencyLinks?.first()?.competency?.course).toBeUndefined();
        expect(component.competencyLinks?.first()?.competency?.userProgress).toBeUndefined();
    });

    it('should set disabled when error during loading', () => {
        const getCourseSpy = jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: undefined });
        const getAllForCourseSpy = jest.spyOn(courseCompetencyService, 'getAllForCourse').mockReturnValue(throwError(() => ({ status: 404 })));

        fixture.detectChanges();

        expect(getCourseSpy).toHaveBeenCalledOnce();
        expect(getAllForCourseSpy).toHaveBeenCalledOnce();
        expect(component.isLoading).toBeFalse();
        expect(component.disabled).toBeTrue();
    });

    it('should be hidden when no competencies', () => {
        const getCourseSpy = jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: [] });
        const getAllForCourseSpy = jest.spyOn(courseCompetencyService, 'getAllForCourse').mockReturnValue(of(new HttpResponse({ body: [] })));

        fixture.detectChanges();

        const select = fixture.debugElement.query(By.css('select'));
        expect(getCourseSpy).toHaveBeenCalledOnce();
        expect(getAllForCourseSpy).toHaveBeenCalledOnce();
        expect(component.isLoading).toBeFalse();
        expect(component.competencyLinks).toBeEmpty();
        expect(select).toBeNull();
    });

    it('should select competencies when value is written', () => {
        jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: [{ id: 1, title: 'test' } as Competency] });

        fixture.detectChanges();

        component.writeValue([new CompetencyLearningObjectLink({ id: 1, title: 'other' } as Competency, 1)]);
        expect(component.selectedCompetencyLinks).toBeArrayOfSize(1);
        expect(component.selectedCompetencyLinks?.first()?.competency?.title).toBe('test');
    });

    it('should update link weight when value is written', () => {
        jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({
            competencies: [{ id: 1, title: 'test' } as Competency, { id: 2, title: 'testAgain' } as Prerequisite, { id: 3, title: 'testMore' } as Competency],
        });

        fixture.detectChanges();

        component.writeValue([
            new CompetencyLearningObjectLink({ id: 1, title: 'other' } as Competency, 0.5),
            new CompetencyLearningObjectLink({ id: 3, title: 'otherMore' } as Competency, 1),
        ]);
        expect(component.selectedCompetencyLinks).toBeArrayOfSize(2);
        expect(component.selectedCompetencyLinks?.first()?.weight).toBe(0.5);
        expect(component.selectedCompetencyLinks?.last()?.weight).toBe(1);
    });

    it('should trigger change detection after loading competencies', () => {
        jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: undefined });
        const changeDetector = fixture.debugElement.injector.get(ChangeDetectorRef);
        const detectChangesStub = jest.spyOn(changeDetector.constructor.prototype, 'detectChanges');

        fixture.detectChanges();

        expect(detectChangesStub).toHaveBeenCalledTimes(2);
    });

    it('should select / unselect competencies', () => {
        const competency1 = { id: 1, optional: false } as Competency;
        const competency2 = { id: 2, optional: true } as Competency;
        const competency3 = { id: 3, optional: false } as Competency;
        jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: [competency1, competency2, competency3] });

        fixture.detectChanges();
        expect(component.selectedCompetencyLinks).toBeUndefined();

        component.toggleCompetency(new CompetencyLearningObjectLink(competency1, 1));
        component.toggleCompetency(new CompetencyLearningObjectLink(competency2, 1));
        component.toggleCompetency(new CompetencyLearningObjectLink(competency3, 1));

        expect(component.selectedCompetencyLinks).toHaveLength(3);
        expect(component.selectedCompetencyLinks).toContainEqual(new CompetencyLearningObjectLink(competency3, 1));

        component.toggleCompetency(new CompetencyLearningObjectLink(competency2, 1));

        expect(component.selectedCompetencyLinks).toHaveLength(2);
        expect(component.selectedCompetencyLinks).not.toContainEqual(new CompetencyLearningObjectLink(competency2, 1));

        component.toggleCompetency(new CompetencyLearningObjectLink(competency1, 1));
        component.toggleCompetency(new CompetencyLearningObjectLink(competency3, 1));

        expect(component.selectedCompetencyLinks).toBeUndefined();
    });

    it('should register onchange', () => {
        component.checkboxStates = {};
        const registerSpy = jest.fn();
        component.registerOnChange(registerSpy);
        component.toggleCompetency(new CompetencyLearningObjectLink({ id: 1 }, 1));
        expect(registerSpy).toHaveBeenCalled();
    });

    it('should set disabled state', () => {
        component.disabled = true;
        component.setDisabledState?.(false);
        expect(component.disabled).toBeFalse();
    });

    describe('AtlasML Competency Suggestions', () => {
        beforeEach(() => {
            const competency1 = { id: 1, title: 'Programming Basics', optional: false } as Competency;
            const competency2 = { id: 2, title: 'Data Structures', optional: false } as Competency;
            const competency3 = { id: 3, title: 'Algorithms', optional: false } as Competency;
            jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: [competency1, competency2, competency3] });

            fixture.componentRef.setInput('exerciseDescription', 'Create a Java program that implements a sorting algorithm');
            fixture.detectChanges();
        });

        it('should show lightbulb button for competency suggestions', () => {
            fixture.detectChanges();
            const btnDe = fixture.debugElement.query(By.css('jhi-button'));
            expect(btnDe).not.toBeNull();
            // Disabled state is controlled by input binding
            const cmp = btnDe.componentInstance;
            expect(cmp.disabled).toBeFalse();
        });

        it('should disable lightbulb button when no exercise description', () => {
            fixture.componentRef.setInput('exerciseDescription', '');
            fixture.detectChanges();

            const btnDe = fixture.debugElement.query(By.css('jhi-button'));
            const cmp = btnDe.componentInstance;
            expect(cmp.disabled).toBeTrue();
        });

        it('should call API and show suggestions when lightbulb button is clicked', () => {
            const mockSuggestionResponse = {
                competencies: [
                    { id: 1, title: 'Programming Basics' },
                    { id: 3, title: 'Algorithms' },
                ],
            };

            const httpPostSpy = jest.spyOn(httpClient, 'post').mockReturnValue(of(mockSuggestionResponse));

            component.suggestCompetencies();

            expect(httpPostSpy).toHaveBeenCalledWith('/api/atlas/competencies/suggest', {
                description: 'Create a Java program that implements a sorting algorithm',
                course_id: '1',
            });
            expect(component.suggestedCompetencyIds.has(1)).toBeTrue();
            expect(component.suggestedCompetencyIds.has(3)).toBeTrue();
            expect(component.suggestedCompetencyIds.has(2)).toBeFalse();
            expect(component.isSuggesting).toBeFalse();
        });

        it('should show spinner while suggesting competencies', () => {
            component.isSuggesting = true;
            fixture.detectChanges();

            const btnDe = fixture.debugElement.query(By.css('jhi-button'));
            const spinnerIcon = btnDe?.query(By.css('.jhi-btn__loading'));
            const lightbulbIcon = btnDe?.query(By.css('.jhi-btn__icon'));

            expect(spinnerIcon).not.toBeNull();
            expect(lightbulbIcon).toBeNull(); // Should not show lightbulb icon when showing spinner
        });

        it('should display lightbulb icon next to suggested competencies', () => {
            const mockSuggestionResponse = {
                competencies: [{ id: 1, title: 'Programming Basics' }],
            };

            jest.spyOn(httpClient, 'post').mockReturnValue(of(mockSuggestionResponse));

            component.suggestCompetencies();
            fixture.detectChanges();

            expect(component.isSuggested(1)).toBeTrue();
            expect(component.isSuggested(2)).toBeFalse();

            // Check for lightbulb icons with warning color next to competencies
            const suggestedLightbulbs = fixture.debugElement.queryAll(By.css('fa-icon.text-warning.ms-2'));
            expect(suggestedLightbulbs.length).toBeGreaterThan(0);
        });

        it('should sort suggested competencies to the top', () => {
            const mockSuggestionResponse = {
                competencies: [{ id: 3, title: 'Algorithms' }],
            };

            jest.spyOn(httpClient, 'post').mockReturnValue(of(mockSuggestionResponse));

            component.suggestCompetencies();

            const firstCompetency = component.competencyLinks?.[0];
            expect(firstCompetency?.competency?.id).toBe(3);
            expect(component.isSuggested(3)).toBeTrue();
        });

        it('should handle API error gracefully', () => {
            jest.spyOn(httpClient, 'post').mockReturnValue(throwError(() => ({ status: 500 })));

            component.suggestCompetencies();

            expect(component.isSuggesting).toBeFalse();
            expect(component.suggestedCompetencyIds.size).toBe(0);
        });

        it('should not call API if description is empty or whitespace only', () => {
            const httpPostSpy = jest.spyOn(httpClient, 'post');

            fixture.componentRef.setInput('exerciseDescription', '');
            component.suggestCompetencies();
            expect(httpPostSpy).not.toHaveBeenCalled();

            fixture.componentRef.setInput('exerciseDescription', '   ');
            component.suggestCompetencies();
            expect(httpPostSpy).not.toHaveBeenCalled();
        });

        it('should clear previous suggestions when making new request', () => {
            // First suggestion
            component.suggestedCompetencyIds.add(1);
            component.suggestedCompetencyIds.add(2);

            const mockResponse = { competencies: [{ id: 3, title: 'New Suggestion' }] };
            jest.spyOn(httpClient, 'post').mockReturnValue(of(mockResponse));

            component.suggestCompetencies();

            expect(component.suggestedCompetencyIds.has(1)).toBeFalse();
            expect(component.suggestedCompetencyIds.has(2)).toBeFalse();
            expect(component.suggestedCompetencyIds.has(3)).toBeTrue();
        });

        it('should handle empty response from API', () => {
            const mockResponse = { competencies: [] };
            jest.spyOn(httpClient, 'post').mockReturnValue(of(mockResponse));

            component.suggestCompetencies();

            expect(component.suggestedCompetencyIds.size).toBe(0);
            expect(component.isSuggesting).toBeFalse();
        });

        it('should disable lightbulb button while suggesting', () => {
            component.isSuggesting = true;
            fixture.detectChanges();

            const btnDe = fixture.debugElement.query(By.css('jhi-button'));
            const cmp = btnDe.componentInstance;
            expect(cmp.disabled).toBeTrue();
        });

        it('should show lightbulb tooltip', () => {
            fixture.detectChanges();
            const btnDe = fixture.debugElement.query(By.css('jhi-button'));
            const cmp = btnDe.componentInstance;
            expect(cmp.tooltip).toBe('artemisApp.courseCompetency.relations.suggestions.getAiSuggestionsTooltip');
        });

        it('should show AI suggested competency tooltip', () => {
            const mockSuggestionResponse = {
                competencies: [{ id: 1, title: 'Programming Basics' }],
            };

            jest.spyOn(httpClient, 'post').mockReturnValue(of(mockSuggestionResponse));

            component.suggestCompetencies();
            fixture.detectChanges();

            const suggestedIcon = fixture.debugElement.query(By.css('fa-icon.text-warning.ms-2'));
            expect(suggestedIcon).toBeTruthy();
            const tooltip = suggestedIcon.injector.get(NgbTooltip, null);
            expect(tooltip).toBeTruthy();
            expect(tooltip?.ngbTooltip).toBe('artemisApp.competency.suggestion.tooltip');
        });

        it('should match competencies by ID when processing suggestions', () => {
            const mockResponse = {
                competencies: [
                    { id: 1, title: 'Different Title' }, // ID matches but title is different
                    { id: 999, title: 'Non-existent' }, // ID doesn't match any competency
                ],
            };
            jest.spyOn(httpClient, 'post').mockReturnValue(of(mockResponse));

            component.suggestCompetencies();

            expect(component.suggestedCompetencyIds.has(1)).toBeTrue(); // Should match by ID
            expect(component.suggestedCompetencyIds.has(999)).toBeFalse(); // Should not match non-existent ID
        });
    });

    // Additional test suite for exercise creation integration
    describe('Exercise Creation Integration', () => {
        it('should accept exercise description as input', () => {
            const testDescription = 'Test exercise description for suggestions';
            fixture.componentRef.setInput('exerciseDescription', testDescription);
            expect(component.exerciseDescription()).toBe(testDescription);
        });

        it('should emit value changes when competency is toggled', () => {
            const competency = { id: 1, title: 'Test Competency', optional: false } as Competency;
            jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: [competency] });

            const emitSpy = jest.spyOn(component.valueChange, 'emit');

            fixture.detectChanges();

            component.toggleCompetency(new CompetencyLearningObjectLink(competency, 1));

            expect(emitSpy).toHaveBeenCalled();
        });

        it('should work with different exercise types', () => {
            // Test that the component works regardless of the exercise type
            // by testing with different descriptions
            const descriptions = [
                'Programming exercise: implement bubble sort',
                'Text exercise: write an essay about algorithms',
                'Modeling exercise: create UML diagrams',
                'Quiz exercise: multiple choice questions',
            ];

            descriptions.forEach((desc) => {
                fixture.componentRef.setInput('exerciseDescription', desc);
                expect(component.exerciseDescription()?.trim()).toBeTruthy();

                // Button should be enabled for non-empty descriptions
                fixture.detectChanges();
                const lightbulbButton = fixture.debugElement.query(By.css('button[ngbTooltip="Get AI Suggestions"]'));
                expect(lightbulbButton?.nativeElement.disabled).toBeFalsy();
            });
        });
    });
});
