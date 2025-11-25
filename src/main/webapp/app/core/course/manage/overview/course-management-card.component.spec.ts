import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { CourseManagementExerciseRowComponent } from 'app/core/course/manage/overview/course-management-exercise-row.component';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { CourseManagementCardComponent } from 'app/core/course/manage/overview/course-management-card.component';
import { CourseManagementOverviewStatisticsComponent } from 'app/core/course/manage/overview/course-management-overview-statistics.component';
import dayjs from 'dayjs/esm';
import { CourseManagementOverviewStatisticsDto } from 'app/core/course/manage/overview/course-management-overview-statistics-dto.model';
import { CourseManagementOverviewExerciseStatisticsDTO } from 'app/core/course/manage/overview/course-management-overview-exercise-statistics-dto.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ImageComponent } from 'app/shared/image/image.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute } from '@angular/router';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

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
            imports: [FaIconComponent],
            declarations: [
                CourseManagementCardComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockRouterLinkDirective,
                MockComponent(CourseManagementExerciseRowComponent),
                MockComponent(CourseManagementOverviewStatisticsComponent),
                MockComponent(ImageComponent),
                MockDirective(TranslateDirective),
            ],
            providers: [
                LocalStorageService,
                SessionStorageService,
                MockProvider(TranslateService),
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: ProfileService, useClass: MockProfileService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseManagementCardComponent);
                component = fixture.componentInstance;
                component.course = course;
            });
    });

    it('should correctly categorize past, current, and future exercises and update statisticsPerExercise', () => {
        component.courseStatistics = courseStatisticsDTO;
        component.ngOnChanges();
        expect(component.statisticsPerExercise.get(exerciseDTO.exerciseId!)).toEqual(exerciseDTO);

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

    it('should set courseColor as soon as course is set', () => {
        component.course = course;
        component.ngOnChanges();
        expect(component.courseColor).toBe('red');
    });

    it('should use default color if the course does not have a color', () => {
        const course = new Course();
        course.id = 1;

        component.course = course;
        component.ngOnChanges();
        expect(component.courseColor).toBe('#3E8ACC');
    });

    it('should not display loading spinner if courseWithExercises and courseStatistics are defined', () => {
        component.courseWithExercises = course;
        component.courseStatistics = courseStatisticsDTO;
        fixture.detectChanges();
        expect(fixture.debugElement.nativeElement.querySelector('.loading-spinner')).toBeFalsy();
    });

    it('should display loading spinner if courseWithExercises is undefined', () => {
        component.courseStatistics = courseStatisticsDTO;
        fixture.detectChanges();
        expect(fixture.debugElement.nativeElement.querySelector('.loading-spinner')).toBeTruthy();
    });

    it('should display loading spinner if courseStatistics is undefined', () => {
        component.courseWithExercises = course;
        fixture.detectChanges();
        expect(fixture.debugElement.nativeElement.querySelector('.loading-spinner')).toBeTruthy();
    });
});
