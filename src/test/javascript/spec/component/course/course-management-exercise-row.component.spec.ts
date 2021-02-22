import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MomentModule } from 'ngx-moment';
import { CourseManagementExerciseRowComponent } from 'app/course/manage/overview/course-management-exercise-row.component';
import { ProgressBarComponent } from 'app/shared/dashboards/tutor-participation-graph/progress-bar/progress-bar.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MockRouterLinkDirective } from '../lecture-unit/lecture-unit-management.component.spec';
import { CourseManagementOverviewExerciseDetailsDTO } from 'app/course/manage/overview/course-management-overview-exercise-details-dto.model';
import { CourseManagementOverviewExerciseStatisticsDTO } from 'app/course/manage/overview/course-management-overview-exercise-statistics-dto.model';
import { ExerciseType } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('CourseManagementExerciseRowComponent', () => {
    let fixture: ComponentFixture<CourseManagementExerciseRowComponent>;
    let component: CourseManagementExerciseRowComponent;

    const exerciseDetailDTO = new CourseManagementOverviewExerciseDetailsDTO();
    exerciseDetailDTO.teamMode = false;
    exerciseDetailDTO.exerciseTitle = 'ModelingExercise';

    const exerciseStatisticsDTO = new CourseManagementOverviewExerciseStatisticsDTO();
    exerciseStatisticsDTO.averageScoreInPercent = 50;
    exerciseStatisticsDTO.exerciseMaxPoints = 10;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MomentModule],
            declarations: [
                CourseManagementExerciseRowComponent,
                MockPipe(TranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockComponent(ProgressBarComponent),
                MockDirective(NgbTooltip),
                MockRouterLinkDirective,
            ],
            providers: [{ provide: LocalStorageService, useClass: MockSyncStorage }, { provide: SessionStorageService, useClass: MockSyncStorage }, MockProvider(TranslateService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseManagementExerciseRowComponent);
                component = fixture.componentInstance;
            });
    });

    it('should initialize component', () => {
        component.course = new Course();
        component.details = exerciseDetailDTO;
        component.ngOnChanges();
        expect(component.displayTitle).to.equal('ModelingExercise');
        component.statistic = exerciseStatisticsDTO;
        component.ngOnChanges();
        expect(component.averageScoreNumerator).to.equal(5);
    });

    it('should get different icons', () => {
        expect(component.getIcon(ExerciseType.MODELING)).to.equal('project-diagram');
        expect(component.getIcon(ExerciseType.PROGRAMMING)).to.equal('keyboard');
        expect(component.getIcon(ExerciseType.TEXT)).to.equal('font');
        expect(component.getIcon(ExerciseType.FILE_UPLOAD)).to.equal('file-upload');
        expect(component.getIcon(ExerciseType.QUIZ)).to.equal('check-double');
    });

    it('should get different tooltips', () => {
        expect(component.getIconTooltip(ExerciseType.MODELING)).to.equal('artemisApp.exercise.isModeling');
        expect(component.getIconTooltip(ExerciseType.PROGRAMMING)).to.equal('artemisApp.exercise.isProgramming');
        expect(component.getIconTooltip(ExerciseType.TEXT)).to.equal('artemisApp.exercise.isText');
        expect(component.getIconTooltip(ExerciseType.FILE_UPLOAD)).to.equal('artemisApp.exercise.isFileUpload');
        expect(component.getIconTooltip(ExerciseType.QUIZ)).to.equal('artemisApp.exercise.isQuiz');
    });
});
