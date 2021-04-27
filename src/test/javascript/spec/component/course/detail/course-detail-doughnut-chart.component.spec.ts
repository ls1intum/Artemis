import { RouterTestingModule } from '@angular/router/testing';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe.ts';
import * as chai from 'chai';
import { MockPipe } from 'ng-mocks';
import { ChartsModule } from 'ng2-charts';
import { MomentModule } from 'ngx-moment';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../test.module';
import { CourseDetailDoughnutChartComponent } from 'app/course/manage/detail/course-detail-doughnut-chart.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('CourseDetailDoughnutChartComponent', () => {
    let fixture: ComponentFixture<CourseDetailDoughnutChartComponent>;
    let component: CourseDetailDoughnutChartComponent;

    const initialStats = [26, 47, 78, 66];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([]), MomentModule, ChartsModule],
            declarations: [CourseDetailDoughnutChartComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseDetailDoughnutChartComponent);
                component = fixture.componentInstance;
            });
    });

    beforeEach(() => {
        component.doughnutChartTitle = 'Assessments';
        component.currentPercentage = 80;
        component.currentAbsolute = 80;
        component.currentMax = 100;
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).to.be.ok;
    });
});
