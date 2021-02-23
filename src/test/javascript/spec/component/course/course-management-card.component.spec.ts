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
import { CourseManagementCardComponent } from 'app/course/manage/overview/course-management-card.component';
import { CourseManagementStatisticsComponent } from 'app/course/manage/overview/course-management-statistics.component';
import * as moment from 'moment';
import { CourseManagementOverviewDto } from 'app/course/manage/overview/course-management-overview-dto.model';
import { CourseManagementOverviewExerciseDetailsDTO } from 'app/course/manage/overview/course-management-overview-exercise-details-dto.model';
import { CourseManagementOverviewStatisticsDto } from 'app/course/manage/overview/course-management-overview-statistics-dto.model';
import { CourseManagementOverviewExerciseStatisticsDTO } from 'app/course/manage/overview/course-management-overview-exercise-statistics-dto.model';
import { Course } from 'app/entities/course.model';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import {ArtemisDatePipe} from "app/shared/pipes/artemis-date.pipe";

chai.use(sinonChai);
const expect = chai.expect;

describe('CourseManagementCardComponent', () => {
    let fixture: ComponentFixture<CourseManagementCardComponent>;
    let component: CourseManagementCardComponent;

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

    const course = new Course();
    course.id = 1;
    course.color = 'red';

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
                MockPipe(ArtemisDatePipe),
                MockDirective(NgbTooltip),
                MockRouterLinkDirective,
                MockComponent(CourseManagementExerciseRowComponent),
                MockComponent(CourseManagementStatisticsComponent),
                MockComponent(SecuredImageComponent),
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
        component.course = course;
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
