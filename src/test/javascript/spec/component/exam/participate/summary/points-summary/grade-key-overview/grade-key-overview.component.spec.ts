import * as sinon from 'sinon';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { GradeKeyOverviewComponent } from 'app/exam/participate/summary/points-summary/grade-key-overview/grade-key-overview.component';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute, Router } from '@angular/router';
import { JhiTranslateDirective } from 'ng-jhipster';
import { of } from 'rxjs';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockRouter } from '../../../../../../helpers/mocks/service/mock-route.service';
import { GradeStep, GradeStepsDTO } from 'app/entities/grade-step.model';
import { GradeType } from 'app/entities/grading-scale.model';
import { HttpResponse } from '@angular/common/http';

describe('GradeKeyOverviewComponent', () => {
    let fixture: ComponentFixture<GradeKeyOverviewComponent>;
    let comp: GradeKeyOverviewComponent;

    let gradingSystemService: GradingSystemService;
    let router: Router;

    const gradeStep1: GradeStep = {
        gradeName: 'Fail',
        lowerBoundPercentage: 0,
        upperBoundPercentage: 50,
        lowerBoundInclusive: true,
        upperBoundInclusive: false,
        isPassingGrade: false,
    };
    const gradeStep2: GradeStep = {
        gradeName: 'Pass',
        lowerBoundPercentage: 50,
        upperBoundPercentage: 100,
        lowerBoundInclusive: true,
        upperBoundInclusive: true,
        isPassingGrade: true,
    };
    const gradeStepsDto: GradeStepsDTO = {
        examTitle: 'Title',
        gradeType: GradeType.BONUS,
        gradeSteps: [gradeStep1, gradeStep2],
    };

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [MockModule(NgbModule)],
            declarations: [GradeKeyOverviewComponent, MockComponent(FaIconComponent), MockPipe(ArtemisTranslatePipe), MockDirective(JhiTranslateDirective)],
            providers: [
                { provide: ActivatedRoute, useValue: { params: of({ courseId: 345, examId: 123 }), queryParams: of({ grade: '2.0' }) } },
                { provide: Router, useClass: MockRouter },
                MockProvider(GradingSystemService),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(GradeKeyOverviewComponent);
                comp = fixture.componentInstance;
                router = fixture.debugElement.injector.get(Router);
                gradingSystemService = fixture.debugElement.injector.get(GradingSystemService);
            });
    });

    afterEach(() => {
        sinon.restore();
    });

    it('should initialize', () => {
        spyOn(gradingSystemService, 'findGradeStepsForExam').and.returnValue(of(new HttpResponse<GradeStepsDTO>({ body: gradeStepsDto })));
        spyOn(gradingSystemService, 'sortGradeSteps').and.returnValue([gradeStep1, gradeStep2]);

        fixture.detectChanges();

        expect(fixture).toBeTruthy();
        expect(comp).toBeTruthy();
        expect(comp.examId).toEqual(123);
        expect(comp.courseId).toEqual(345);
        expect(comp.studentGrade).toEqual('2.0');
        expect(comp.examTitle).toEqual('Title');
        expect(comp.isBonus).toEqual(true);
        expect(comp.gradeSteps).toEqual([gradeStep1, gradeStep2]);
    });

    it('should navigate to previous state', () => {
        const routerSpy = spyOn(router, 'navigate').and.callFake(() => {});
        comp.courseId = 345;
        comp.examId = 123;

        comp.previousState();

        expect(routerSpy).toHaveBeenCalledWith(['courses', '345', 'exams', '123']);
    });

    it('should print PDF', fakeAsync(() => {
        const windowSpy = spyOn(window, 'print');

        comp.printPDF();

        tick();
        expect(windowSpy).toHaveBeenCalled();
    }));

    it('should properly determine that points are not set', () => {
        comp.gradeSteps = gradeStepsDto.gradeSteps;

        expect(comp.hasPointsSet()).toEqual(false);
    });

    it('should properly determine that points are set', () => {
        const gradeStepWithPoints1 = Object.assign({}, gradeStep1);
        gradeStepWithPoints1.lowerBoundPoints = 0;
        gradeStepWithPoints1.upperBoundPoints = 50;
        const gradeStepWithPoints2 = Object.assign({}, gradeStep2);
        gradeStepWithPoints2.lowerBoundPoints = 50;
        gradeStepWithPoints2.upperBoundPoints = 100;
        comp.gradeSteps = [gradeStepWithPoints1, gradeStepWithPoints2];

        expect(comp.hasPointsSet()).toEqual(true);
    });

    it('should round correctly', () => {
        expect(comp.round(undefined)).toBeUndefined();
        expect(comp.round(5)).toEqual(5);
        expect(comp.round(3.33333333333333333)).toEqual(3.33);
    });
});
