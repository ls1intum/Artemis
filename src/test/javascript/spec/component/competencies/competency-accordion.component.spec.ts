import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltipMocksModule } from '../../helpers/mocks/directive/ngbTooltipMocks.module';
import { CompetencyAccordionComponent } from 'app/course/competencies/competency-accordion/competency-accordion.component';
import { CompetencyRingsComponent } from 'app/course/competencies/competency-rings/competency-rings.component';
import { CompetencyMetrics, ExerciseInformation, ExerciseMetrics, LectureUnitInformation, LectureUnitStudentMetricsDTO, StudentMetrics } from 'app/entities/student-metrics.model';
import dayjs from 'dayjs/esm';
import { ExerciseType } from 'app/entities/exercise.model';
import { LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';

describe('CompetencyAccordionComponent', () => {
    let fixture: ComponentFixture<CompetencyAccordionComponent>;
    let component: CompetencyAccordionComponent;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [NgbTooltipMocksModule],
            declarations: [CompetencyAccordionComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FaIconComponent), MockComponent(CompetencyRingsComponent)],
            providers: [MockProvider(TranslateService)],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CompetencyAccordionComponent);
                component = fixture.componentInstance;
            });
    });

    it('should calculate exercise progress', () => {
        const exerciseInformation0: ExerciseInformation = { maxPoints: 10, startDate: dayjs(), title: '', type: ExerciseType.PROGRAMMING, id: 0 };
        const exerciseInformation1: ExerciseInformation = { maxPoints: 20, startDate: dayjs(), title: '', type: ExerciseType.MODELING, id: 1 };
        const exerciseMetrics: ExerciseMetrics = { exerciseInformation: { 0: exerciseInformation0, 1: exerciseInformation1 }, score: { 0: 80, 1: 40 }, completed: [0] };
        const competencyMetrics: CompetencyMetrics = { exercises: { 42: [0, 1] } };
        const metrics: StudentMetrics = { exerciseMetrics, competencyMetrics };
        component.competency = { description: '', masteryThreshold: 80, optional: false, title: '', id: 42 };
        component.metrics = metrics;
        const progress = component.calculateExercisesProgress();
        expect(progress).toBeCloseTo((80 * 10 + 40 * 20) / (10 + 20), 1);
    });

    it('should calculate lecture progress', () => {
        const before = dayjs().subtract(1, 'day');
        const lectureUnitInformation0: LectureUnitInformation = { lectureTitle: '', name: '', type: LectureUnitType.ATTACHMENT, id: 0, lectureId: 21, releaseDate: before };
        const lectureUnitInformation1: LectureUnitInformation = { lectureTitle: '', name: '', type: LectureUnitType.TEXT, id: 1, lectureId: 21, releaseDate: before };
        const lectureUnitInformation2: LectureUnitInformation = { lectureTitle: '', name: '', type: LectureUnitType.VIDEO, id: 2, lectureId: 21, releaseDate: before };
        const lectureUnitStudentMetricsDTO: LectureUnitStudentMetricsDTO = {
            lectureUnitInformation: { 0: lectureUnitInformation0, 1: lectureUnitInformation1, 2: lectureUnitInformation2 },
            completed: [0, 2],
        };
        const competencyMetrics: CompetencyMetrics = { lectureUnits: { 42: [0, 1, 2] } };
        const metrics: StudentMetrics = { lectureUnitStudentMetricsDTO, competencyMetrics };
        component.competency = { description: '', masteryThreshold: 80, optional: false, title: '', id: 42 };
        component.metrics = metrics;
        const progress = component.calculateLectureUnitsProgress();
        expect(progress).toBeCloseTo(66.6, 0);
    });
});
