import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { TutorialGroupRowComponent } from 'app/course/tutorial-groups/shared/tutorial-groups-table/tutorial-group-row/tutorial-group-row.component';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { generateExampleTutorialGroup } from '../helpers/tutorialGroupExampleModels';
import { TutorialGroupUtilizationIndicatorComponent } from 'app/course/tutorial-groups/shared/tutorial-group-utilization-indicator/tutorial-group-utilization-indicator.component';
import { MeetingPatternPipe } from 'app/course/tutorial-groups/shared/meeting-pattern.pipe';

describe('TutorialGroupRowComponent', () => {
    let component: TutorialGroupRowComponent;
    let fixture: ComponentFixture<TutorialGroupRowComponent>;
    let tutorialGroup: TutorialGroup;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [RouterTestingModule.withRoutes([])],
            declarations: [
                TutorialGroupRowComponent,
                MockComponent(TutorialGroupUtilizationIndicatorComponent),
                MockPipe(ArtemisDatePipe),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(MeetingPatternPipe),
            ],
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
