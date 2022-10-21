import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, ActivatedRouteSnapshot, Params, Router } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ThemeService } from 'app/core/theme/theme.service';
import { Bonus } from 'app/entities/bonus.model';
import { GradeStep, GradeStepsDTO } from 'app/entities/grade-step.model';
import { GradeType } from 'app/entities/grading-scale.model';
import { BonusService } from 'app/grading-system/bonus/bonus.service';
import { GradingKeyOverviewComponent } from 'app/grading-system/grading-key-overview/grading-key-overview.component';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { GradeStepBoundsPipe } from 'app/shared/pipes/grade-step-bounds.pipe';
import { SafeHtmlPipe } from 'app/shared/pipes/safe-html.pipe';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { LocalStorageService } from 'ngx-webstorage';
import { of } from 'rxjs';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { MockLocalStorageService } from '../../helpers/mocks/service/mock-local-storage.service';

describe('GradeKeyOverviewComponent', () => {
    let fixture: ComponentFixture<GradingKeyOverviewComponent>;
    let comp: GradingKeyOverviewComponent;
    let route: ActivatedRoute;

    let gradingSystemService: GradingSystemService;
    let bonusService: BonusService;

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

    const studentGrade = '2.0';

    beforeEach(() => {
        route = {
            snapshot: { params: {} as Params, queryParams: { grade: studentGrade } as Params, data: {} },
            parent: {
                snapshot: { params: {} },
                parent: {
                    snapshot: {
                        params: { courseId: 345, examId: 123 } as Params,
                    },
                },
            },
        } as ActivatedRoute;

        return TestBed.configureTestingModule({
            imports: [MockModule(NgbModule)],
            declarations: [
                GradingKeyOverviewComponent,
                MockComponent(FaIconComponent),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(TranslateDirective),
                MockPipe(SafeHtmlPipe),
                MockPipe(GradeStepBoundsPipe),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useClass: MockRouter },
                MockProvider(GradingSystemService),
                MockProvider(BonusService),
                MockProvider(ArtemisNavigationUtilService),
                { provide: LocalStorageService, useClass: MockLocalStorageService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(GradingKeyOverviewComponent);
                comp = fixture.componentInstance;
                gradingSystemService = fixture.debugElement.injector.get(GradingSystemService);
                bonusService = fixture.debugElement.injector.get(BonusService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    function expectInitialState(grade?: string) {
        jest.spyOn(gradingSystemService, 'findGradeSteps').mockReturnValue(of(gradeStepsDto));
        jest.spyOn(gradingSystemService, 'sortGradeSteps').mockReturnValue([gradeStep1, gradeStep2]);
        const gradePointsSpy = jest.spyOn(gradingSystemService, 'setGradePoints').mockImplementation();

        fixture.detectChanges();

        expect(fixture).toBeTruthy();
        expect(comp).toBeTruthy();
        expect(comp.examId).toBe(123);
        expect(comp.courseId).toBe(345);
        expect(comp.studentGrade).toBe(grade);
        expect(comp.title).toBe('Title');
        expect(comp.isBonus).toBeTrue();
        expect(comp.isExam).toBeTrue();
        expect(comp.gradeSteps).toEqual([gradeStep1, gradeStep2]);
        expect(gradePointsSpy).toHaveBeenCalledWith([gradeStep1, gradeStep2], 100);
    }

    it('should initialize when grade queryParam is not given', () => {
        route.snapshot.queryParams = {};

        expectInitialState(undefined);
    });

    it('should initialize when params are in grandparent route', () => {
        expectInitialState(studentGrade);
    });

    it('should initialize when params are in parent route', () => {
        route.parent!.snapshot.params = route.parent?.parent?.snapshot.params!;
        route.parent!.parent!.snapshot = { params: {} } as ActivatedRouteSnapshot;

        expectInitialState(studentGrade);
    });

    it('should initialize when params are in current route', () => {
        route.snapshot.params = route.parent?.parent?.snapshot.params!;
        route.parent!.parent!.snapshot = { params: {} } as ActivatedRouteSnapshot;

        expectInitialState(studentGrade);
    });

    it('should initialize for bonus grading scale', () => {
        jest.spyOn(gradingSystemService, 'getGradingScaleTitle').mockImplementation((gradingScale) => gradingScale?.course?.title);
        jest.spyOn(gradingSystemService, 'getGradingScaleMaxPoints').mockImplementation((gradingScale) => gradingScale?.course?.maxPoints ?? 0);
        const bonusServiceSpy = jest.spyOn(bonusService, 'findBonusForExam').mockReturnValue(
            of({
                body: {
                    sourceGradingScale: {
                        gradeSteps: gradeStepsDto.gradeSteps,
                        gradeType: gradeStepsDto.gradeType,
                        course: { title: gradeStepsDto.title, maxPoints: gradeStepsDto.maxPoints },
                    },
                } as Bonus,
            } as HttpResponse<Bonus>),
        );

        route.snapshot.params = route.parent?.parent?.snapshot.params!;
        route.parent!.parent!.snapshot = { params: {} } as ActivatedRouteSnapshot;
        route.snapshot.data.forBonus = true;

        expectInitialState(studentGrade);

        expect(bonusServiceSpy).toHaveBeenCalledOnce();
        expect(bonusServiceSpy).toHaveBeenCalledWith(345, 123, true);
    });

    it('should print PDF', fakeAsync(() => {
        const printSpy = jest.spyOn(TestBed.inject(ThemeService), 'print').mockImplementation();

        comp.printPDF();

        tick();
        expect(printSpy).toHaveBeenCalledOnce();
    }));

    it('should round correctly', () => {
        expect(comp.round(undefined)).toBeUndefined();
        expect(comp.round(5)).toBe(5);
        expect(comp.round(3.33333333333333333)).toBe(3.33);
    });
});
