import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { generateExampleTutorialGroup } from '../helpers/tutorialGroupExampleModels';
import { RouterModule } from '@angular/router';
import { TutorialGroupRowComponent } from 'app/tutorialgroup/shared/tutorial-groups-table/tutorial-group-row/tutorial-group-row.component';
import { TutorialGroupUtilizationIndicatorComponent } from 'app/tutorialgroup/shared/tutorial-group-utilization-indicator/tutorial-group-utilization-indicator.component';
import { MeetingPatternPipe } from 'app/tutorialgroup/shared/meeting-pattern.pipe';

describe('TutorialGroupRowComponent', () => {
    let component: TutorialGroupRowComponent;
    let fixture: ComponentFixture<TutorialGroupRowComponent>;
    let tutorialGroup: TutorialGroup;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [RouterModule.forRoot([])],
            declarations: [
                TutorialGroupRowComponent,
                MockComponent(TutorialGroupUtilizationIndicatorComponent),
                MockPipe(ArtemisDatePipe),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(MeetingPatternPipe),
            ],
            providers: [],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialGroupRowComponent);
        component = fixture.componentInstance;
        tutorialGroup = generateExampleTutorialGroup({});
        component.tutorialGroup = tutorialGroup;
        component.showIdColumn = true;
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
