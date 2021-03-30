import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { CourseManagementExerciseRowComponent } from 'app/course/manage/overview/course-management-exercise-row.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { CourseManagementCardComponent } from 'app/course/manage/overview/course-management-card.component';
import { CourseManagementStatisticsComponent } from 'app/course/manage/overview/course-management-statistics.component';
import { ChartsModule } from 'ng2-charts';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

chai.use(sinonChai);
const expect = chai.expect;

describe('CourseManagementExerciseStatisticsComponent', () => {
    let fixture: ComponentFixture<CourseManagementStatisticsComponent>;
    let component: CourseManagementStatisticsComponent;

    const courseId = 1;
    const amountOfStudentsInCourse = 25;
    const initialStats = [0, 11, 9, 23];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ChartsModule],
            declarations: [
                CourseManagementStatisticsComponent,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(NgbTooltip),
                MockComponent(CourseManagementExerciseRowComponent),
                MockComponent(CourseManagementCardComponent),
            ],
            providers: [{ provide: LocalStorageService, useClass: MockSyncStorage }, { provide: SessionStorageService, useClass: MockSyncStorage }, MockProvider(TranslateService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseManagementStatisticsComponent);
                component = fixture.componentInstance;
            });
    });

    it('should initialize component and load values', () => {
        // Provide the @Input data
        component.courseId = courseId;
        component.amountOfStudentsInCourse = amountOfStudentsInCourse;
        component.initialStats = initialStats;

        fixture.detectChanges();
        expect(component).to.be.ok;

        expect(component.dataForSpanType).to.deep.equal([0, 44, 36, 92]);
        expect(component.chartData[0].label).to.equal(component.amountOfStudents);
        expect(component.chartData[0].data).to.deep.equal([0, 44, 36, 92]);

        // Test formatting
        expect(component.barChartOptions.scales.yAxes[0].ticks.callback(44)).to.equal('44%');
        expect(component.barChartOptions.tooltips.callbacks.label({ index: 2 })).to.equal(' ' + initialStats[2]);
    });

    it('should react to changes', () => {
        // Provide the @Input data
        component.courseId = courseId;
        fixture.detectChanges();

        component.initialStats = [];
        component.amountOfStudentsInCourse = 0;
        component.ngOnChanges();

        expect(component.loading).to.be.false;
        expect(component.dataForSpanType).to.deep.equal([0, 0, 0, 0]);
    });
});
