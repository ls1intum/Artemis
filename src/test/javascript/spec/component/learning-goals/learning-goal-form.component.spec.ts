import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { NgbDropdownModule } from '@ng-bootstrap/ng-bootstrap';
import { LearningGoalFormComponent, LearningGoalFormData } from 'app/course/learning-goals/learning-goal-form/learning-goal-form.component';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { LearningGoal, LearningGoalTaxonomy } from 'app/entities/learningGoal.model';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { KeysPipe } from 'app/shared/pipes/keys.pipe';
import { ArtemisTestModule } from '../../test.module';

describe('LearningGoalFormComponent', () => {
    let learningGoalFormComponentFixture: ComponentFixture<LearningGoalFormComponent>;
    let learningGoalFormComponent: LearningGoalFormComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ReactiveFormsModule, NgbDropdownModule],
            declarations: [LearningGoalFormComponent, MockPipe(ArtemisTranslatePipe), MockPipe(KeysPipe)],
            providers: [MockProvider(LearningGoalService), MockProvider(LectureUnitService)],
        })
            .compileComponents()
            .then(() => {
                learningGoalFormComponentFixture = TestBed.createComponent(LearningGoalFormComponent);
                learningGoalFormComponent = learningGoalFormComponentFixture.componentInstance;
            });
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
        };
        learningGoalFormComponentFixture.detectChanges();
        learningGoalFormComponent.formData = formData;
        learningGoalFormComponent.ngOnChanges();

        expect(learningGoalFormComponent.titleControl?.value).toEqual(formData.title);
        expect(learningGoalFormComponent.descriptionControl?.value).toEqual(formData.description);
        expect(learningGoalFormComponent.selectedLectureUnitsInTable).toEqual(formData.connectedLectureUnits);
    });
});
