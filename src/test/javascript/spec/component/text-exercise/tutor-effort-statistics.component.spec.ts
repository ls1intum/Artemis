import { ComponentFixture, TestBed, getTestBed } from '@angular/core/testing';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockPipe, MockDirective } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TutorEffortStatisticsComponent } from 'app/exercises/text/manage/tutor-effort/tutor-effort-statistics.component';
import { ArtemisTestModule } from '../../test.module';
import { MockHasAnyAuthorityDirective } from '../../helpers/mocks/directive/mock-has-any-authority.directive';
import { ChartsModule } from 'ng2-charts';
import { TutorEffort } from 'app/entities/tutor-effort.model';

describe('TutorEffortStatisticsComponent', () => {
    let fixture: ComponentFixture<TutorEffortStatisticsComponent>;
    let component: TutorEffortStatisticsComponent;
    let httpMock: HttpTestingController;
    let compiled: any;
    const tutorEffortsMocked = [
        {
            courseId: 1,
            exerciseId: 1,
            numberOfSubmissionsAssessed: 1,
            totalTimeSpentMinutes: 1,
        },
        {
            courseId: 1,
            exerciseId: 1,
            numberOfSubmissionsAssessed: 1,
            totalTimeSpentMinutes: 20,
        },
        {
            courseId: 1,
            exerciseId: 1,
            numberOfSubmissionsAssessed: 1,
            totalTimeSpentMinutes: 30,
        },
    ];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ChartsModule, ArtemisTestModule, HttpClientTestingModule],
            declarations: [TutorEffortStatisticsComponent, MockPipe(ArtemisTranslatePipe), MockDirective(MockHasAnyAuthorityDirective)],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorEffortStatisticsComponent);
                component = fixture.componentInstance;
                compiled = fixture.debugElement.nativeElement;
                fixture.detectChanges();
            });
        httpMock = getTestBed().get(HttpTestingController);
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).toBeTruthy();
    });

    it('should call loadTutorEfforts', () => {
        component.currentExerciseId = 1;
        component.currentCourseId = 1;
        component.loadTutorEfforts();
        httpMock.expectOne({ url: `api/courses/1/exercises/1/tutor-effort`, method: 'GET' });
    });

    it('should check tutor effort response handler with non-empty input', () => {
        component.currentExerciseId = 1;
        const expected = tutorEffortsMocked;
        component.handleTutorEffortResponse(expected);
        expect(component.tutorEfforts).toStrictEqual(expected);
        expect(component.numberOfSubmissions).toEqual(3);
        expect(component.totalTimeSpent).toEqual(51);
        expect(component.averageTimeSpent).toEqual(Math.round((component.numberOfSubmissions / component.totalTimeSpent + Number.EPSILON) * 100) / 100);
        expect(component.chartDataSets[0].data).toStrictEqual(component.effortDistribution);
    });

    it('should check tutor effort response handler with empty input', () => {
        component.currentExerciseId = 1;
        const expected: TutorEffort[] = [];
        component.handleTutorEffortResponse(expected);
        expect(component.tutorEfforts).toStrictEqual(expected);
        expect(component.numberOfSubmissions).toEqual(0);
        expect(component.totalTimeSpent).toEqual(0);
        expect(component.averageTimeSpent).toEqual(0);
        expect(component.chartDataSets[0].data).toStrictEqual(component.effortDistribution);
    });

    it('should call loadNumberOfTutorsInvolved', () => {
        component.currentExerciseId = 1;
        component.currentCourseId = 1;
        component.loadNumberOfTutorsInvolved();
        httpMock.expectOne({ url: `/analytics/text-assessment/events/courses/1/exercises/1`, method: 'GET' });
    });

    it('should call setTutorsInvolved', () => {
        component.setTutorsInvolved(1);
        expect(component.numberOfTutorsInvolvedInCourse).toEqual(1);
    });

    it('should call setTutorsInvolved with invalid parameters', () => {
        component.setTutorsInvolved(undefined);
        expect(component.numberOfTutorsInvolvedInCourse).toBeUndefined();
    });

    it('should call distributeEffortToSets', () => {
        component.currentExerciseId = 1;
        component.tutorEfforts = tutorEffortsMocked;
        const expected = new Array<number>(13).fill(0);
        expected[0] = 1;
        expected[2] = 1;
        expected[3] = 1;
        component.distributeEffortToSets();
        expect(component.effortDistribution).toStrictEqual(expected);
    });

    it('should show the table headers if tutor efforts list is not empty', () => {
        component.tutorEfforts = tutorEffortsMocked;
        fixture.detectChanges();
        const numberOfSubmissionsAssessed = compiled.querySelector('[jhiTranslate$=numberOfSubmissionsAssessed]');
        const totalTimeSpent = compiled.querySelector('[jhiTranslate$=totalTimeSpent]');
        const averageTime = compiled.querySelector('[jhiTranslate$=averageTime]');
        const numberOfTutorsInvolved = compiled.querySelector('[jhiTranslate$=numberOfTutorsInvolved]');
        expect(numberOfSubmissionsAssessed).toBeTruthy();
        expect(totalTimeSpent).toBeTruthy();
        expect(averageTime).toBeTruthy();
        expect(numberOfTutorsInvolved).toBeTruthy();
    });

    it('should show the no data message if tutor efforts list is empty', () => {
        component.tutorEfforts = [];
        fixture.detectChanges();
        const noData = compiled.querySelector('[jhiTranslate$=noData]');
        const numberOfSubmissionsAssessed = compiled.querySelector('[jhiTranslate$=numberOfSubmissionsAssessed]');
        expect(noData).toBeTruthy();
        expect(numberOfSubmissionsAssessed).toBeFalsy();
    });
});
