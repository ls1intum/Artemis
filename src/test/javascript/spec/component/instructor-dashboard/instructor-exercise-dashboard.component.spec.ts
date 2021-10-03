import { ComponentFixture, TestBed } from '@angular/core/testing';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { MockComponent } from 'ng-mocks';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterModule } from '@angular/router';
import { SidePanelComponent } from 'app/shared/side-panel/side-panel.component';
import { TutorLeaderboardComponent } from 'app/shared/dashboards/tutor-leaderboard/tutor-leaderboard.component';
import { StatsForDashboard } from 'app/course/dashboards/stats-for-dashboard.model';
import { ChartsModule } from 'ng2-charts';
import { TranslateModule } from '@ngx-translate/core';
import { HeaderExercisePageWithDetailsComponent } from 'app/exercises/shared/exercise-headers/header-exercise-page-with-details.component';
import { InstructorExerciseDashboardComponent } from 'app/exercises/shared/dashboards/instructor/instructor-exercise-dashboard.component';
import { HeaderParticipationPageComponent } from 'app/exercises/shared/exercise-headers/header-participation-page.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('InstructorExerciseDashboardComponent', () => {
    let comp: InstructorExerciseDashboardComponent;
    let fixture: ComponentFixture<InstructorExerciseDashboardComponent>;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisSharedModule, RouterModule, ChartsModule, TranslateModule.forRoot()],
            declarations: [
                InstructorExerciseDashboardComponent,
                MockComponent(HeaderExercisePageWithDetailsComponent),
                MockComponent(HeaderParticipationPageComponent),
                MockComponent(SidePanelComponent),
                MockComponent(TutorLeaderboardComponent),
            ],
            providers: [],
        });
        fixture = TestBed.createComponent(InstructorExerciseDashboardComponent);
        comp = fixture.componentInstance;
    });

    it('Statistics are calculated correctly', () => {
        const stats = new StatsForDashboard();
        stats.numberOfSubmissions.inTime = 420;
        stats.numberOfSubmissions.late = 110;
        stats.totalNumberOfAssessments.inTime = 333;
        stats.totalNumberOfAssessments.late = 55;
        stats.numberOfAutomaticAssistedAssessments.inTime = 42;
        stats.numberOfAutomaticAssistedAssessments.late = 15;
        comp.stats = stats;
        comp.setStatistics();
        expect(comp.totalManualAssessmentPercentage.inTime).equal(69);
        expect(comp.totalManualAssessmentPercentage.late).equal(36);
        expect(comp.totalAutomaticAssessmentPercentage.inTime).equal(10);
        expect(comp.totalAutomaticAssessmentPercentage.late).equal(13);
        expect(comp.dataForAssessmentPieChart[0]).equal(142);
        expect(comp.dataForAssessmentPieChart[1]).equal(331);
        expect(comp.dataForAssessmentPieChart[2]).equal(57);
    });

    it('Statistics are calculated, and rounded towards zero correctly', () => {
        const stats = new StatsForDashboard();
        stats.numberOfSubmissions.inTime = 2162;
        stats.totalNumberOfAssessments.inTime = 2152;
        stats.numberOfAutomaticAssistedAssessments.inTime = 4;
        comp.stats = stats;
        comp.setStatistics();
        expect(comp.totalManualAssessmentPercentage.inTime).equal(99);
        expect(comp.totalAutomaticAssessmentPercentage.inTime).equal(0);
        expect(comp.dataForAssessmentPieChart[0]).equal(10);
        expect(comp.dataForAssessmentPieChart[1]).equal(2148);
        expect(comp.dataForAssessmentPieChart[2]).equal(4);
    });
});
