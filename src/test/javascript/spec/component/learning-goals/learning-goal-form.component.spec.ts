import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { NgbDropdownModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { LearningGoalFormComponent, LearningGoalFormData } from 'app/course/learning-goals/learning-goal-form/learning-goal-form.component';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe.ts';
import * as chai from 'chai';
import { JhiAlertService } from 'ng-jhipster';
import { MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import * as sinon from 'sinon';
import * as sinonChai from 'sinon-chai';

chai.use(sinonChai);
const expect = chai.expect;
describe('LearningGoalFormComponent', () => {
    let learningGoalFormComponentFixture: ComponentFixture<LearningGoalFormComponent>;
    let learningGoalFormComponent: LearningGoalFormComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ReactiveFormsModule, FormsModule, NgbDropdownModule],
            declarations: [LearningGoalFormComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(LearningGoalService), MockProvider(LectureUnitService), MockProvider(JhiAlertService), MockProvider(TranslateService)],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                learningGoalFormComponentFixture = TestBed.createComponent(LearningGoalFormComponent);
                learningGoalFormComponent = learningGoalFormComponentFixture.componentInstance;
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize', () => {
        learningGoalFormComponentFixture.detectChanges();
        expect(learningGoalFormComponent).to.be.ok;
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

        const getAllForCourseStub = sinon.stub(learningGoalService, 'getAllForCourse').returns(of(response));

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
        expect(learningGoalFormComponent.form.valid).to.be.true;
        expect(getAllForCourseStub).to.have.been.called;
        const submitFormSpy = sinon.spy(learningGoalFormComponent, 'submitForm');
        const submitFormEventSpy = sinon.spy(learningGoalFormComponent.formSubmitted, 'emit');

        const submitButton = learningGoalFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();

        learningGoalFormComponentFixture.whenStable().then(() => {
            expect(submitFormSpy).to.have.been.called;
            expect(submitFormEventSpy).to.have.been.calledWith({
                title: exampleTitle,
                description: exampleDescription,
                connectedLectureUnits: [exampleLectureUnit],
            });
        });
    }));

    it('should correctly set form values in edit mode', () => {
        learningGoalFormComponent.isEditMode = true;
        const textUnit = new TextUnit();
        textUnit.id = 1;
        const formData: LearningGoalFormData = {
            title: 'test',
            description: 'lorem ipsum',
            connectedLectureUnits: [textUnit],
        };
        learningGoalFormComponentFixture.detectChanges();
        learningGoalFormComponent.formData = formData;
        learningGoalFormComponent.ngOnChanges();

        expect(learningGoalFormComponent.titleControl?.value).to.equal(formData.title);
        expect(learningGoalFormComponent.descriptionControl?.value).to.equal(formData.description);
        expect(learningGoalFormComponent.selectedLectureUnitsInTable).to.equal(formData.connectedLectureUnits);
    });
});
