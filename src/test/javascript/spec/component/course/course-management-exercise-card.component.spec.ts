import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { CourseManagementExerciseRowComponent } from 'app/course/manage/overview/course-management-exercise-row.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MockRouterLinkDirective } from '../lecture-unit/lecture-unit-management.component.spec';
import { CourseManagementOverviewCourseInformationDto } from 'app/course/manage/course-management-overview-courses-dto.model';
import { CourseManagementCardComponent } from 'app/course/manage/overview/course-management-card.component';
import { CourseManagementStatisticsComponent } from 'app/course/manage/overview/course-management-statistics.component';
import { CourseManagementOverviewCourseDetailDto } from 'app/course/manage/course-management-overview-course-dto.model';
import { CourseManagementOverviewStatisticsDto } from 'app/course/manage/course-management-overview-statistics-dto.model';
import { CourseManagementOverviewExerciseDetailsDTO } from 'app/entities/course-management-overview-exercise-details-dto.model';
import * as moment from 'moment';
import { CourseManagementOverviewExerciseStatisticsDTO } from 'app/entities/course-management-overview-exercise-statistics-dto.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('CourseManagementCardComponent', () => {
    let fixture: ComponentFixture<CourseManagementCardComponent>;
    let component: CourseManagementCardComponent;

    const courseDetails = new CourseManagementOverviewCourseDetailDto();
    courseDetails.courseId = 1;
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
    courseDetails.exerciseDetails = [pastExercise, currentExercise, futureExercise2, futureExercise1];

    const coursesDTO = new CourseManagementOverviewCourseInformationDto();
    coursesDTO.id = 1;
    coursesDTO.color = 'red';

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
            });
    });

    it('should initialize component', () => {
        component.course = coursesDTO;
        component.courseStatistics = courseStatisticsDTO;
        component.ngOnChanges();
        expect(component.statisticsPerExercise[exerciseDTO.exerciseId!]).to.deep.equal(exerciseDTO);

        component.courseDetails = courseDetails;
        component.ngOnChanges();
        expect(component.futureExercises).to.deep.equal([futureExercise1, futureExercise2]);
        expect(component.currentExercises).to.deep.equal([currentExercise]);
        expect(component.pastExercises).to.deep.equal([pastExercise]);
    });
});
