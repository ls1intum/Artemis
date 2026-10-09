import { vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { Competency, CompetencyLearningObjectLink, CourseCompetency } from 'app/atlas/shared/entities/competency.model';
import { Subject, of, throwError } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import {} from '@angular/core';
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
        const profileService = TestBed.inject(ProfileService);

        const profileInfo = { activeModuleFeatures: [MODULE_FEATURE_ATLAS] } as ProfileInfo;
        const getProfileInfoMock = vi.spyOn(profileService, 'getProfileInfo');
        getProfileInfoMock.mockReturnValue(profileInfo);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should get competencies from cache', () => {
        const nonOptional = { id: 1, optional: false } as Competency;
        const optional = { id: 2, optional: true } as Competency;
        const getCourseSpy = vi.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: [nonOptional, optional] });
        const getAllForCourseSpy = vi.spyOn(courseCompetencyService, 'getAllForCourse');

        fixture.detectChanges();

        const selector = fixture.debugElement.nativeElement.querySelector('#competency-selector');
        expect(component.selectedCompetencyLinks).toBeUndefined();
        expect(getCourseSpy).toHaveBeenCalledOnce();
        expect(getAllForCourseSpy).not.toHaveBeenCalled();
        expect(component.isLoading()).toBeFalsy();
        expect(component.competencyLinks()).toHaveLength(2);
        expect(selector).not.toBeNull();
    });

    it('should get competencies from service', () => {
        const nonOptional = { id: 1, optional: false } as Competency;
        const optional = { id: 2, optional: true } as Competency;
        const getCourseSpy = vi.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: undefined });
        const getAllForCourseSpy = vi.spyOn(courseCompetencyService, 'getAllForCourse').mockReturnValue(of(new HttpResponse({ body: [nonOptional, optional] })));

        fixture.detectChanges();

        expect(getCourseSpy).toHaveBeenCalledOnce();
        expect(getAllForCourseSpy).toHaveBeenCalledOnce();
        expect(component.isLoading()).toBeFalsy();
        expect(component.competencyLinks()).toHaveLength(2);
        expect(component.competencyLinks()?.first()?.competency?.course).toBeUndefined();
        expect(component.competencyLinks()?.first()?.competency?.userProgress).toBeUndefined();
    });

    it('should set disabled when error during loading', () => {
        const getCourseSpy = vi.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: undefined });
        const getAllForCourseSpy = vi.spyOn(courseCompetencyService, 'getAllForCourse').mockReturnValue(throwError(() => ({ status: 404 })));

        fixture.detectChanges();

        expect(getCourseSpy).toHaveBeenCalledOnce();
        expect(getAllForCourseSpy).toHaveBeenCalledOnce();
        expect(component.isLoading()).toBeFalsy();
        expect(component.disabled()).toBeTruthy();
    });

    it('should be hidden when no competencies', () => {
        const getCourseSpy = vi.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: [] });
        const getAllForCourseSpy = vi.spyOn(courseCompetencyService, 'getAllForCourse').mockReturnValue(of(new HttpResponse({ body: [] })));

        fixture.detectChanges();

        const select = fixture.debugElement.query(By.css('select'));
        expect(getCourseSpy).toHaveBeenCalledOnce();
        expect(getAllForCourseSpy).toHaveBeenCalledOnce();
        expect(component.isLoading()).toBeFalsy();
        expect(component.competencyLinks()).toHaveLength(0);
        expect(select).toBeNull();
    });

    it('should select competencies when value is written', () => {
        vi.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: [{ id: 1, title: 'test' } as Competency] });

        fixture.detectChanges();

        component.writeValue([new CompetencyLearningObjectLink({ id: 1, title: 'other' } as Competency, 1)]);
        expect(component.selectedCompetencyLinks).toHaveLength(1);
        expect(component.selectedCompetencyLinks?.first()?.competency?.title).toBe('test');
    });

    it('should update link weight when value is written', () => {
        vi.spyOn(courseStorageService, 'getCourse').mockReturnValue({
            competencies: [{ id: 1, title: 'test' } as Competency, { id: 2, title: 'testAgain' } as Prerequisite, { id: 3, title: 'testMore' } as Competency],
        });

        fixture.detectChanges();

        component.writeValue([
            new CompetencyLearningObjectLink({ id: 1, title: 'other' } as Competency, 0.5),
            new CompetencyLearningObjectLink({ id: 3, title: 'otherMore' } as Competency, 1),
        ]);
        expect(component.selectedCompetencyLinks).toHaveLength(2);
        expect(component.selectedCompetencyLinks?.first()?.weight).toBe(0.5);
        expect(component.selectedCompetencyLinks?.last()?.weight).toBe(1);
    });

    it('should trigger change detection after loading competencies', () => {
        vi.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: undefined });

        fixture.detectChanges();
    });

    it('should select / unselect competencies', () => {
        const competency1 = { id: 1, optional: false } as Competency;
        const competency2 = { id: 2, optional: true } as Competency;
        const competency3 = { id: 3, optional: false } as Competency;
        vi.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: [competency1, competency2, competency3] });

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
        component.checkboxStates.set({});
        const registerSpy = vi.fn();
        component.registerOnChange(registerSpy);
        component.toggleCompetency(new CompetencyLearningObjectLink({ id: 1 }, 1));
        expect(registerSpy).toHaveBeenCalled();
    });

    it('should set disabled state', () => {
        component.disabled.set(true);
        component.setDisabledState?.(false);
        expect(component.disabled()).toBeFalsy();
    });

    describe('refreshWithLinks', () => {
        const competency1 = { id: 1, title: 'first' } as Competency;
        const competency2 = { id: 2, title: 'second' } as Competency;

        it('should not emit valueChange (regression: infinite valueChange loop with a parent that refreshes on valueChange)', () => {
            vi.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: [competency1, competency2] });
            fixture.detectChanges();
            const emitSpy = vi.spyOn(component.valueChange, 'emit');

            component.refreshWithLinks([new CompetencyLearningObjectLink(competency1, 0.5)]);

            expect(emitSpy).not.toHaveBeenCalled();
        });

        it('should merge new competencies and update selection, checkbox states and the form control', () => {
            vi.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: [competency1, competency2] });
            fixture.detectChanges();
            const onChangeSpy = vi.fn();
            component.registerOnChange(onChangeSpy);
            const createdCompetency = { id: 3, title: 'created by the checklist' } as Competency;

            component.refreshWithLinks([new CompetencyLearningObjectLink(competency1, 0.5), new CompetencyLearningObjectLink(createdCompetency, 1)]);

            expect(component.competencyLinks()).toHaveLength(3);
            expect(component.selectedCompetencyLinks).toHaveLength(2);
            expect(component.selectedCompetencyLinks?.first()?.weight).toBe(0.5);
            expect(component.checkboxStates()).toEqual({ 1: true, 2: false, 3: true });
            expect(onChangeSpy).toHaveBeenCalledWith(component.selectedCompetencyLinks);
        });

        it('should keep the AI provenance of refreshed links when the selection is edited afterwards', () => {
            vi.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: [competency1, competency2] });
            fixture.detectChanges();
            const emitSpy = vi.spyOn(component.valueChange, 'emit');

            component.refreshWithLinks([new CompetencyLearningObjectLink(competency1, 0.5, true)]);
            const competency2Link = component.competencyLinks()!.find((link) => link.competency?.id === competency2.id)!;
            component.toggleCompetency(competency2Link);

            const emittedLinks = emitSpy.mock.lastCall![0]!;
            expect(emittedLinks.find((link) => link.competency?.id === competency1.id)?.generatedByAi).toBe(true);
            expect(emittedLinks.find((link) => link.competency?.id === competency2.id)?.generatedByAi).toBe(false);
        });

        it('should mark a link as manual when an AI-selected competency is ticked again by hand', () => {
            vi.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: [competency1, competency2] });
            fixture.detectChanges();

            component.refreshWithLinks([new CompetencyLearningObjectLink(competency1, 0.5, true)]);
            const competency1Link = component.competencyLinks()!.find((link) => link.competency?.id === competency1.id)!;
            component.toggleCompetency(competency1Link);
            component.toggleCompetency(competency1Link);

            expect(component.selectedCompetencyLinks?.first()?.generatedByAi).toBe(false);
        });

        it('should apply links that arrived while loading without emitting valueChange (regression: infinite valueChange loop)', () => {
            const competenciesLoaded = new Subject<HttpResponse<CourseCompetency[]>>();
            vi.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: undefined });
            vi.spyOn(courseCompetencyService, 'getAllForCourse').mockReturnValue(competenciesLoaded.asObservable());
            fixture.detectChanges();
            const emitSpy = vi.spyOn(component.valueChange, 'emit');

            component.refreshWithLinks([new CompetencyLearningObjectLink(competency1, 0.5)]);
            competenciesLoaded.next(new HttpResponse({ body: [competency1, competency2] }));

            expect(component.selectedCompetencyLinks).toHaveLength(1);
            expect(component.checkboxStates()).toEqual({ 1: true, 2: false });
            expect(emitSpy).not.toHaveBeenCalled();
        });
    });

    // Additional test suite for exercise creation integration
    describe('Exercise Creation Integration', () => {
        it('should emit value changes when competency is toggled', () => {
            const competency = { id: 1, title: 'Test Competency', optional: false } as Competency;
            vi.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: [competency] });

            const emitSpy = vi.spyOn(component.valueChange, 'emit');

            fixture.detectChanges();

            component.toggleCompetency(new CompetencyLearningObjectLink(competency, 1));

            expect(emitSpy).toHaveBeenCalled();
        });
    });
});
