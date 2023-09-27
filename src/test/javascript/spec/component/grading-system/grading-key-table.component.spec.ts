import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute, ActivatedRouteSnapshot, Params, Router } from '@angular/router';
import { of } from 'rxjs';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { GradeStep, GradeStepsDTO } from 'app/entities/grade-step.model';
import { GradeType, GradingScale } from 'app/entities/grading-scale.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SafeHtmlPipe } from 'app/shared/pipes/safe-html.pipe';
import { GradeStepBoundsPipe } from 'app/shared/pipes/grade-step-bounds.pipe';
import { MockLocalStorageService } from '../../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService } from 'ngx-webstorage';
import { BonusService } from 'app/grading-system/bonus/bonus.service';
import { Bonus } from 'app/entities/bonus.model';
import { HttpResponse } from '@angular/common/http';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { ScoresStorageService } from 'app/course/course-scores/scores-storage.service';
import { CourseScores, StudentScores } from 'app/course/course-scores/course-scores';
import { GradingKeyTableComponent } from 'app/grading-system/grading-key-overview/grading-key/grading-key-table.component';

describe('GradingKeyTableComponent', () => {
    let fixture: ComponentFixture<GradingKeyTableComponent>;
    let comp: GradingKeyTableComponent;
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
        plagiarismGrade: GradingScale.DEFAULT_PLAGIARISM_GRADE,
        noParticipationGrade: GradingScale.DEFAULT_NO_PARTICIPATION_GRADE,
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
                GradingKeyTableComponent,
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
                MockProvider(CourseStorageService),
                MockProvider(ScoresStorageService),
                { provide: LocalStorageService, useClass: MockLocalStorageService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(GradingKeyTableComponent);
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
        expect(comp.studentGradeOrBonusPointsOrGradeBonus).toBe(grade);
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
        // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
        route.parent!.snapshot.params = route.parent?.parent?.snapshot.params!;
        route.parent!.parent!.snapshot = { params: {} } as ActivatedRouteSnapshot;

        expectInitialState(studentGrade);
    });

    it('should initialize when params are in current route', () => {
        // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
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

        // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
        route.snapshot.params = route.parent?.parent?.snapshot.params!;
        route.parent!.parent!.snapshot = { params: {} } as ActivatedRouteSnapshot;
        route.snapshot.data.forBonus = true;

        expectInitialState(studentGrade);

        expect(bonusServiceSpy).toHaveBeenCalledOnce();
        expect(bonusServiceSpy).toHaveBeenCalledWith(345, 123, true);
    });

    it('should initialize for courses', () => {
        route.parent!.parent!.snapshot!.params.examId = undefined;
        const courseId = route.parent!.parent!.snapshot!.params.courseId;
        const reachablePoints = 200;

        const scoresStorageService = fixture.debugElement.injector.get(ScoresStorageService);
        const getStoredScoresStub = jest.spyOn(scoresStorageService, 'getStoredTotalScores').mockReturnValue(new CourseScores(250, 200, 0, new StudentScores()));
        const gradingSystemServiceSpy = jest.spyOn(gradingSystemService, 'setGradePoints');

        jest.spyOn(gradingSystemService, 'findGradeSteps').mockReturnValue(of(gradeStepsDto));
        jest.spyOn(gradingSystemService, 'sortGradeSteps').mockReturnValue([gradeStep1, gradeStep2]);

        fixture.detectChanges();

        expect(fixture).toBeTruthy();
        expect(comp).toBeTruthy();
        expect(comp.examId).toBeUndefined();
        expect(comp.courseId).toBe(courseId);
        expect(comp.studentGradeOrBonusPointsOrGradeBonus).toBe(studentGrade);
        expect(comp.title).toBe('Title');
        expect(comp.isBonus).toBeTrue();
        expect(comp.isExam).toBeFalse();

        expect(getStoredScoresStub).toHaveBeenCalledOnce();
        expect(getStoredScoresStub).toHaveBeenCalledWith(courseId);

        expect(gradingSystemServiceSpy).toHaveBeenCalledOnce();
        expect(gradingSystemServiceSpy).toHaveBeenCalledWith([gradeStep1, gradeStep2], reachablePoints);
    });
});
