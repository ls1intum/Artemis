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

describe('TutorEffortStatisticsComponent', () => {
    let fixture: ComponentFixture<TutorEffortStatisticsComponent>;
    let component: TutorEffortStatisticsComponent;
    let httpMock: HttpTestingController;
    let compiled: any;

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

    it('should call loadNumberOfTutorsInvolved', () => {
        component.currentExerciseId = 1;
        component.currentCourseId = 1;
        component.loadNumberOfTutorsInvolved();
        httpMock.expectOne({ url: `/analytics/text-assessment/events/courses/1/exercises/1`, method: 'GET' });
    });

    it('should call distributeEffortToSets', () => {
        component.currentExerciseId = 1;
        component.tutorEfforts = [
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
        const expected = new Array<number>(13).fill(0);
        expected[0] = 1;
        expected[2] = 1;
        expected[3] = 1;
        component.distributeEffortToSets();
        expect(component.effortDistribution).toStrictEqual(expected);
    });

    it('should show the table headers if tutor efforts list is not empty', () => {
        component.tutorEfforts = [
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
        const totalTimeSpent = compiled.querySelector('[jhiTranslate$=totalTimeSpent]');
        const averageTime = compiled.querySelector('[jhiTranslate$=averageTime]');
        const numberOfTutorsInvolved = compiled.querySelector('[jhiTranslate$=numberOfTutorsInvolved]');
        expect(noData).toBeTruthy();
        expect(numberOfSubmissionsAssessed).toBeFalsy();
        expect(totalTimeSpent).toBeFalsy();
        expect(averageTime).toBeFalsy();
        expect(numberOfTutorsInvolved).toBeFalsy();
    });
});
