import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { GradingKeyOverviewComponent } from 'app/grading-system/grading-key-overview/grading-key-overview.component';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute, ActivatedRouteSnapshot, Params, Router } from '@angular/router';
import { of } from 'rxjs';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { GradeStep, GradeStepsDTO } from 'app/entities/grade-step.model';
import { GradeType } from 'app/entities/grading-scale.model';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SafeHtmlPipe } from 'app/shared/pipes/safe-html.pipe';
import { GradeStepBoundsPipe } from 'app/shared/pipes/grade-step-bounds.pipe';
import { MockLocalStorageService } from '../../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService } from 'ngx-webstorage';
import { ThemeService } from 'app/core/theme/theme.service';

describe('GradeKeyOverviewComponent', () => {
    let fixture: ComponentFixture<GradingKeyOverviewComponent>;
    let comp: GradingKeyOverviewComponent;
    let route: ActivatedRoute;

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

    const studentGrade = '2.0';

    beforeEach(() => {
        route = {
            snapshot: { params: {} as Params, queryParams: { grade: studentGrade } as Params },
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
                MockProvider(ArtemisNavigationUtilService),
                { provide: LocalStorageService, useClass: MockLocalStorageService },
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
