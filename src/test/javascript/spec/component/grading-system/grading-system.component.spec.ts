import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GradingSystemComponent } from 'app/grading-system/grading-system.component';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import * as chai from 'chai';
import * as sinon from 'sinon';
import { SinonStub } from 'sinon';
import sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { GradeType, GradingScale } from 'app/entities/grading-scale.model';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { MockTranslateService, TranslateTestingModule } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';
import { GradingSystemInfoModalComponent } from 'app/grading-system/grading-system-info-modal/grading-system-info-modal.component';
import { FormsModule } from '@angular/forms';
import { GradeStep } from 'app/entities/grade-step.model';
import { cloneDeep } from 'lodash-es';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { TranslateService } from '@ngx-translate/core';
import { Exam } from 'app/entities/exam.model';
import { Course } from 'app/entities/course.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('Grading System Component', () => {
    let comp: GradingSystemComponent;
    let fixture: ComponentFixture<GradingSystemComponent>;
    let gradingSystemService: GradingSystemService;
    let translateService: TranslateService;
    let translateStub: SinonStub;
    let examService: ExamManagementService;

    const route = { params: of({ courseId: 1, examId: 1 }) } as any as ActivatedRoute;

    const gradeStep1: GradeStep = {
        gradeName: 'Fail',
        lowerBoundPercentage: 0,
        upperBoundPercentage: 40,
        lowerBoundInclusive: true,
        upperBoundInclusive: false,
        isPassingGrade: false,
    };
    const gradeStep2: GradeStep = {
        gradeName: 'Pass',
        lowerBoundPercentage: 40,
        upperBoundPercentage: 80,
        lowerBoundInclusive: true,
        upperBoundInclusive: false,
        isPassingGrade: true,
    };
    const gradeStep3: GradeStep = {
        gradeName: 'Excellent',
        lowerBoundPercentage: 80,
        upperBoundPercentage: 100,
        lowerBoundInclusive: true,
        upperBoundInclusive: true,
        isPassingGrade: true,
    };
    const gradeSteps = [gradeStep1, gradeStep2, gradeStep3];

    const exam = new Exam();
    exam.maxPoints = 100;
    const course = new Course();
    course.maxPoints = 100;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateTestingModule, FormsModule, RouterTestingModule.withRoutes([])],
            declarations: [
                GradingSystemComponent,
                MockComponent(AlertComponent),
                MockComponent(AlertErrorComponent),
                MockComponent(GradingSystemInfoModalComponent),
                MockDirective(DeleteButtonDirective),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(GradingSystemComponent);
        comp = fixture.componentInstance;

        gradingSystemService = TestBed.inject(GradingSystemService);
        examService = TestBed.inject(ExamManagementService);
        translateService = TestBed.inject(TranslateService);
    });

    beforeEach(() => {
        comp.gradingScale = new GradingScale();
        comp.gradingScale.gradeSteps = cloneDeep(gradeSteps);
        comp.courseId = 123;
        comp.examId = 456;
        comp.firstPassingGrade = 'Pass';
        translateStub = sinon.stub(translateService, 'instant');
    });

    afterEach(() => {
        sinon.restore();
    });

    it('should handle find response for exam', () => {
        const findGradingScaleForExamStub = sinon.stub(gradingSystemService, 'findGradingScaleForExam').returns(of(new HttpResponse<GradingScale>({ body: comp.gradingScale })));
        const findExamStub = sinon.stub(examService, 'find').returns(of(new HttpResponse<Exam>({ body: exam })));

        fixture.detectChanges();

        expect(comp).to.be.ok;
        expect(comp.isExam).to.be.true;
        expect(findGradingScaleForExamStub).to.have.been.calledOnceWithExactly(1, 1);
        expect(findExamStub).to.have.been.calledOnceWithExactly(1, 1);
        expect(comp.exam).to.equal(exam);
        expect(comp.maxPoints).to.equal(exam.maxPoints);
    });

    it('should handle find response for exam and not find a grading scale', () => {
        const findGradingScaleForExamAndReturnNotFoundStub = sinon
            .stub(gradingSystemService, 'findGradingScaleForExam')
            .returns(of(new HttpResponse<GradingScale>({ status: 404 })));

        fixture.detectChanges();

        expect(findGradingScaleForExamAndReturnNotFoundStub).to.have.been.calledOnceWithExactly(1, 1);
    });

    it('should generate default grading scale', () => {
        comp.generateDefaultGradingScale();

        expect(comp.gradingScale.gradeType).to.equal(GradeType.GRADE);
        expect(comp.firstPassingGrade).to.equal('4.0');
        expect(comp.lowerBoundInclusivity).true;
        expect(comp.gradingScale.gradeSteps.length).is.equal(13);
        comp.gradingScale.gradeSteps.forEach((gradeStep) => {
            expect(gradeStep.id).undefined;
            expect(gradeStep.gradeName).to.be.ok;
            expect(gradeStep.lowerBoundInclusive).true;
            expect(gradeStep.lowerBoundPercentage).to.be.within(0, 100);
            expect(gradeStep.upperBoundPercentage).to.be.within(0, 100);
            expect(gradeStep.lowerBoundPercentage).to.be.lessThanOrEqual(gradeStep.upperBoundPercentage);
            if (gradeStep.upperBoundPercentage === 100) {
                expect(gradeStep.upperBoundInclusive).true;
            } else {
                expect(gradeStep.upperBoundInclusive).false;
            }
            if (gradeStep.lowerBoundPercentage >= 50) {
                expect(gradeStep.isPassingGrade).true;
            }
        });
    });

    it('should delete grade step', () => {
        comp.deleteGradeStep(1);

        expect(comp.gradingScale.gradeSteps.length).to.be.equal(2);
        expect(comp.gradingScale.gradeSteps).to.not.contain(gradeStep2);
    });

    it('should create grade step', () => {
        comp.lowerBoundInclusivity = true;

        comp.createGradeStep();

        expect(comp.gradingScale.gradeSteps.length).to.be.equal(4);
        expect(comp.gradingScale.gradeSteps[3].id).to.be.undefined;
        expect(comp.gradingScale.gradeSteps[3].gradeName).to.be.equal('');
        expect(comp.gradingScale.gradeSteps[3].lowerBoundPercentage).to.be.equal(100);
        expect(comp.gradingScale.gradeSteps[3].upperBoundPercentage).to.be.equal(100);
        expect(comp.gradingScale.gradeSteps[3].isPassingGrade).to.be.equal(true);
        expect(comp.gradingScale.gradeSteps[3].lowerBoundInclusive).to.be.equal(true);
        expect(comp.gradingScale.gradeSteps[3].upperBoundInclusive).to.be.equal(true);
    });

    it('should delete grade names correctly', () => {
        comp.deleteGradeNames();

        comp.gradingScale.gradeSteps.forEach((gradeStep) => {
            expect(gradeStep.gradeName).is.equal('');
        });
    });

    it('should filter grade steps with empty names correctly', () => {
        comp.gradingScale.gradeSteps[0].gradeName = '';
        comp.gradingScale.gradeSteps[2].gradeName = '';

        const filteredGradeSteps = comp.gradeStepsWithNonemptyNames();

        expect(filteredGradeSteps.length).to.equal(1);
        expect(filteredGradeSteps[0]).to.deep.equal(gradeStep2);
    });

    it('should set passing Grades correctly', () => {
        comp.firstPassingGrade = 'Fail';

        comp.setPassingGrades(comp.gradingScale.gradeSteps);

        comp.gradingScale.gradeSteps.forEach((gradeStep) => {
            expect(gradeStep.isPassingGrade).to.be.equal(true);
        });

        comp.firstPassingGrade = '';

        comp.setPassingGrades(comp.gradingScale.gradeSteps);

        comp.gradingScale.gradeSteps.forEach((gradeStep) => {
            expect(gradeStep.isPassingGrade).to.be.equal(false);
        });
    });

    it('should determine first passing grade correctly', () => {
        comp.determineFirstPassingGrade();

        expect(comp.firstPassingGrade).to.be.equal('Pass');
    });

    it('should set inclusivity correctly', () => {
        comp.lowerBoundInclusivity = false;

        comp.setInclusivity(comp.gradingScale.gradeSteps);

        comp.gradingScale.gradeSteps.forEach((gradeStep) => {
            expect(gradeStep.upperBoundInclusive).to.be.equal(true);
            if (gradeStep.lowerBoundPercentage === 0) {
                expect(gradeStep.lowerBoundInclusive).to.be.equal(true);
            } else {
                expect(gradeStep.lowerBoundInclusive).to.be.equal(false);
            }
        });
    });

    it('should determine lower bound inclusivity correctly', () => {
        comp.setBoundInclusivity();

        expect(comp.lowerBoundInclusivity).to.be.equal(true);
    });

    it('should not delete non-existing grading scale', () => {
        comp.existingGradingScale = false;
        const gradingSystemDeleteForCourseStub = sinon.stub(gradingSystemService, 'deleteGradingScaleForCourse');
        const gradingSystemDeleteForExamStub = sinon.stub(gradingSystemService, 'deleteGradingScaleForExam');

        comp.delete();

        expect(gradingSystemDeleteForCourseStub).to.not.have.been.called;
        expect(gradingSystemDeleteForExamStub).to.not.have.been.called;
    });

    it('should delete grading scale for course', () => {
        comp.existingGradingScale = true;
        comp.isExam = false;
        comp.courseId = 123;
        const gradingSystemDeleteForCourseStub = sinon.stub(gradingSystemService, 'deleteGradingScaleForCourse').returns(of(new HttpResponse<{}>({ body: [] })));

        comp.delete();

        expect(gradingSystemDeleteForCourseStub).to.have.been.calledOnceWith(comp.courseId);
        expect(comp.existingGradingScale).to.equal(false);
    });

    it('should delete grading scale for exam', () => {
        comp.existingGradingScale = true;
        comp.isExam = true;
        const gradingSystemDeleteForExamStub = sinon.stub(gradingSystemService, 'deleteGradingScaleForExam').returns(of(new HttpResponse<{}>({ body: [] })));

        comp.delete();

        expect(gradingSystemDeleteForExamStub).to.have.been.calledOnceWith(comp.courseId, comp.examId);
        expect(comp.existingGradingScale).to.equal(false);
    });

    it('should not update grading scale', () => {
        comp.existingGradingScale = false;
        comp.isExam = false;
        comp.course = course;
        const gradingSystemServiceStub = sinon.stub(gradingSystemService, 'createGradingScaleForCourse').returns(of(new HttpResponse<GradingScale>({ body: undefined })));

        comp.save();

        expect(gradingSystemServiceStub).to.have.been.calledOnceWith(comp.courseId);
        expect(comp.existingGradingScale).to.be.false;
    });

    it('should create grading scale correctly for course', () => {
        comp.existingGradingScale = false;
        comp.course = course;
        const createdGradingScaleForCourse = comp.gradingScale;
        createdGradingScaleForCourse.gradeType = GradeType.BONUS;
        const gradingSystemCreateForCourseStub = sinon
            .stub(gradingSystemService, 'createGradingScaleForCourse')
            .returns(of(new HttpResponse<GradingScale>({ body: createdGradingScaleForCourse })));

        comp.save();

        expect(gradingSystemCreateForCourseStub).to.have.been.calledOnceWith(comp.courseId);
        expect(comp.existingGradingScale).to.equal(true);
        expect(comp.gradingScale).to.equal(createdGradingScaleForCourse);
    });

    it('should create grading scale correctly for exam', () => {
        comp.existingGradingScale = false;
        comp.isExam = true;
        comp.exam = exam;
        const createdGradingScaleForExam = comp.gradingScale;
        createdGradingScaleForExam.gradeType = GradeType.BONUS;
        const gradingSystemCreateForExamStub = sinon
            .stub(gradingSystemService, 'createGradingScaleForExam')
            .returns(of(new HttpResponse<GradingScale>({ body: createdGradingScaleForExam })));

        comp.save();

        expect(gradingSystemCreateForExamStub).to.have.been.calledOnceWith(comp.courseId, comp.examId);
        expect(comp.existingGradingScale).to.equal(true);
        expect(comp.gradingScale).to.deep.equal(createdGradingScaleForExam);
    });

    it('should update grading scale correctly for course', () => {
        comp.existingGradingScale = true;
        comp.course = course;
        const updateGradingScaleFoCourse = comp.gradingScale;
        updateGradingScaleFoCourse.gradeType = GradeType.BONUS;
        const gradingSystemUpdateForCourseStub = sinon
            .stub(gradingSystemService, 'updateGradingScaleForCourse')
            .returns(of(new HttpResponse<GradingScale>({ body: updateGradingScaleFoCourse })));

        comp.save();

        expect(gradingSystemUpdateForCourseStub).to.have.been.calledOnceWith(comp.courseId);
        expect(comp.existingGradingScale).to.equal(true);
        expect(comp.gradingScale).to.deep.equal(updateGradingScaleFoCourse);
    });

    it('should update grading scale correctly for exam', () => {
        comp.existingGradingScale = true;
        comp.isExam = true;
        comp.exam = exam;
        const updatedGradingScaleForExam = comp.gradingScale;
        updatedGradingScaleForExam.gradeType = GradeType.BONUS;
        const gradingSystemUpdateForExamStub = sinon
            .stub(gradingSystemService, 'updateGradingScaleForExam')
            .returns(of(new HttpResponse<GradingScale>({ body: updatedGradingScaleForExam })));

        comp.save();

        expect(gradingSystemUpdateForExamStub).to.have.been.calledOnceWith(comp.courseId);
        expect(comp.existingGradingScale).to.equal(true);
        expect(comp.gradingScale).to.deep.equal(updatedGradingScaleForExam);
    });

    it('should handle find response correctly', () => {
        comp.handleFindResponse(comp.gradingScale);

        expect(comp.firstPassingGrade).to.be.equal('Pass');
        expect(comp.lowerBoundInclusivity).to.be.equal(true);
        expect(comp.existingGradingScale).to.be.equal(true);
    });

    it('should validate valid grading scale correctly', () => {
        expect(comp.validGradeSteps()).to.be.true;
        expect(comp.invalidGradeStepsMessage).to.be.undefined;
    });

    it('should validate invalid grading scale with empty grade steps correctly', () => {
        comp.gradingScale.gradeSteps = [];
        translateStub.returns('empty set');

        expect(comp.validGradeSteps()).to.be.false;
        expect(comp.invalidGradeStepsMessage).to.be.equal('empty set');
        expect(translateStub).to.have.been.calledOnceWithExactly('artemisApp.gradingSystem.error.empty');
    });

    it('should validate invalid grading scale with negative max points', () => {
        comp.course = course;
        comp.maxPoints = -10;
        translateStub.returns('negative max points');

        expect(comp.validGradeSteps()).to.be.false;
        expect(comp.invalidGradeStepsMessage).to.be.equal('negative max points');
        expect(translateStub).to.have.been.calledOnceWithExactly('artemisApp.gradingSystem.error.negativeMaxPoints');
        course.maxPoints = 100;
    });

    it('should validate invalid grading scale with empty grade step fields correctly', () => {
        comp.gradingScale.gradeSteps[0].gradeName = '';
        translateStub.returns('empty field');

        expect(comp.validGradeSteps()).to.be.false;
        expect(comp.invalidGradeStepsMessage).to.be.equal('empty field');
        expect(translateStub).to.have.been.calledOnceWithExactly('artemisApp.gradingSystem.error.emptyFields');
    });

    it('should validate invalid grading scale with empty grade step point fields correctly', () => {
        comp.course = course;
        comp.maxPoints = 100;
        translateStub.returns('empty field for points');

        expect(comp.validGradeSteps()).to.be.false;
        expect(comp.invalidGradeStepsMessage).to.be.equal('empty field for points');
        expect(translateStub).to.have.been.calledOnceWithExactly('artemisApp.gradingSystem.error.emptyFields');
    });

    it('should validate invalid grading scale with invalid percentages', () => {
        comp.gradingScale.gradeSteps[0].lowerBoundPercentage = -10;
        translateStub.returns('invalid percentage');

        expect(comp.validGradeSteps()).to.be.false;
        expect(comp.invalidGradeStepsMessage).to.be.equal('invalid percentage');
        expect(translateStub).to.have.been.calledOnceWithExactly('artemisApp.gradingSystem.error.invalidMinMaxPercentages');
    });

    it('should validate invalid grading scale with invalid points', () => {
        comp.maxPoints = 100;
        comp.gradingScale.gradeSteps[0].lowerBoundPoints = 0;
        comp.gradingScale.gradeSteps[0].upperBoundPoints = -120;
        comp.gradingScale.gradeSteps[1].lowerBoundPoints = 40;
        comp.gradingScale.gradeSteps[1].upperBoundPoints = 80;
        comp.gradingScale.gradeSteps[2].lowerBoundPoints = 80;
        comp.gradingScale.gradeSteps[2].upperBoundPoints = 100;
        translateStub.returns('invalid points');

        expect(comp.validGradeSteps()).to.be.false;
        expect(comp.invalidGradeStepsMessage).to.be.equal('invalid points');
        expect(translateStub).to.have.been.calledOnceWithExactly('artemisApp.gradingSystem.error.invalidMinMaxPoints');
    });

    it('should validate invalid grading scale with set points when all should be undefined', () => {
        comp.gradingScale.gradeSteps[0].upperBoundPoints = 70;

        expect(comp.validGradeSteps()).to.be.false;
    });

    it('should validate invalid grading scale with non-unique grade names', () => {
        comp.gradingScale.gradeType = GradeType.GRADE;
        comp.gradingScale.gradeSteps[1].gradeName = 'Fail';
        translateStub.returns('non-unique grade names');

        expect(comp.validGradeSteps()).to.be.false;
        expect(comp.invalidGradeStepsMessage).to.be.equal('non-unique grade names');
        expect(translateStub).to.have.been.calledOnceWithExactly('artemisApp.gradingSystem.error.nonUniqueGradeNames');
    });

    it('should validate invalid grading scale with unset first passing grade', () => {
        comp.gradingScale.gradeType = GradeType.GRADE;
        comp.firstPassingGrade = undefined;
        translateStub.returns('unset first passing grade');

        expect(comp.validGradeSteps()).to.be.false;
        expect(comp.invalidGradeStepsMessage).to.be.equal('unset first passing grade');
        expect(translateStub).to.have.been.calledOnceWithExactly('artemisApp.gradingSystem.error.unsetFirstPassingGrade');
    });

    it('should validate invalid grading scale with invalid bonus points', () => {
        comp.gradingScale.gradeSteps[0].gradeName = '-2';
        comp.gradingScale.gradeType = GradeType.BONUS;
        translateStub.returns('invalid bonus points');

        expect(comp.validGradeSteps()).to.be.false;
        expect(comp.invalidGradeStepsMessage).to.be.equal('invalid bonus points');
        expect(translateStub).to.have.been.calledOnceWithExactly('artemisApp.gradingSystem.error.invalidBonusPoints');
    });

    it('should validate invalid grading scale without strictly ascending bonus points', () => {
        comp.gradingScale.gradeSteps[0].gradeName = '0';
        comp.gradingScale.gradeSteps[1].gradeName = '2';
        comp.gradingScale.gradeSteps[2].gradeName = '1';
        comp.gradingScale.gradeType = GradeType.BONUS;
        translateStub.returns('descending bonus points');

        expect(comp.validGradeSteps()).to.be.false;
        expect(comp.invalidGradeStepsMessage).to.be.equal('descending bonus points');
        expect(translateStub).to.have.been.calledOnceWithExactly('artemisApp.gradingSystem.error.nonStrictlyIncreasingBonusPoints');
    });

    it('should validate invalid grading scale with invalid adjacency', () => {
        const gradeStep: GradeStep = {
            gradeName: 'Grade',
            isPassingGrade: false,
            lowerBoundInclusive: true,
            lowerBoundPercentage: 0,
            upperBoundInclusive: false,
            upperBoundPercentage: 30,
        };
        translateStub.returns('invalid adjacency');
        sinon.stub(gradingSystemService, 'sortGradeSteps').returns([gradeStep, gradeStep2, gradeStep3]);

        expect(comp.validGradeSteps()).to.be.false;
        expect(comp.invalidGradeStepsMessage).to.be.equal('invalid adjacency');
        expect(translateStub).to.have.been.calledOnceWithExactly('artemisApp.gradingSystem.error.invalidAdjacency');
    });

    it('should validate invalid grading scale with invalid first grade step', () => {
        const invalidFirstGradeStep: GradeStep = {
            gradeName: 'Name',
            isPassingGrade: false,
            lowerBoundInclusive: true,
            lowerBoundPercentage: 20,
            upperBoundInclusive: false,
            upperBoundPercentage: 40,
        };
        sinon.stub(gradingSystemService, 'sortGradeSteps').returns([invalidFirstGradeStep, gradeStep2, gradeStep3]);
        comp.gradingScale.gradeSteps[0].lowerBoundPercentage = 10;
        translateStub.returns('invalid first grade step');

        expect(comp.validGradeSteps()).to.be.false;
        expect(comp.invalidGradeStepsMessage).to.be.equal('invalid first grade step');
        expect(translateStub).to.have.been.calledOnceWithExactly('artemisApp.gradingSystem.error.invalidFirstAndLastStep');
    });

    it('should detect that max points are valid', () => {
        comp.maxPoints = 100;

        expect(comp.maxPointsValid()).to.be.true;
    });

    it('should set points correctly', () => {
        gradeStep1.lowerBoundPoints = undefined;

        comp.setPoints(gradeStep1, true);

        expect(gradeStep1.lowerBoundPoints).to.be.undefined;

        comp.maxPoints = 100;

        comp.setPoints(gradeStep1, true);

        expect(gradeStep1.lowerBoundPoints).to.equal(0);

        comp.setPoints(gradeStep1, false);

        expect(gradeStep1.upperBoundPoints).to.equal(40);

        gradeStep1.lowerBoundPoints = undefined;
        gradeStep1.upperBoundPoints = undefined;
    });

    it('should set percentages correctly', () => {
        comp.maxPoints = 100;
        gradeStep2.lowerBoundPoints = 40;
        gradeStep2.upperBoundPoints = 80;

        comp.setPercentage(gradeStep2, true);
        comp.setPercentage(gradeStep2, false);

        expect(gradeStep2.lowerBoundPercentage).to.equal(40);
        expect(gradeStep2.upperBoundPercentage).to.equal(80);

        gradeStep2.lowerBoundPoints = undefined;
        gradeStep2.upperBoundPoints = undefined;
    });

    it('should set all grade step points correctly', () => {
        comp.maxPoints = 100;

        comp.onChangeMaxPoints(100);

        expect(comp.gradingScale.gradeSteps[0].lowerBoundPoints).to.equal(0);
        expect(comp.gradingScale.gradeSteps[0].upperBoundPoints).to.equal(40);
        expect(comp.gradingScale.gradeSteps[1].lowerBoundPoints).to.equal(40);
        expect(comp.gradingScale.gradeSteps[1].upperBoundPoints).to.equal(80);
        expect(comp.gradingScale.gradeSteps[2].lowerBoundPoints).to.equal(80);
        expect(comp.gradingScale.gradeSteps[2].upperBoundPoints).to.equal(100);

        comp.onChangeMaxPoints(-10);

        expect(comp.gradingScale.gradeSteps[0].lowerBoundPoints).to.be.undefined;
        expect(comp.gradingScale.gradeSteps[0].upperBoundPoints).to.be.undefined;
        expect(comp.gradingScale.gradeSteps[1].lowerBoundPoints).to.be.undefined;
        expect(comp.gradingScale.gradeSteps[1].upperBoundPoints).to.be.undefined;
        expect(comp.gradingScale.gradeSteps[2].lowerBoundPoints).to.be.undefined;
        expect(comp.gradingScale.gradeSteps[2].upperBoundPoints).to.be.undefined;
    });
});
