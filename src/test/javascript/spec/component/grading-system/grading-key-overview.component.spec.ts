import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { GradingKeyOverviewComponent } from 'app/grading-system/grading-key-overview/grading-key-overview.component';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { GradeStep, GradeStepsDTO } from 'app/entities/grade-step.model';
import { GradeType } from 'app/entities/grading-scale.model';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('GradeKeyOverviewComponent', () => {
    let fixture: ComponentFixture<GradingKeyOverviewComponent>;
    let comp: GradingKeyOverviewComponent;

    let gradingSystemService: GradingSystemService;

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
        title: 'Title',
        gradeType: GradeType.BONUS,
        gradeSteps: [gradeStep1, gradeStep2],
        maxPoints: 100,
    };

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [MockModule(NgbModule)],
            declarations: [GradingKeyOverviewComponent, MockComponent(FaIconComponent), MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)],
            providers: [
                { provide: ActivatedRoute, useValue: { params: of({ courseId: 345, examId: 123 }), queryParams: of({ grade: '2.0' }) } },
                { provide: Router, useClass: MockRouter },
                MockProvider(GradingSystemService),
                MockProvider(ArtemisNavigationUtilService),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(GradingKeyOverviewComponent);
                comp = fixture.componentInstance;
                gradingSystemService = fixture.debugElement.injector.get(GradingSystemService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        jest.spyOn(gradingSystemService, 'findGradeSteps').mockReturnValue(of(gradeStepsDto));
        jest.spyOn(gradingSystemService, 'sortGradeSteps').mockReturnValue([gradeStep1, gradeStep2]);
        const gradePointsSpy = jest.spyOn(gradingSystemService, 'setGradePoints').mockImplementation();

        fixture.detectChanges();

        expect(fixture).toBeTruthy();
        expect(comp).toBeTruthy();
        expect(comp.examId).toEqual(123);
        expect(comp.courseId).toEqual(345);
        expect(comp.studentGrade).toEqual('2.0');
        expect(comp.title).toEqual('Title');
        expect(comp.isBonus).toEqual(true);
        expect(comp.isExam).toEqual(true);
        expect(comp.gradeSteps).toEqual([gradeStep1, gradeStep2]);
        expect(gradePointsSpy).toHaveBeenCalledWith([gradeStep1, gradeStep2], 100);
    });

    it('should print PDF', fakeAsync(() => {
        const windowSpy = jest.spyOn(window, 'print').mockImplementation();

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
