import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync, flush, tick } from '@angular/core/testing';
import { Competency, CompetencyTaxonomy } from 'app/atlas/shared/entities/competency.model';
import { TextUnit } from 'app/lecture/shared/entities/lecture-unit/textUnit.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { LectureUnitService } from 'app/lecture/manage/lecture-units/services/lecture-unit.service';
import { MockComponent, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { CompetencyFormComponent } from 'app/atlas/manage/forms/competency/competency-form.component';
import { CourseCompetencyFormData } from 'app/atlas/manage/forms/course-competency-form.component';
import { By } from '@angular/platform-browser';
import { CommonCourseCompetencyFormComponent } from 'app/atlas/manage/forms/common-course-competency-form.component';
import { CourseCompetencyService } from 'app/atlas/shared/services/course-competency.service';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('CompetencyFormComponent', () => {
    let competencyFormComponentFixture: ComponentFixture<CompetencyFormComponent>;
    let competencyFormComponent: CompetencyFormComponent;

    let translateService: TranslateService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [OwlNativeDateTimeModule, CommonCourseCompetencyFormComponent, MockComponent(MarkdownEditorMonacoComponent)],
            declarations: [],
            providers: [MockProvider(CourseCompetencyService), MockProvider(LectureUnitService), { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
            return new MockResizeObserver(callback);
        });
        competencyFormComponentFixture = TestBed.createComponent(CompetencyFormComponent);
        competencyFormComponent = competencyFormComponentFixture.componentInstance;
        competencyFormComponentFixture.componentRef.setInput('competency', new Competency());

        translateService = TestBed.inject(TranslateService);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        competencyFormComponentFixture.detectChanges();
        expect(competencyFormComponent).toBeDefined();
    });

    it('should submit valid form', fakeAsync(() => {
        // stubbing competency service for asynchronous validator
        const courseCompetencyService = TestBed.inject(CourseCompetencyService);
        const getAllTitlesSpy = jest.spyOn(courseCompetencyService, 'getCourseCompetencyTitles').mockReturnValue(of(new HttpResponse({ body: ['test'], status: 200 })));

        const competencyOfResponse: Competency = { id: 1, title: 'test' };

        const response: HttpResponse<Competency[]> = new HttpResponse({
            body: [competencyOfResponse],
            status: 200,
        });

        jest.spyOn(courseCompetencyService, 'getAllForCourse').mockReturnValue(of(response));

        competencyFormComponentFixture.detectChanges();

        const exampleTitle = 'uniqueName';
        competencyFormComponent.titleControl!.setValue(exampleTitle);
        const exampleDescription = 'lorem ipsum';
        competencyFormComponent.descriptionControl!.setValue(exampleDescription);
        const exampleLectureUnit = new TextUnit();
        exampleLectureUnit.id = 1;

        const exampleLecture = new Lecture();
        exampleLecture.id = 1;
        exampleLecture.lectureUnits = [exampleLectureUnit];

        competencyFormComponentFixture.detectChanges();
        tick(250); // async validator fires after 250ms and fully filled in form should now be valid!
        expect(competencyFormComponent.form.valid).toBeTrue();
        expect(getAllTitlesSpy).toHaveBeenCalledOnce();
        const submitFormSpy = jest.spyOn(competencyFormComponent, 'submitForm');
        const submitFormEventSpy = jest.spyOn(competencyFormComponent.formSubmitted, 'emit');

        const submitButton = competencyFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();
        competencyFormComponentFixture.detectChanges();

        flush();
        expect(submitFormSpy).toHaveBeenCalledOnce();
        expect(submitFormEventSpy).toHaveBeenCalledOnce();
        discardPeriodicTasks();
    }));

    it('should correctly set form values in edit mode', () => {
        competencyFormComponentFixture.componentRef.setInput('isEditMode', true);
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
        competencyFormComponentFixture.detectChanges();
        competencyFormComponentFixture.componentRef.setInput('formData', formData);
        competencyFormComponentFixture.detectChanges();

        expect(competencyFormComponent.titleControl?.value).toEqual(formData.title);
        expect(competencyFormComponent.descriptionControl?.value).toEqual(formData.description);
        expect(competencyFormComponent.softDueDateControl?.value).toEqual(formData.softDueDate);
        expect(competencyFormComponent.optionalControl?.value).toEqual(formData.optional);
    });

    it('should suggest taxonomy when title changes', () => {
        competencyFormComponentFixture.detectChanges();

        const commonCourseCompetencyFormComponent = competencyFormComponentFixture.debugElement.query(By.directive(CommonCourseCompetencyFormComponent)).componentInstance;
        const suggestTaxonomySpy = jest.spyOn(commonCourseCompetencyFormComponent, 'suggestTaxonomies');
        const translateSpy = createTranslateSpy();

        const titleInput = competencyFormComponentFixture.nativeElement.querySelector('#title');
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
        competencyFormComponentFixture.detectChanges();

        const commonCourseCompetencyFormComponent = competencyFormComponentFixture.debugElement.query(By.directive(CommonCourseCompetencyFormComponent)).componentInstance;
        const suggestTaxonomySpy = jest.spyOn(commonCourseCompetencyFormComponent, 'suggestTaxonomies');
        const translateSpy = createTranslateSpy();

        competencyFormComponent.updateDescriptionControl('Building a tool: create a plan and implement something!');

        expect(suggestTaxonomySpy).toHaveBeenCalledOnce();
        expect(translateSpy).toHaveBeenCalledTimes(12);
        expect(commonCourseCompetencyFormComponent.suggestedTaxonomies).toEqual([
            'artemisApp.courseCompetency.taxonomies.REMEMBER',
            'artemisApp.courseCompetency.taxonomies.UNDERSTAND',
        ]);
    });

    it('validator should verify title is unique', fakeAsync(() => {
        const existingTitles = ['nameExisting'];
        const courseCompetencyService = TestBed.inject(CourseCompetencyService);
        jest.spyOn(courseCompetencyService, 'getCourseCompetencyTitles').mockReturnValue(of(new HttpResponse({ body: existingTitles, status: 200 })));
        competencyFormComponentFixture.componentRef.setInput('isEditMode', true);
        competencyFormComponentFixture.componentRef.setInput('formData', { title: 'initialName' } as CourseCompetencyFormData);
        competencyFormComponentFixture.detectChanges();

        const titleControl = competencyFormComponent.titleControl!;
        tick(250);
        expect(titleControl.errors?.titleUnique).toBeUndefined();

        titleControl.setValue('anotherName');
        tick(250);
        expect(titleControl.errors?.titleUnique).toBeUndefined();

        titleControl.setValue('');
        tick(250);
        expect(titleControl.errors?.titleUnique).toBeUndefined();

        titleControl.setValue('nameExisting');
        tick(250);
        expect(titleControl.errors?.titleUnique).toBeDefined();
        flush();
        discardPeriodicTasks();
    }));

    function createTranslateSpy() {
        return jest.spyOn(translateService, 'instant').mockImplementation((key) => {
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
