import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ClusterStatisticsComponent } from 'app/exercises/text/manage/cluster-statistics/cluster-statistics.component';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ProgressBarComponent } from 'app/shared/dashboards/tutor-participation-graph/progress-bar/progress-bar.component';
import { TextExerciseService } from 'app/exercises/text/manage/text-exercise/text-exercise.service';
import { ActivatedRoute } from '@angular/router';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';

describe('TextClusterStatisticsComponent', () => {
    let fixture: ComponentFixture<ClusterStatisticsComponent>;
    let component: ClusterStatisticsComponent;
    let compiled: any;
    let textExerciseService: TextExerciseService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
            declarations: [ClusterStatisticsComponent, MockPipe(ArtemisTranslatePipe), MockComponent(ProgressBarComponent)],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                {
                    provide: ActivatedRoute,
                    useValue: new MockActivatedRoute({ exerciseId: 222 }),
                },
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
                textExerciseService = fixture.debugElement.injector.get(TextExerciseService);
            });
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });

    it('should call loadClusterFromExercise', () => {
        const getClusterStatsSpy = jest.spyOn(textExerciseService, 'getClusterStats');
        component.loadClusterFromExercise(1);
        expect(getClusterStatsSpy).toHaveBeenCalledWith(1);
    });

    it('should call setClusterDisabledPredicate', () => {
        const getClusterStatsSpy = jest.spyOn(textExerciseService, 'setClusterDisabledPredicate');
        component.setClusterDisabledPredicate(1, true);
        expect(getClusterStatsSpy).toHaveBeenCalledWith(222, 1, true);
    });

    it('should show a not found message in case the cluster statistics is empty', () => {
        component.clusters = [];
        component.currentExerciseId = 1;

        const noClusterFoundComponent = compiled.querySelector('[jhiTranslate$=noClustersFound]');
        expect(noClusterFoundComponent).not.toBeNull();
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
        expect(firstColumn).not.toBeNull();
        expect(secondColumn).not.toBeNull();
        expect(thirdColumn).not.toBeNull();
        expect(noClustersFound).toBeNull();
    });
});
