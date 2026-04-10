import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { TranslateService } from '@ngx-translate/core';
import { CourseManagementExerciseRowComponent, ExerciseRowType } from 'app/core/course/manage/overview/course-management-exercise-row.component';
import { CourseManagementOverviewExerciseStatisticsDTO } from 'app/core/course/manage/overview/course-management-overview-exercise-statistics-dto.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute, RouterLink, RouterModule } from '@angular/router';
import { NgClass } from '@angular/common';
import { ComponentRef } from '@angular/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ExerciseCategoriesComponent } from 'app/exercise/exercise-categories/exercise-categories.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ProgressBarComponent } from 'app/shared/dashboards/tutor-participation-graph/progress-bar/progress-bar.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';

describe('CourseManagementExerciseRowComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<CourseManagementExerciseRowComponent>;
    let component: CourseManagementExerciseRowComponent;
    let componentRef: ComponentRef<CourseManagementExerciseRowComponent>;

    const exerciseDetails = {
        teamMode: false,
        title: 'ModelingExercise',
    } as Exercise;

    const exerciseStatisticsDTO = new CourseManagementOverviewExerciseStatisticsDTO();
    exerciseStatisticsDTO.averageScoreInPercent = 50;
    exerciseStatisticsDTO.exerciseMaxPoints = 10;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [CourseManagementExerciseRowComponent, RouterModule.forRoot([])],
            providers: [LocalStorageService, { provide: TranslateService, useClass: MockTranslateService }, { provide: ActivatedRoute, useValue: new MockActivatedRoute() }],
        }).overrideComponent(CourseManagementExerciseRowComponent, {
            set: {
                imports: [
                    RouterLink,
                    NgClass,
                    MockComponent(FaIconComponent),
                    MockDirective(NgbTooltip),
                    MockComponent(ExerciseCategoriesComponent),
                    MockDirective(TranslateDirective),
                    MockComponent(ProgressBarComponent),
                    MockPipe(ArtemisDatePipe),
                    MockPipe(ArtemisTranslatePipe),
                    MockPipe(ArtemisTimeAgoPipe),
                ],
            },
        });
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(CourseManagementExerciseRowComponent);
        component = fixture.componentInstance;
        componentRef = fixture.componentRef;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize component', async () => {
        componentRef.setInput('course', new Course());
        componentRef.setInput('details', exerciseDetails);
        componentRef.setInput('rowType', ExerciseRowType.PAST);
        fixture.detectChanges();
        await fixture.whenStable();

        componentRef.setInput('statistic', exerciseStatisticsDTO);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.averageScoreNumerator()).toBe(5);
    });
});
