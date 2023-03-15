import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TutorialGroupUtilizationIndicatorComponent } from 'app/course/tutorial-groups/shared/tutorial-group-utilization-indicator/tutorial-group-utilization-indicator.component';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { VerticalProgressBarComponent } from 'app/shared/vertical-progress-bar/vertical-progress-bar.component';
import { MockComponent } from 'ng-mocks';
import { generateExampleTutorialGroup } from '../helpers/tutorialGroupExampleModels';

describe('TutorialGroupUtilizationIndicatorComponent', () => {
    let component: TutorialGroupUtilizationIndicatorComponent;
    let fixture: ComponentFixture<TutorialGroupUtilizationIndicatorComponent>;
    let tutorialGroup: TutorialGroup;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [TutorialGroupUtilizationIndicatorComponent, MockComponent(VerticalProgressBarComponent)],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialGroupUtilizationIndicatorComponent);
        component = fixture.componentInstance;
        tutorialGroup = generateExampleTutorialGroup({});
        component.tutorialGroup = tutorialGroup;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
