import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Course } from 'app/entities/course.model';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { ModelingExamSummaryComponent } from 'app/exam/participate/summary/exercises/modeling-exam-summary/modeling-exam-summary.component';
import { ModelingEditorComponent } from 'app/exercises/modeling/shared/modeling-editor.component';
import * as chai from 'chai';
import { MockComponent } from 'ng-mocks';
import * as sinonChai from 'sinon-chai';
import { ExamNavigationBarComponent } from 'app/exam/participate/exam-navigation-bar/exam-navigation-bar.component';
import { ArtemisTestModule } from '../../../test.module';

chai.use(sinonChai);
const expect = chai.expect;

describe('Exam Navigation Bar Component', () => {
    let fixture: ComponentFixture<ExamNavigationBarComponent>;
    let comp: ExamNavigationBarComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ExamNavigationBarComponent],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamNavigationBarComponent);
                comp = fixture.componentInstance;
            });
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(ModelingExamSummaryComponent).to.be.ok;
    });
});
