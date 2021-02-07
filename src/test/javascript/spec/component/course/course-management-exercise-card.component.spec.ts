import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { CourseManagementExerciseRowComponent } from 'app/course/manage/overview/course-management-exercise-row.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MockRouterLinkDirective } from '../lecture-unit/lecture-unit-management.component.spec';
import { CourseManagementCardComponent } from 'app/course/manage/overview/course-management-card.component';
import { CourseManagementStatisticsComponent } from 'app/course/manage/overview/course-management-statistics.component';
import * as moment from 'moment';
import { CourseManagementOverviewDetailsDto } from 'app/course/manage/overview/course-management-overview-details-dto.model';
import { CourseManagementOverviewDto } from 'app/course/manage/overview/course-management-overview-dto.model';
import { CourseManagementOverviewExerciseDetailsDTO } from 'app/course/manage/overview/course-management-overview-exercise-details-dto.model';
import { CourseManagementOverviewStatisticsDto } from 'app/course/manage/overview/course-management-overview-statistics-dto.model';
import { CourseManagementOverviewExerciseStatisticsDTO } from 'app/course/manage/overview/course-management-overview-exercise-statistics-dto.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('CourseManagementCardComponent', () => {
    let fixture: ComponentFixture<CourseManagementCardComponent>;
    let component: CourseManagementCardComponent;
    let service: CourseManagementService;

    const courseDTO = new CourseManagementOverviewDto();
    courseDTO.courseId = 1;
    const pastExercise = new CourseManagementOverviewExerciseDetailsDTO();
    pastExercise.dueDate = moment().subtract(6, 'days');
    pastExercise.assessmentDueDate = moment().subtract(1, 'days');
    const currentExercise = new CourseManagementOverviewExerciseDetailsDTO();
    currentExercise.dueDate = moment().add(2, 'days');
    currentExercise.releaseDate = moment().subtract(2, 'days');
    const futureExercise1 = new CourseManagementOverviewExerciseDetailsDTO();
    futureExercise1.releaseDate = moment().add(4, 'days');
    const futureExercise2 = new CourseManagementOverviewExerciseDetailsDTO();
    futureExercise2.releaseDate = moment().add(6, 'days');
    courseDTO.exerciseDetails = [pastExercise, currentExercise, futureExercise2, futureExercise1];

    const courseDetailsDTO = new CourseManagementOverviewDetailsDto();
    courseDetailsDTO.id = 1;
    courseDetailsDTO.color = 'red';

    const courseStatisticsDTO = new CourseManagementOverviewStatisticsDto();
    const exerciseDTO = new CourseManagementOverviewExerciseStatisticsDTO();
    exerciseDTO.exerciseId = 1;
    exerciseDTO.exerciseMaxPoints = 10;
    exerciseDTO.averageScoreInPercent = 50;
    courseStatisticsDTO.exerciseDTOS = [exerciseDTO];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                CourseManagementCardComponent,
                MockPipe(TranslatePipe),
                MockDirective(NgbTooltip),
                MockRouterLinkDirective,
                MockComponent(CourseManagementExerciseRowComponent),
                MockComponent(CourseManagementStatisticsComponent),
            ],
            providers: [{ provide: LocalStorageService, useClass: MockSyncStorage }, { provide: SessionStorageService, useClass: MockSyncStorage }, MockProvider(TranslateService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseManagementCardComponent);
                component = fixture.componentInstance;
                service = TestBed.inject(CourseManagementService);
            });
    });

    it('should initialize component', () => {
        component.course = courseDetailsDTO;
        component.courseStatistics = courseStatisticsDTO;
        component.ngOnChanges();
        expect(component.statisticsPerExercise[exerciseDTO.exerciseId!]).to.deep.equal(exerciseDTO);

        component.courseDetails = courseDTO;
        component.ngOnChanges();
        expect(component.futureExercises).to.deep.equal([futureExercise1, futureExercise2]);
        expect(component.currentExercises).to.deep.equal([currentExercise]);
        expect(component.pastExercises).to.deep.equal([pastExercise]);
    });
});
