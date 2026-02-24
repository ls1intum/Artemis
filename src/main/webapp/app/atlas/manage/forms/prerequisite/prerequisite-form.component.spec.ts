import { vi } from 'vitest';
import type { Mocked } from 'vitest';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { NgbDropdownModule } from '@ng-bootstrap/ng-bootstrap';
import { CompetencyTaxonomy } from 'app/atlas/shared/entities/competency.model';
import { TextUnit } from 'app/lecture/shared/entities/lecture-unit/textUnit.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { LectureUnitService } from 'app/lecture/manage/lecture-units/services/lecture-unit.service';
import { MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { CompetencyFormComponent } from 'app/atlas/manage/forms/competency/competency-form.component';
import { CourseCompetencyFormData } from 'app/atlas/manage/forms/course-competency-form.component';
import { By } from '@angular/platform-browser';
import { CommonCourseCompetencyFormComponent } from 'app/atlas/manage/forms/common-course-competency-form.component';
import { PrerequisiteFormComponent } from 'app/atlas/manage/forms/prerequisite/prerequisite-form.component';
import { PrerequisiteService } from 'app/atlas/manage/services/prerequisite.service';
import { Prerequisite } from 'app/atlas/shared/entities/prerequisite.model';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { CourseCompetencyService } from 'app/atlas/shared/services/course-competency.service';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ThemeService } from 'app/core/theme/shared/theme.service';
import { MockThemeService } from 'test/helpers/mocks/service/mock-theme.service';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('PrerequisiteFormComponent', () => {
    setupTestBed({ zoneless: true });
    let prerequisiteFormComponentFixture: ComponentFixture<PrerequisiteFormComponent>;
    let prerequisiteFormComponent: PrerequisiteFormComponent;

    let translateService: TranslateService;
    const prerequisiteServiceMock = { getAllForCourse: vi.fn() } as unknown as PrerequisiteService;
    const courseCompetencyServiceMock: Mocked<Pick<CourseCompetencyService, 'getCourseCompetencyTitles'>> = {
        getCourseCompetencyTitles: vi.fn(),
    };
    let originalResizeObserver: typeof ResizeObserver | undefined;

    beforeEach(() => {
        originalResizeObserver = globalThis.ResizeObserver;
        courseCompetencyServiceMock.getCourseCompetencyTitles.mockReturnValue(of(new HttpResponse({ body: [] })));

        TestBed.configureTestingModule({
            imports: [CompetencyFormComponent, ReactiveFormsModule, NgbDropdownModule, OwlNativeDateTimeModule],
            providers: [
                { provide: PrerequisiteService, useValue: prerequisiteServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: CourseCompetencyService, useValue: courseCompetencyServiceMock },
                MockProvider(LectureUnitService),
                { provide: ThemeService, useClass: MockThemeService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        prerequisiteFormComponentFixture = TestBed.createComponent(PrerequisiteFormComponent);
        prerequisiteFormComponent = prerequisiteFormComponentFixture.componentInstance;
        prerequisiteFormComponentFixture.componentRef.setInput('prerequisite', new Prerequisite());
        translateService = TestBed.inject(TranslateService);
        globalThis.ResizeObserver = MockResizeObserver as unknown as typeof ResizeObserver;
    });

    afterEach(() => {
        globalThis.ResizeObserver = originalResizeObserver as typeof ResizeObserver;
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        prerequisiteFormComponentFixture.detectChanges();
        expect(prerequisiteFormComponent).toBeDefined();
    });

    it('should submit valid form', () => {
        vi.useFakeTimers();
        try {
            // stubbing prerequisite service for asynchronous validator
            const getCourseCompetencyTitlesSpy = vi
                .spyOn(courseCompetencyServiceMock, 'getCourseCompetencyTitles')
                .mockReturnValue(of(new HttpResponse({ body: ['test'], status: 200 })));

            const competencyOfResponse: Prerequisite = { id: 1, title: 'test' };

            const response: HttpResponse<Prerequisite[]> = new HttpResponse({
                body: [competencyOfResponse],
                status: 200,
            });

            vi.spyOn(prerequisiteServiceMock, 'getAllForCourse').mockReturnValue(of(response));

            prerequisiteFormComponentFixture.detectChanges();

            const exampleTitle = 'uniqueName';
            prerequisiteFormComponent.titleControl!.setValue(exampleTitle);
            const exampleDescription = 'lorem ipsum';
            prerequisiteFormComponent.descriptionControl!.setValue(exampleDescription);
            const exampleLectureUnit = new TextUnit();
            exampleLectureUnit.id = 1;

            const exampleLecture = new Lecture();
            exampleLecture.id = 1;
            exampleLecture.lectureUnits = [exampleLectureUnit];

            prerequisiteFormComponentFixture.detectChanges();
            vi.advanceTimersByTime(250); // async validator fires after 250ms and fully filled in form should now be valid!
            expect(prerequisiteFormComponent.form.valid).toBeTruthy();
            expect(getCourseCompetencyTitlesSpy).toHaveBeenCalledOnce();
            const submitFormSpy = vi.spyOn(prerequisiteFormComponent, 'submitForm');
            const submitFormEventSpy = vi.spyOn(prerequisiteFormComponent.formSubmitted, 'emit');

            const submitButton = prerequisiteFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
            submitButton.click();
            prerequisiteFormComponentFixture.detectChanges();

            vi.runOnlyPendingTimers();
            expect(submitFormSpy).toHaveBeenCalledOnce();
            expect(submitFormEventSpy).toHaveBeenCalledOnce();
        } finally {
            vi.useRealTimers();
        }
    });

    it('should correctly set form values in edit mode', () => {
        prerequisiteFormComponentFixture.componentRef.setInput('isEditMode', true);
        const textUnit = new TextUnit();
        textUnit.id = 1;
        const formData: CourseCompetencyFormData = {
            id: 1,
            title: 'test',
            description: 'lorem ipsum',
            softDueDate: dayjs(),
            taxonomy: CompetencyTaxonomy.ANALYZE,
            optional: true,
        };
        prerequisiteFormComponentFixture.componentRef.setInput('formData', formData);
        prerequisiteFormComponentFixture.detectChanges();

        expect(prerequisiteFormComponent.titleControl?.value).toEqual(formData.title);
        expect(prerequisiteFormComponent.descriptionControl?.value).toEqual(formData.description);
        expect(prerequisiteFormComponent.softDueDateControl?.value).toEqual(formData.softDueDate);
        expect(prerequisiteFormComponent.optionalControl?.value).toEqual(formData.optional);
    });

    it('should suggest taxonomy when title changes', () => {
        prerequisiteFormComponentFixture.detectChanges();

        const commonCourseCompetencyFormComponent = prerequisiteFormComponentFixture.debugElement.query(By.directive(CommonCourseCompetencyFormComponent)).componentInstance;
        const suggestTaxonomySpy = vi.spyOn(commonCourseCompetencyFormComponent, 'suggestTaxonomies');
        const translateSpy = createTranslateSpy();

        const titleInput = prerequisiteFormComponentFixture.nativeElement.querySelector('#title');
        titleInput.value = 'Building a tool: create a plan and implement something!';
        titleInput.dispatchEvent(new Event('input'));

        expect(suggestTaxonomySpy).toHaveBeenCalledOnce();
        expect(translateSpy).toHaveBeenCalledTimes(12);
        expect(commonCourseCompetencyFormComponent.suggestedTaxonomies).toEqual([
            'artemisApp.courseCompetency.taxonomies.REMEMBER',
            'artemisApp.courseCompetency.taxonomies.UNDERSTAND',
        ]);
    });

    it('should suggest taxonomy when description changes', () => {
        prerequisiteFormComponentFixture.detectChanges();

        const commonCourseCompetencyFormComponent = prerequisiteFormComponentFixture.debugElement.query(By.directive(CommonCourseCompetencyFormComponent)).componentInstance;
        const suggestTaxonomySpy = vi.spyOn(commonCourseCompetencyFormComponent, 'suggestTaxonomies');
        const translateSpy = createTranslateSpy();

        prerequisiteFormComponent.updateDescriptionControl('Building a tool: create a plan and implement something!');

        expect(suggestTaxonomySpy).toHaveBeenCalledOnce();
        expect(translateSpy).toHaveBeenCalledTimes(12);
        expect(commonCourseCompetencyFormComponent.suggestedTaxonomies).toEqual([
            'artemisApp.courseCompetency.taxonomies.REMEMBER',
            'artemisApp.courseCompetency.taxonomies.UNDERSTAND',
        ]);
    });

    it('validator should verify title is unique', () => {
        vi.useFakeTimers();
        try {
            const existingTitles = ['nameExisting'];
            vi.spyOn(courseCompetencyServiceMock, 'getCourseCompetencyTitles').mockReturnValue(
                of(
                    new HttpResponse({
                        body: existingTitles,
                        status: 200,
                    }),
                ),
            );
            prerequisiteFormComponentFixture.componentRef.setInput('isEditMode', true);
            prerequisiteFormComponentFixture.componentRef.setInput('formData', { title: 'initialName' } as CourseCompetencyFormData);
            prerequisiteFormComponentFixture.detectChanges();

            const titleControl = prerequisiteFormComponent.titleControl!;
            vi.advanceTimersByTime(250);
            expect(titleControl.errors?.titleUnique).toBeUndefined();

            titleControl.setValue('anotherName');
            vi.advanceTimersByTime(250);
            expect(titleControl.errors?.titleUnique).toBeUndefined();

            titleControl.setValue('');
            vi.advanceTimersByTime(250);
            expect(titleControl.errors?.titleUnique).toBeUndefined();

            titleControl.setValue('nameExisting');
            vi.advanceTimersByTime(250);
            expect(titleControl.errors?.titleUnique).toBeDefined();
        } finally {
            vi.useRealTimers();
        }
    });

    function createTranslateSpy() {
        return vi.spyOn(translateService, 'instant').mockImplementation((key) => {
            switch (key) {
                case 'artemisApp.courseCompetency.keywords.REMEMBER':
                    return 'Something';
                case 'artemisApp.courseCompetency.keywords.UNDERSTAND':
                    return 'invent, build';
                default:
                    return key;
            }
        });
    }
});
