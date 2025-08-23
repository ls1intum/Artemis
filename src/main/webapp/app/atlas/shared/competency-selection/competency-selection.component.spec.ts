import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { Competency, CompetencyLearningObjectLink } from 'app/atlas/shared/entities/competency.model';
import { of, throwError } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
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

describe('CompetencySelection', () => {
    let fixture: ComponentFixture<CompetencySelectionComponent>;
    let component: CompetencySelectionComponent;
    let courseStorageService: CourseStorageService;
    let courseCompetencyService: CourseCompetencyService;
    let httpClient: HttpClient;

    beforeEach(() => {
        TestBed.configureTestingModule({
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
                MockProvider(CourseStorageService),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CompetencySelectionComponent);
                component = fixture.componentInstance;
                courseStorageService = fixture.debugElement.injector.get(CourseStorageService);
                courseCompetencyService = fixture.debugElement.injector.get(CourseCompetencyService);
                httpClient = fixture.debugElement.injector.get(HttpClient);
                const profileService = fixture.debugElement.injector.get(ProfileService);

                const profileInfo = { activeModuleFeatures: [MODULE_FEATURE_ATLAS] } as ProfileInfo;
                const getProfileInfoMock = jest.spyOn(profileService, 'getProfileInfo');
                getProfileInfoMock.mockReturnValue(profileInfo);
            });
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

        expect(detectChangesStub).toHaveBeenCalledOnce();
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

            component.exerciseDescription = 'Create a Java program that implements a sorting algorithm';
            fixture.detectChanges();
        });

        it('should show lightbulb button for competency suggestions', () => {
            const lightbulbButton = fixture.debugElement.query(By.css('button[ngbTooltip="Get AI Suggestions"]'));
            expect(lightbulbButton).not.toBeNull();
            expect(lightbulbButton.nativeElement.disabled).toBeFalse();
        });

        it('should disable lightbulb button when no exercise description', () => {
            component.exerciseDescription = '';
            fixture.detectChanges();

            const lightbulbButton = fixture.debugElement.query(By.css('button[ngbTooltip="Get AI Suggestions"]'));
            expect(lightbulbButton.nativeElement.disabled).toBeTrue();
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
        });

        it('should show spinner while suggesting competencies', () => {
            jest.spyOn(httpClient, 'post').mockReturnValue(of({ competencies: [] }));

            component.isSuggesting = true;
            fixture.detectChanges();

            const spinner = fixture.debugElement.query(By.css('.spinner-border-sm'));
            const lightbulbIcon = fixture.debugElement.query(By.css('fa-icon'));

            expect(spinner).not.toBeNull();
            expect(lightbulbIcon).toBeNull();
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

            const suggestedLightbulbs = fixture.debugElement.queryAll(By.css('fa-icon.text-warning'));
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

            component.exerciseDescription = '';
            component.suggestCompetencies();
            expect(httpPostSpy).not.toHaveBeenCalled();

            component.exerciseDescription = '   ';
            component.suggestCompetencies();
            expect(httpPostSpy).not.toHaveBeenCalled();
        });
    });
});
