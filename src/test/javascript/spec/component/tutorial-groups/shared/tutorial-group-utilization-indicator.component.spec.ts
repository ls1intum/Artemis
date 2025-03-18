import { ComponentFixture, TestBed } from '@angular/core/testing';

import { VerticalProgressBarComponent } from 'app/shared/vertical-progress-bar/vertical-progress-bar.component';
import { MockComponent, MockPipe } from 'ng-mocks';
import { generateExampleTutorialGroup } from '../helpers/tutorialGroupExampleModels';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TutorialGroupUtilizationIndicatorComponent } from 'app/tutorialgroup/shared/tutorial-group-utilization-indicator/tutorial-group-utilization-indicator.component';

describe('TutorialGroupUtilizationIndicatorComponent', () => {
    let component: TutorialGroupUtilizationIndicatorComponent;
    let fixture: ComponentFixture<TutorialGroupUtilizationIndicatorComponent>;
    let tutorialGroup: TutorialGroup;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [TutorialGroupUtilizationIndicatorComponent, MockComponent(VerticalProgressBarComponent), MockPipe(ArtemisTranslatePipe)],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialGroupUtilizationIndicatorComponent);
        component = fixture.componentInstance;
        tutorialGroup = generateExampleTutorialGroup({});
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
