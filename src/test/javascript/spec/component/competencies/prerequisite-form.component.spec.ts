import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync, flush, tick } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { NgbDropdownModule } from '@ng-bootstrap/ng-bootstrap';
import { CompetencyTaxonomy } from 'app/entities/competency.model';
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
import { PrerequisiteFormComponent } from 'app/course/competencies/forms/prerequisite/prerequisite-form.component';
import { PrerequisiteService } from 'app/course/competencies/prerequisite.service';
import { Prerequisite } from 'app/entities/prerequisite.model';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';

describe('PrerequisiteFormComponent', () => {
    let prerequisiteFormComponentFixture: ComponentFixture<PrerequisiteFormComponent>;
    let prerequisiteFormComponent: PrerequisiteFormComponent;

    let translateService: TranslateService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CompetencyFormComponent, ArtemisTestModule, ReactiveFormsModule, NgbDropdownModule],
            providers: [MockProvider(PrerequisiteService), MockProvider(LectureUnitService), { provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideModule(ArtemisMarkdownEditorModule, {
                remove: { exports: [MarkdownEditorMonacoComponent] },
                add: { exports: [MockComponent(MarkdownEditorMonacoComponent)], declarations: [MockComponent(MarkdownEditorMonacoComponent)] },
            })
            .compileComponents()
            .then(() => {
                prerequisiteFormComponentFixture = TestBed.createComponent(PrerequisiteFormComponent);
                prerequisiteFormComponent = prerequisiteFormComponentFixture.componentInstance;
            });
        translateService = TestBed.inject(TranslateService);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        prerequisiteFormComponentFixture.detectChanges();
        expect(prerequisiteFormComponent).toBeDefined();
    });

    it('should submit valid form', fakeAsync(() => {
        // stubbing prerequisite service for asynchronous validator
        const prerequisiteService = TestBed.inject(PrerequisiteService);
        const getAllTitlesSpy = jest.spyOn(prerequisiteService, 'getCourseCompetencyTitles').mockReturnValue(of(new HttpResponse({ body: ['test'], status: 200 })));

        const competencyOfResponse: Prerequisite = { id: 1, title: 'test' };

        const response: HttpResponse<Prerequisite[]> = new HttpResponse({
            body: [competencyOfResponse],
            status: 200,
        });

        jest.spyOn(prerequisiteService, 'getAllForCourse').mockReturnValue(of(response));

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
        tick(250); // async validator fires after 250ms and fully filled in form should now be valid!
        expect(prerequisiteFormComponent.form.valid).toBeTrue();
        expect(getAllTitlesSpy).toHaveBeenCalledOnce();
        const submitFormSpy = jest.spyOn(prerequisiteFormComponent, 'submitForm');
        const submitFormEventSpy = jest.spyOn(prerequisiteFormComponent.formSubmitted, 'emit');

        const submitButton = prerequisiteFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();
        prerequisiteFormComponentFixture.detectChanges();

        flush();
        expect(submitFormSpy).toHaveBeenCalledOnce();
        expect(submitFormEventSpy).toHaveBeenCalledOnce();
        discardPeriodicTasks();
    }));

    it('should correctly set form values in edit mode', () => {
        prerequisiteFormComponent.isEditMode = true;
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
        prerequisiteFormComponentFixture.detectChanges();
        prerequisiteFormComponent.formData = formData;
        prerequisiteFormComponent.ngOnChanges();

        expect(prerequisiteFormComponent.titleControl?.value).toEqual(formData.title);
        expect(prerequisiteFormComponent.descriptionControl?.value).toEqual(formData.description);
        expect(prerequisiteFormComponent.softDueDateControl?.value).toEqual(formData.softDueDate);
        expect(prerequisiteFormComponent.optionalControl?.value).toEqual(formData.optional);
    });

    it('should suggest taxonomy when title changes', () => {
        prerequisiteFormComponentFixture.detectChanges();

        const commonCourseCompetencyFormComponent = prerequisiteFormComponentFixture.debugElement.query(By.directive(CommonCourseCompetencyFormComponent)).componentInstance;
        const suggestTaxonomySpy = jest.spyOn(commonCourseCompetencyFormComponent, 'suggestTaxonomies');
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
        const suggestTaxonomySpy = jest.spyOn(commonCourseCompetencyFormComponent, 'suggestTaxonomies');
        const translateSpy = createTranslateSpy();

        prerequisiteFormComponent.updateDescriptionControl('Building a tool: create a plan and implement something!');

        expect(suggestTaxonomySpy).toHaveBeenCalledOnce();
        expect(translateSpy).toHaveBeenCalledTimes(12);
        expect(commonCourseCompetencyFormComponent.suggestedTaxonomies).toEqual([
            'artemisApp.courseCompetency.taxonomies.REMEMBER',
            'artemisApp.courseCompetency.taxonomies.UNDERSTAND',
        ]);
    });

    it('validator should verify title is unique', fakeAsync(() => {
        const prerequisiteService = TestBed.inject(PrerequisiteService);
        const existingTitles = ['nameExisting'];
        jest.spyOn(prerequisiteService, 'getCourseCompetencyTitles').mockReturnValue(of(new HttpResponse({ body: existingTitles, status: 200 })));
        prerequisiteFormComponent.isEditMode = true;
        prerequisiteFormComponent.formData.title = 'initialName';

        prerequisiteFormComponentFixture.detectChanges();

        const titleControl = prerequisiteFormComponent.titleControl!;
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
