import { ComponentFixture, TestBed, getTestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockPipe, MockComponent } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ClusterStatisticsComponent } from 'app/exercises/text/manage/cluster-statistics/cluster-statistics.component';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ProgressBarComponent } from 'app/shared/dashboards/tutor-participation-graph/progress-bar/progress-bar.component';
describe('TextClusterStatisticsComponent', () => {
    let fixture: ComponentFixture<ClusterStatisticsComponent>;
    let component: ClusterStatisticsComponent;
    let httpMock: HttpTestingController;
    let compiled: any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
            declarations: [ClusterStatisticsComponent, MockPipe(ArtemisTranslatePipe), MockComponent(ProgressBarComponent)],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ClusterStatisticsComponent);
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

    it('should call loadClusterFromExercise', () => {
        component.loadClusterFromExercise(1);
        httpMock.expectOne({ url: `api/text-exercises/1/cluster-statistics`, method: 'GET' });
    });

    it('should call setClusterDisabledPredicate', () => {
        component.setClusterDisabledPredicate(1, true);
        httpMock.expectOne({ url: `api/text-clusters/1?disabled=true`, method: 'PATCH' });
    });

    it('should show a not found message in case the cluster statistics is empty', () => {
        component.clusters = [];
        component.currentExerciseId = 1;

        const noClusterFoundComponent = compiled.querySelector('[jhiTranslate$=noClustersFound]');
        expect(noClusterFoundComponent).toBeTruthy();
    });

    it('should show the table elements in case the cluster statistics is not empty', () => {
        component.clusters = [
            {
                clusterId: 0,
                clusterSize: 5,
                numberOfAutomaticFeedbacks: 0,
                disabled: true,
            },
        ];

        component.currentExerciseId = 1;
        fixture.detectChanges();
        const firstColumn = compiled.querySelector('[jhiTranslate$=clusterId]');
        const secondColumn = compiled.querySelector('[jhiTranslate$=reusedFeedbackRatio]');
        const thirdColumn = compiled.querySelector('[jhiTranslate$=action]');
        const noClustersFound = compiled.querySelector('[jhiTranslate$=noClustersFound]');
        expect(firstColumn).toBeTruthy();
        expect(secondColumn).toBeTruthy();
        expect(thirdColumn).toBeTruthy();
        expect(noClustersFound).toBeFalsy();
    });
});
