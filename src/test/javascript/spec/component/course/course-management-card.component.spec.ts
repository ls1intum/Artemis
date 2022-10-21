import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { CourseManagementExerciseRowComponent } from 'app/course/manage/overview/course-management-exercise-row.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';
import { CourseManagementCardComponent } from 'app/course/manage/overview/course-management-card.component';
import { CourseManagementOverviewStatisticsComponent } from 'app/course/manage/overview/course-management-overview-statistics.component';
import dayjs from 'dayjs/esm';
import { CourseManagementOverviewStatisticsDto } from 'app/course/manage/overview/course-management-overview-statistics-dto.model';
import { CourseManagementOverviewExerciseStatisticsDTO } from 'app/course/manage/overview/course-management-overview-exercise-statistics-dto.model';
import { Course } from 'app/entities/course.model';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { Exercise } from 'app/entities/exercise.model';

describe('CourseManagementCardComponent', () => {
    let fixture: ComponentFixture<CourseManagementCardComponent>;
    let component: CourseManagementCardComponent;

    const pastExercise = {
        dueDate: dayjs().subtract(6, 'days'),
        assessmentDueDate: dayjs().subtract(1, 'days'),
    } as Exercise;
    const currentExercise = {
        dueDate: dayjs().add(2, 'days'),
        releaseDate: dayjs().subtract(2, 'days'),
    } as Exercise;
    const futureExercise1 = {
        releaseDate: dayjs().add(4, 'days'),
    } as Exercise;
    const futureExercise2 = {
        releaseDate: dayjs().add(6, 'days'),
    } as Exercise;

    const course = new Course();
    course.id = 1;
    course.color = 'red';
    course.exercises = [pastExercise, currentExercise, futureExercise2, futureExercise1];

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
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockDirective(NgbTooltip),
                MockRouterLinkDirective,
                MockComponent(CourseManagementExerciseRowComponent),
                MockComponent(CourseManagementOverviewStatisticsComponent),
                MockComponent(SecuredImageComponent),
            ],
            providers: [{ provide: LocalStorageService, useClass: MockSyncStorage }, { provide: SessionStorageService, useClass: MockSyncStorage }, MockProvider(TranslateService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseManagementCardComponent);
                component = fixture.componentInstance;
                component.course = course;
            });
    });

    it('should initialize component', () => {
        component.courseStatistics = courseStatisticsDTO;
        component.ngOnChanges();
        expect(component.statisticsPerExercise[exerciseDTO.exerciseId!]).toEqual(exerciseDTO);

        component.courseWithExercises = course;
        component.ngOnChanges();
        expect(component.futureExercises).toEqual([futureExercise1, futureExercise2]);
        expect(component.currentExercises).toEqual([currentExercise]);
        expect(component.pastExercises).toEqual([pastExercise]);
    });

    it('should only display the latest five past exercises', () => {
        const pastExercise2 = { assessmentDueDate: dayjs().subtract(2, 'days') } as Exercise;
        const pastExercise3 = { dueDate: dayjs().subtract(5, 'days') } as Exercise;
        const pastExercise4 = { dueDate: dayjs().subtract(7, 'days') } as Exercise;
        const pastExercise5 = { assessmentDueDate: dayjs().subtract(3, 'days') } as Exercise;
        const pastExercise6 = { assessmentDueDate: dayjs().subtract(8, 'days') } as Exercise;
        component.courseWithExercises = {
            exercises: [pastExercise, pastExercise2, pastExercise3, pastExercise4, pastExercise5, pastExercise6],
        } as Course;

        component.ngOnChanges();
        expect(component.pastExercises).toEqual([pastExercise, pastExercise2, pastExercise5, pastExercise3, pastExercise4]);
    });
});
