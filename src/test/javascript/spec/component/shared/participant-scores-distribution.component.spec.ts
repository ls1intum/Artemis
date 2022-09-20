import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ParticipantScoresDistributionComponent } from 'app/shared/participant-scores/participant-scores-distribution/participant-scores-distribution.component';
import { MockComponent, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTestModule } from '../../test.module';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { BarChartModule } from '@swimlane/ngx-charts';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { GraphColors } from 'app/entities/statistics.model';
import { GradeType, GradingScale } from 'app/entities/grading-scale.model';
import { GradeStep } from 'app/entities/grade-step.model';

describe('ParticipantScoresDistributionComponent', () => {
    let fixture: ComponentFixture<ParticipantScoresDistributionComponent>;
    let component: ParticipantScoresDistributionComponent;

    let expectedColoring: string[];
    let expectedDistribution: number[];

    const defaultLabels = [
        '[0,5)',
        '[5,10)',
        '[10,15)',
        '[15,20)',
        '[20,25)',
        '[25,30)',
        '[30,35)',
        '[35,40)',
        '[40,45)',
        '[45,50)',
        '[50,55)',
        '[55,60)',
        '[60,65)',
        '[65,70)',
        '[70,75)',
        '[75,80)',
        '[80,85)',
        '[85,90)',
        '[90,95)',
        '[95,100]',
    ];

    const gradeStep1: GradeStep = {
        isPassingGrade: false,
        lowerBoundInclusive: true,
        lowerBoundPercentage: 0,
        upperBoundInclusive: false,
        upperBoundPercentage: 40,
        gradeName: '4',
    };
    const gradeStep2: GradeStep = {
        isPassingGrade: true,
        lowerBoundInclusive: true,
        lowerBoundPercentage: 40,
        upperBoundInclusive: false,
        upperBoundPercentage: 60,
        gradeName: '3',
    };
    const gradeStep3: GradeStep = {
        isPassingGrade: true,
        lowerBoundInclusive: true,
        lowerBoundPercentage: 60,
        upperBoundInclusive: false,
        upperBoundPercentage: 80,
        gradeName: '2',
    };
    const gradeStep4: GradeStep = {
        isPassingGrade: true,
        lowerBoundInclusive: true,
        lowerBoundPercentage: 80,
        upperBoundInclusive: true,
        upperBoundPercentage: 100,
        gradeName: '1',
    };

    const gradingScale = {
        gradeType: GradeType.GRADE,
        gradeSteps: [gradeStep1, gradeStep2, gradeStep3, gradeStep4],
    } as GradingScale;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(BarChartModule)],
            declarations: [ParticipantScoresDistributionComponent, MockPipe(ArtemisTranslatePipe), MockComponent(HelpIconComponent)],
            providers: [MockProvider(GradingSystemService), { provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ParticipantScoresDistributionComponent);
                component = fixture.componentInstance;

                component.scores = [14, 56];
            });
    });

    afterEach(() => jest.restoreAllMocks());

    it('should setup default configuration if no grading scale exists', () => {
        expectedColoring = [...Array(8).fill(GraphColors.YELLOW), ...Array(12).fill(GraphColors.GREY)];
        expectedDistribution = [0, 0, 1, ...Array(8).fill(0), 1, ...Array(8).fill(0)];
        component.scoreToHighlight = 70;

        component.ngOnChanges();

        expect(component.yScaleMax).toBe(30);
        expect(component.height).toBe(500);
        expect(component.xAxisLabel).toBe('artemisApp.examScores.xAxes');
        expect(component.yAxisLabel).toBe('artemisApp.examScores.yAxes');
        expect(component.helpIconTooltip).toBe('artemisApp.instructorDashboard.courseScoreChart.noGradingScaleExplanation');
        expect(component.ngxColor.domain).toEqual(expectedColoring);
        expect(component.ngxData.map((data) => data.name)).toEqual(defaultLabels);
        expect(component.ngxData.map((data) => data.value)).toEqual(expectedDistribution);

        component.isCourseScore = false;

        component.ngOnChanges();

        expect(component.height).toBe(400);
        expect(component.helpIconTooltip).toBe('artemisApp.examScores.noGradingScaleExplanation');
    });

    it('should setup default configuration if nonbonus grading scale exists', () => {
        component.gradingScale = gradingScale;
        expectedColoring = [GraphColors.RED, ...Array(3).fill(GraphColors.GREY)];
        expectedDistribution = [2, 0, 0, 0];
        const expectedLabels = ['[0,40) {4}', '[40,60) {3}', '[60,80) {2}', '[80,100] {1}'];

        component.ngOnChanges();

        expect(component.gradingScaleExists).toBeTrue();
        expect(component.xAxisLabel).toBe('artemisApp.examScores.xAxesartemisApp.examScores.xAxesSuffixNoBonus');
        expect(component.yAxisLabel).toBe('artemisApp.examScores.yAxes');
        expect(component.helpIconTooltip).toBe('artemisApp.instructorDashboard.courseScoreChart.gradingScaleExplanationNotBonus');
        expect(component.ngxColor.domain).toEqual(expectedColoring);
        expect(component.ngxData.map((data) => data.name)).toEqual(expectedLabels);
        expect(component.ngxData.map((data) => data.value)).toEqual(expectedDistribution);

        component.isCourseScore = false;

        component.ngOnChanges();

        expect(component.helpIconTooltip).toBe('artemisApp.examScores.gradingScaleExplanationNotBonus');
    });

    it('should setup default configuration if bonus grading scale exists', () => {
        const bonusGradingScale = gradingScale;
        bonusGradingScale.gradeType = GradeType.BONUS;
        component.gradingScale = bonusGradingScale;
        component.scoreToHighlight = 13;
        expectedColoring = [GraphColors.LIGHT_BLUE, ...Array(3).fill(GraphColors.GREY)];
        expectedDistribution = [2, 0, 0, 0];
        const expectedLabels = ['[0,40) {4}', '[40,60) {3}', '[60,80) {2}', '[80,100] {1}'];

        component.ngOnChanges();

        expect(component.gradingScaleExists).toBeTrue();
        expect(component.isBonus).toBeTrue();
        expect(component.xAxisLabel).toBe('artemisApp.examScores.xAxesartemisApp.examScores.xAxesSuffixBonus');
        expect(component.yAxisLabel).toBe('artemisApp.examScores.yAxes');
        expect(component.helpIconTooltip).toBe('artemisApp.examScores.gradingScaleExplanationBonus');
        expect(component.ngxColor.domain).toEqual(expectedColoring);
        expect(component.ngxData.map((data) => data.name)).toEqual(expectedLabels);
        expect(component.ngxData.map((data) => data.value)).toEqual(expectedDistribution);

        component.scoreToHighlight = undefined;

        component.ngOnChanges();

        expect(component.ngxColor.domain).toEqual(Array(4).fill(GraphColors.GREY));
    });

    it('should setup default configuration from gradeNames for bonus grades if nonbonus grading scale with bonus exists', () => {
        component.scores = undefined;
        component.gradeNames = ['2', '3'];
        component.gradingScale = gradingScale;
        expectedColoring = [GraphColors.RED, ...Array(3).fill(GraphColors.GREY)];
        expectedDistribution = [0, 1, 1, 0];
        const expectedLabels = ['[0,40) {4}', '[40,60) {3}', '[60,80) {2}', '[80,100] {1}'];

        component.ngOnChanges();

        expect(component.gradingScaleExists).toBeTrue();
        expect(component.xAxisLabel).toBe('artemisApp.examScores.xAxesartemisApp.examScores.xAxesSuffixNoBonus');
        expect(component.yAxisLabel).toBe('artemisApp.examScores.yAxes');
        expect(component.helpIconTooltip).toBe('artemisApp.instructorDashboard.courseScoreChart.gradingScaleExplanationNotBonus');
        expect(component.ngxColor.domain).toEqual(expectedColoring);
        expect(component.ngxData.map((data) => data.name)).toEqual(expectedLabels);
        expect(component.ngxData.map((data) => data.value)).toEqual(expectedDistribution);
    });

    it('should throw when both scores and gradeNames are not given', () => {
        component.scores = undefined;
        component.gradeNames = undefined;
        expect(() => component.ngOnChanges()).toThrow(Error);
    });

    it('should listen to window resizing', () => {
        const realignChartSpy = jest.spyOn(component, 'realignChart');

        window['innerWidth'] = 700;
        window.dispatchEvent(new Event('resize'));

        expect(realignChartSpy).toHaveBeenCalledOnce();
        expect(component.showYAxisLabel).toBeTrue();

        window['innerWidth'] = 699;
        window.dispatchEvent(new Event('resize'));

        expect(realignChartSpy).toHaveBeenCalledTimes(2);
        expect(component.showYAxisLabel).toBeFalse();
    });
});
