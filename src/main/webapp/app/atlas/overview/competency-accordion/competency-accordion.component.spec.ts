import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CompetencyAccordionComponent } from 'app/atlas/overview/competency-accordion/competency-accordion.component';
import { CompetencyRingsComponent } from 'app/atlas/shared/competency-rings/competency-rings.component';
import {
    CompetencyMetrics,
    ExerciseInformation,
    ExerciseMetrics,
    LectureUnitInformation,
    LectureUnitStudentMetricsDTO,
    StudentMetrics,
} from 'app/atlas/shared/entities/student-metrics.model';
import dayjs from 'dayjs/esm';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';

describe('CompetencyAccordionComponent', () => {
    let fixture: ComponentFixture<CompetencyAccordionComponent>;
    let component: CompetencyAccordionComponent;
    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [CompetencyAccordionComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FaIconComponent), MockComponent(CompetencyRingsComponent)],
            providers: [MockProvider(TranslateService)],
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
        fixture.componentRef.setInput('competency', { description: '', masteryThreshold: 80, optional: false, title: '', id: 42 });
        fixture.componentRef.setInput('metrics', metrics);
        const progress = component.calculateExercisesProgress();
        // achieved points decided by total points
        expect(progress).toBeCloseTo((80 * 10 + 40 * 20) / (10 + 20), 1);
    });

    it('should calculate lecture progress', () => {
        const before = dayjs().subtract(1, 'day');
        const lectureUnitInformation0: LectureUnitInformation = { lectureTitle: '', name: '', type: LectureUnitType.ATTACHMENT_VIDEO, id: 0, lectureId: 21, releaseDate: before };
        const lectureUnitInformation1: LectureUnitInformation = { lectureTitle: '', name: '', type: LectureUnitType.TEXT, id: 1, lectureId: 21, releaseDate: before };
        const lectureUnitInformation2: LectureUnitInformation = { lectureTitle: '', name: '', type: LectureUnitType.ONLINE, id: 2, lectureId: 21, releaseDate: before };
        const lectureUnitStudentMetricsDTO: LectureUnitStudentMetricsDTO = {
            lectureUnitInformation: { 0: lectureUnitInformation0, 1: lectureUnitInformation1, 2: lectureUnitInformation2 },
            completed: [0, 2],
        };
        const competencyMetrics: CompetencyMetrics = { lectureUnits: { 42: [0, 1, 2] } };
        const metrics: StudentMetrics = { lectureUnitStudentMetricsDTO, competencyMetrics };
        fixture.componentRef.setInput('competency', { description: '', masteryThreshold: 80, optional: false, title: '', id: 42 });
        fixture.componentRef.setInput('metrics', metrics);
        const progress = component.calculateLectureUnitsProgress();
        // completed 2 out of 3 lecture units
        expect(progress).toBeCloseTo((2 / 3) * 100, 0);
    });
});
