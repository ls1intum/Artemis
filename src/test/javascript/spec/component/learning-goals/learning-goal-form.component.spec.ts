import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { NgbDropdownModule, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { LearningGoalFormComponent, LearningGoalFormData } from 'app/course/learning-goals/learning-goal-form/learning-goal-form.component';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { LearningGoal, LearningGoalTaxonomy } from 'app/entities/learningGoal.model';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { KeysPipe } from 'app/shared/pipes/keys.pipe';
import { ArtemisTestModule } from '../../test.module';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { IncludedInOverallScore } from 'app/entities/exercise.model';
import { Exercise } from 'app/entities/exercise.model';

describe('LearningGoalFormComponent', () => {
    let learningGoalFormComponentFixture: ComponentFixture<LearningGoalFormComponent>;
    let learningGoalFormComponent: LearningGoalFormComponent;

    let translateService: TranslateService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ReactiveFormsModule, NgbDropdownModule, MockModule(NgbTooltipModule)],
            declarations: [LearningGoalFormComponent, MockPipe(ArtemisTranslatePipe), MockPipe(KeysPipe)],
            providers: [MockProvider(LearningGoalService), MockProvider(LectureUnitService), { provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                learningGoalFormComponentFixture = TestBed.createComponent(LearningGoalFormComponent);
                learningGoalFormComponent = learningGoalFormComponentFixture.componentInstance;
            });
        translateService = TestBed.inject(TranslateService);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        learningGoalFormComponentFixture.detectChanges();
        expect(learningGoalFormComponent).toBeDefined();
    });

    it('should submit valid form', fakeAsync(() => {
        // stubbing learning goal service for asynchronous validator
        const learningGoalService = TestBed.inject(LearningGoalService);

        const learningGoalOfResponse = new LearningGoal();
        learningGoalOfResponse.id = 1;
        learningGoalOfResponse.title = 'test';

        const response: HttpResponse<LearningGoal[]> = new HttpResponse({
            body: [learningGoalOfResponse],
            status: 200,
        });

        const getAllForCourseSpy = jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(of(response));

        learningGoalFormComponentFixture.detectChanges();

        const exampleTitle = 'uniqueName';
        learningGoalFormComponent.titleControl!.setValue(exampleTitle);
        const exampleDescription = 'lorem ipsum';
        learningGoalFormComponent.descriptionControl!.setValue(exampleDescription);
        const exampleLectureUnit = new TextUnit();
        exampleLectureUnit.id = 1;

        const exampleLecture = new Lecture();
        exampleLecture.id = 1;
        exampleLecture.lectureUnits = [exampleLectureUnit];

        learningGoalFormComponent.selectLectureInDropdown(exampleLecture);
        learningGoalFormComponentFixture.detectChanges();
        // selecting the lecture unit in the table
        const lectureUnitRow = learningGoalFormComponentFixture.debugElement.nativeElement.querySelector('.lectureUnitRow');
        lectureUnitRow.click();
        learningGoalFormComponentFixture.detectChanges();
        tick(250); // async validator fires after 250ms and fully filled in form should now be valid!
        expect(learningGoalFormComponent.form.valid).toBeTrue();
        expect(getAllForCourseSpy).toHaveBeenCalledOnce();
        const submitFormSpy = jest.spyOn(learningGoalFormComponent, 'submitForm');
        const submitFormEventSpy = jest.spyOn(learningGoalFormComponent.formSubmitted, 'emit');

        const submitButton = learningGoalFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();

        learningGoalFormComponentFixture.whenStable().then(() => {
            expect(submitFormSpy).toHaveBeenCalledOnce();
            expect(submitFormEventSpy).toHaveBeenCalledOnce();
        });
    }));

    it('should detect disable optional selection if scored exercise exists', fakeAsync(() => {
        // stubbing learning goal service for validator
        const learningGoalService = TestBed.inject(LearningGoalService);

        const learningGoalOfGetAllForCourseResponse = new LearningGoal();
        learningGoalOfGetAllForCourseResponse.id = 1;
        learningGoalOfGetAllForCourseResponse.title = 'test';

        const getAllForCourseResponse: HttpResponse<LearningGoal[]> = new HttpResponse({
            body: [learningGoalOfGetAllForCourseResponse],
            status: 200,
        });

        jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(of(getAllForCourseResponse));

        learningGoalFormComponentFixture.detectChanges();

        const exampleTitle = 'uniqueName';
        learningGoalFormComponent.titleControl!.setValue(exampleTitle);
        const exampleDescription = 'lorem ipsum';
        learningGoalFormComponent.descriptionControl!.setValue(exampleDescription);

        const exercise = { id: 1, includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY } as Exercise;

        const learningGoalOfFindByIdResponse = new LearningGoal();
        learningGoalOfGetAllForCourseResponse.id = 2;
        learningGoalOfFindByIdResponse.exercises = [exercise];

        const findByIdResponse: HttpResponse<LearningGoal> = new HttpResponse({
            body: learningGoalOfFindByIdResponse,
            status: 200,
        });
        jest.spyOn(learningGoalService, 'findById').mockReturnValue(of(findByIdResponse));

        learningGoalFormComponent.courseId = 1;
        learningGoalFormComponent.formData.id = 2;
        learningGoalFormComponent.isEditMode = true;

        learningGoalFormComponentFixture.detectChanges();

        tick(250); // async validator fires after 250ms and fully filled in form should now be valid!
        expect(learningGoalFormComponent.canBeOptional()).toBeFalse();
        const optionalInput = learningGoalFormComponentFixture.debugElement.nativeElement.querySelector('#optional');
        learningGoalFormComponentFixture.whenStable().then(() => {
            expect(optionalInput.disabled).toBeTrue();
        });
    }));

    it('should correctly set form values in edit mode', () => {
        learningGoalFormComponent.isEditMode = true;
        const textUnit = new TextUnit();
        textUnit.id = 1;
        const formData: LearningGoalFormData = {
            id: 1,
            title: 'test',
            description: 'lorem ipsum',
            connectedLectureUnits: [textUnit],
            taxonomy: LearningGoalTaxonomy.ANALYZE,
            optional: true,
        };

        // stubbing learning goal service for validator
        const learningGoalService = TestBed.inject(LearningGoalService);
        const findByIdResponse: HttpResponse<LearningGoal> = new HttpResponse({
            body: new LearningGoal(),
            status: 200,
        });
        jest.spyOn(learningGoalService, 'findById').mockReturnValue(of(findByIdResponse));

        learningGoalFormComponentFixture.detectChanges();
        learningGoalFormComponent.formData = formData;
        learningGoalFormComponent.ngOnChanges();

        expect(learningGoalFormComponent.titleControl?.value).toEqual(formData.title);
        expect(learningGoalFormComponent.descriptionControl?.value).toEqual(formData.description);
        expect(learningGoalFormComponent.optionalControl?.value).toEqual(formData.optional);
        expect(learningGoalFormComponent.selectedLectureUnitsInTable).toEqual(formData.connectedLectureUnits);
    });

    it.each(['title', 'description'])('should suggest taxonomy when input is changed', (inputField: string) => {
        const suggestTaxonomySpy = jest.spyOn(learningGoalFormComponent, 'suggestTaxonomies');
        const translateSpy = jest.spyOn(translateService, 'instant').mockImplementation((key) => {
            switch (key) {
                case 'artemisApp.learningGoal.keywords.remember':
                    return 'Something';
                case 'artemisApp.learningGoal.keywords.understand':
                    return 'invent, build';
                default:
                    return key;
            }
        });
        learningGoalFormComponentFixture.detectChanges();

        const input = learningGoalFormComponentFixture.nativeElement.querySelector(`#${inputField}`);
        input.value = 'Building a tool: create a plan and implement something!';
        input.dispatchEvent(new Event('input'));

        expect(suggestTaxonomySpy).toHaveBeenCalledOnce();
        expect(translateSpy).toHaveBeenCalledTimes(12);
        expect(learningGoalFormComponent.suggestedTaxonomies).toEqual(['artemisApp.learningGoal.taxonomies.remember', 'artemisApp.learningGoal.taxonomies.understand']);
    });
});
