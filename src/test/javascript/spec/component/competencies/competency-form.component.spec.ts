import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync, flush, tick } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { NgbDropdownModule } from '@ng-bootstrap/ng-bootstrap';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { Competency, CompetencyTaxonomy } from 'app/entities/competency.model';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { MockComponent, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { CompetencyFormComponent } from 'app/course/competencies/forms/competency/competency-form.component';
import { CourseCompetencyFormData } from 'app/course/competencies/forms/course-competency-form.component';
import { By } from '@angular/platform-browser';
import { CommonCourseCompetencyFormComponent } from 'app/course/competencies/forms/common-course-competency-form.component';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';

describe('CompetencyFormComponent', () => {
    let competencyFormComponentFixture: ComponentFixture<CompetencyFormComponent>;
    let competencyFormComponent: CompetencyFormComponent;

    let translateService: TranslateService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CompetencyFormComponent, ArtemisTestModule, ReactiveFormsModule, NgbDropdownModule],
            declarations: [],
            providers: [MockProvider(CompetencyService), MockProvider(LectureUnitService), { provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideModule(ArtemisMarkdownEditorModule, {
                remove: { exports: [MarkdownEditorMonacoComponent] },
                add: { exports: [MockComponent(MarkdownEditorMonacoComponent)], declarations: [MockComponent(MarkdownEditorMonacoComponent)] },
            })
            .compileComponents()
            .then(() => {
                competencyFormComponentFixture = TestBed.createComponent(CompetencyFormComponent);
                competencyFormComponent = competencyFormComponentFixture.componentInstance;
            });
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
        const competencyService = TestBed.inject(CompetencyService);
        const getAllTitlesSpy = jest.spyOn(competencyService, 'getCourseCompetencyTitles').mockReturnValue(of(new HttpResponse({ body: ['test'], status: 200 })));

        const competencyOfResponse: Competency = { id: 1, title: 'test' };

        const response: HttpResponse<Competency[]> = new HttpResponse({
            body: [competencyOfResponse],
            status: 200,
        });

        jest.spyOn(competencyService, 'getAllForCourse').mockReturnValue(of(response));

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
        competencyFormComponent.isEditMode = true;
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
        competencyFormComponent.formData = formData;
        competencyFormComponent.ngOnChanges();

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
        const competencyService = TestBed.inject(CompetencyService);
        const existingTitles = ['nameExisting'];
        jest.spyOn(competencyService, 'getCourseCompetencyTitles').mockReturnValue(of(new HttpResponse({ body: existingTitles, status: 200 })));
        competencyFormComponent.isEditMode = true;
        competencyFormComponent.formData.title = 'initialName';

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
