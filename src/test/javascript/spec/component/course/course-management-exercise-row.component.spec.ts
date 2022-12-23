import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { CourseManagementExerciseRowComponent } from 'app/course/manage/overview/course-management-exercise-row.component';
import { ProgressBarComponent } from 'app/shared/dashboards/tutor-participation-graph/progress-bar/progress-bar.component';
import { MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';
import { CourseManagementOverviewExerciseStatisticsDTO } from 'app/course/manage/overview/course-management-overview-exercise-statistics-dto.model';
import { Exercise } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';

describe('CourseManagementExerciseRowComponent', () => {
    let fixture: ComponentFixture<CourseManagementExerciseRowComponent>;
    let component: CourseManagementExerciseRowComponent;

    const exerciseDetails = {
        teamMode: false,
        title: 'ModelingExercise',
    } as Exercise;

    const exerciseStatisticsDTO = new CourseManagementOverviewExerciseStatisticsDTO();
    exerciseStatisticsDTO.averageScoreInPercent = 50;
    exerciseStatisticsDTO.exerciseMaxPoints = 10;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                CourseManagementExerciseRowComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockComponent(ProgressBarComponent),
                MockRouterLinkDirective,
                MockPipe(ArtemisTimeAgoPipe),
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
        component.details = exerciseDetails;
        component.ngOnChanges();
        component.statistic = exerciseStatisticsDTO;
        component.ngOnChanges();
        expect(component.averageScoreNumerator).toBe(5);
    });
});
