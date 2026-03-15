import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { TranslateService } from '@ngx-translate/core';
import { CourseManagementCardComponent } from 'app/core/course/manage/overview/course-management-card.component';
import dayjs from 'dayjs/esm';
import { CourseManagementOverviewStatisticsDto } from 'app/core/course/manage/overview/course-management-overview-statistics-dto.model';
import { CourseManagementOverviewExerciseStatisticsDTO } from 'app/core/course/manage/overview/course-management-overview-exercise-statistics-dto.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute, RouterLink, RouterModule } from '@angular/router';
import { NgStyle } from '@angular/common';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MODULE_FEATURE_ATLAS, MODULE_FEATURE_EXAM, MODULE_FEATURE_LECTURE, MODULE_FEATURE_TUTORIALGROUP } from 'app/app.constants';
import { ComponentRef } from '@angular/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ImageComponent } from 'app/shared/image/image.component';
import { CourseManagementExerciseRowComponent } from 'app/core/course/manage/overview/course-management-exercise-row.component';
import { CourseManagementOverviewStatisticsComponent } from 'app/core/course/manage/overview/course-management-overview-statistics.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FeatureToggleHideDirective } from 'app/shared/feature-toggle/feature-toggle-hide.directive';
import { FeatureOverlayComponent } from 'app/shared/components/feature-overlay/feature-overlay.component';
describe('CourseManagementCardComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<CourseManagementCardComponent>;
    let component: CourseManagementCardComponent;
    let componentRef: ComponentRef<CourseManagementCardComponent>;

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

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [CourseManagementCardComponent, RouterModule.forRoot([])],
            providers: [
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: ProfileService, useClass: MockProfileService },
            ],
        }).overrideComponent(CourseManagementCardComponent, {
            set: {
                imports: [
                    NgStyle,
                    RouterLink,
                    MockComponent(ImageComponent),
                    MockDirective(TranslateDirective),
                    MockComponent(FaIconComponent),
                    MockComponent(CourseManagementExerciseRowComponent),
                    MockComponent(CourseManagementOverviewStatisticsComponent),
                    MockDirective(NgbTooltip),
                    MockDirective(FeatureToggleHideDirective),
                    MockPipe(ArtemisDatePipe),
                    MockPipe(ArtemisTranslatePipe),
                    MockComponent(FeatureOverlayComponent),
                ],
            },
        });
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(CourseManagementCardComponent);
        component = fixture.componentInstance;
        componentRef = fixture.componentRef;
        componentRef.setInput('course', course);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should correctly categorize past, current, and future exercises and update statisticsPerExercise', async () => {
        componentRef.setInput('courseStatistics', courseStatisticsDTO);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(component.statisticsPerExercise().get(exerciseDTO.exerciseId!)).toEqual(exerciseDTO);

        componentRef.setInput('courseWithExercises', course);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(component.futureExercises()).toEqual([futureExercise1, futureExercise2]);
        expect(component.currentExercises()).toEqual([currentExercise]);
        expect(component.pastExercises()).toEqual([pastExercise]);
    });

    it('should only display the latest five past exercises', async () => {
        const pastExercise2 = { assessmentDueDate: dayjs().subtract(2, 'days') } as Exercise;
        const pastExercise3 = { dueDate: dayjs().subtract(5, 'days') } as Exercise;
        const pastExercise4 = { dueDate: dayjs().subtract(7, 'days') } as Exercise;
        const pastExercise5 = { assessmentDueDate: dayjs().subtract(3, 'days') } as Exercise;
        const pastExercise6 = { assessmentDueDate: dayjs().subtract(8, 'days') } as Exercise;
        componentRef.setInput('courseWithExercises', {
            exercises: [pastExercise, pastExercise2, pastExercise3, pastExercise4, pastExercise5, pastExercise6],
        } as Course);

        fixture.detectChanges();
        await fixture.whenStable();
        expect(component.pastExercises()).toEqual([pastExercise, pastExercise2, pastExercise5, pastExercise3, pastExercise4]);
    });

    it('should set courseColor as soon as course is set', () => {
        componentRef.setInput('course', course);
        fixture.detectChanges();
        expect(component.courseColor()).toBe('red');
    });

    it('should use default color if the course does not have a color', () => {
        const courseWithoutColor = new Course();
        courseWithoutColor.id = 1;

        componentRef.setInput('course', courseWithoutColor);
        fixture.detectChanges();
        expect(component.courseColor()).toBe('#3E8ACC');
    });

    it('should not display loading spinner if courseWithExercises and courseStatistics are defined', () => {
        componentRef.setInput('courseWithExercises', course);
        componentRef.setInput('courseStatistics', courseStatisticsDTO);
        fixture.detectChanges();
        expect(fixture.debugElement.nativeElement.querySelector('.loading-spinner')).toBeFalsy();
    });

    it('should display loading spinner if courseWithExercises is undefined', () => {
        componentRef.setInput('courseStatistics', courseStatisticsDTO);
        fixture.detectChanges();
        expect(fixture.debugElement.nativeElement.querySelector('.loading-spinner')).toBeTruthy();
    });

    it('should display loading spinner if courseStatistics is undefined', () => {
        componentRef.setInput('courseWithExercises', course);
        fixture.detectChanges();
        expect(fixture.debugElement.nativeElement.querySelector('.loading-spinner')).toBeTruthy();
    });

    describe('module feature toggles', () => {
        let profileService: ProfileService;

        beforeEach(() => {
            profileService = TestBed.inject(ProfileService);
        });

        it('should initialize module feature toggles from ProfileService', () => {
            const getProfileInfoSpy = vi.spyOn(profileService, 'getProfileInfo');
            getProfileInfoSpy.mockReturnValue({
                activeModuleFeatures: [MODULE_FEATURE_ATLAS, MODULE_FEATURE_EXAM, MODULE_FEATURE_LECTURE, MODULE_FEATURE_TUTORIALGROUP],
            } as any);

            // Recreate component to pick up the new mocked values
            fixture = TestBed.createComponent(CourseManagementCardComponent);
            component = fixture.componentInstance;
            componentRef = fixture.componentRef;
            componentRef.setInput('course', course);
            fixture.detectChanges();

            expect(component.atlasEnabled).toBe(true);
            expect(component.examEnabled).toBe(true);
            expect(component.lectureEnabled).toBe(true);
            expect(component.tutorialGroupEnabled).toBe(true);
        });

        it('should set module feature toggles to false when features are not active', () => {
            const getProfileInfoSpy = vi.spyOn(profileService, 'getProfileInfo');
            getProfileInfoSpy.mockReturnValue({
                activeModuleFeatures: [],
            } as any);

            // Recreate component to pick up the new mocked values
            fixture = TestBed.createComponent(CourseManagementCardComponent);
            component = fixture.componentInstance;
            componentRef = fixture.componentRef;
            componentRef.setInput('course', course);
            fixture.detectChanges();

            expect(component.atlasEnabled).toBe(false);
            expect(component.examEnabled).toBe(false);
            expect(component.lectureEnabled).toBe(false);
            expect(component.tutorialGroupEnabled).toBe(false);
        });
    });
});
