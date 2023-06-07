import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { NgbDropdownModule } from '@ng-bootstrap/ng-bootstrap';
import { CompetencyFormComponent, CompetencyFormData } from 'app/course/competencies/competency-form/competency-form.component';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { Competency, CompetencyTaxonomy } from 'app/entities/competency.model';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { KeysPipe } from 'app/shared/pipes/keys.pipe';
import { ArtemisTestModule } from '../../test.module';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('CompetencyFormComponent', () => {
    let competencyFormComponentFixture: ComponentFixture<CompetencyFormComponent>;
    let competencyFormComponent: CompetencyFormComponent;

    let translateService: TranslateService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ReactiveFormsModule, NgbDropdownModule],
            declarations: [CompetencyFormComponent, MockPipe(ArtemisTranslatePipe), MockPipe(KeysPipe)],
            providers: [MockProvider(CompetencyService), MockProvider(LectureUnitService), { provide: TranslateService, useClass: MockTranslateService }],
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

        const competencyOfResponse = new Competency();
        competencyOfResponse.id = 1;
        competencyOfResponse.title = 'test';

        const response: HttpResponse<Competency[]> = new HttpResponse({
            body: [competencyOfResponse],
            status: 200,
        });

        const getAllForCourseSpy = jest.spyOn(competencyService, 'getAllForCourse').mockReturnValue(of(response));

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

        competencyFormComponent.selectLectureInDropdown(exampleLecture);
        competencyFormComponentFixture.detectChanges();
        // selecting the lecture unit in the table
        const lectureUnitRow = competencyFormComponentFixture.debugElement.nativeElement.querySelector('.lectureUnitRow');
        lectureUnitRow.click();
        competencyFormComponentFixture.detectChanges();
        tick(250); // async validator fires after 250ms and fully filled in form should now be valid!
        expect(competencyFormComponent.form.valid).toBeTrue();
        expect(getAllForCourseSpy).toHaveBeenCalledOnce();
        const submitFormSpy = jest.spyOn(competencyFormComponent, 'submitForm');
        const submitFormEventSpy = jest.spyOn(competencyFormComponent.formSubmitted, 'emit');

        const submitButton = competencyFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();

        competencyFormComponentFixture.whenStable().then(() => {
            expect(submitFormSpy).toHaveBeenCalledOnce();
            expect(submitFormEventSpy).toHaveBeenCalledOnce();
        });
    }));

    it('should correctly set form values in edit mode', () => {
        competencyFormComponent.isEditMode = true;
        const textUnit = new TextUnit();
        textUnit.id = 1;
        const formData: CompetencyFormData = {
            id: 1,
            title: 'test',
            description: 'lorem ipsum',
            connectedLectureUnits: [textUnit],
            taxonomy: CompetencyTaxonomy.ANALYZE,
        };
        competencyFormComponentFixture.detectChanges();
        competencyFormComponent.formData = formData;
        competencyFormComponent.ngOnChanges();

        expect(competencyFormComponent.titleControl?.value).toEqual(formData.title);
        expect(competencyFormComponent.descriptionControl?.value).toEqual(formData.description);
        expect(competencyFormComponent.selectedLectureUnitsInTable).toEqual(formData.connectedLectureUnits);
    });

    it.each(['title', 'description'])('should suggest taxonomy when input is changed', (inputField: string) => {
        const suggestTaxonomySpy = jest.spyOn(competencyFormComponent, 'suggestTaxonomies');
        const translateSpy = jest.spyOn(translateService, 'instant').mockImplementation((key) => {
            switch (key) {
                case 'artemisApp.competency.keywords.remember':
                    return 'Something';
                case 'artemisApp.competency.keywords.understand':
                    return 'invent, build';
                default:
                    return key;
            }
        });
        competencyFormComponentFixture.detectChanges();

        const input = competencyFormComponentFixture.nativeElement.querySelector(`#${inputField}`);
        input.value = 'Building a tool: create a plan and implement something!';
        input.dispatchEvent(new Event('input'));

        expect(suggestTaxonomySpy).toHaveBeenCalledOnce();
        expect(translateSpy).toHaveBeenCalledTimes(12);
        expect(competencyFormComponent.suggestedTaxonomies).toEqual(['artemisApp.competency.taxonomies.remember', 'artemisApp.competency.taxonomies.understand']);
    });
});
