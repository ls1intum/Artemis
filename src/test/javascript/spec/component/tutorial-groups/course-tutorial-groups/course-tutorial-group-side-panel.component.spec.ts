import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CourseTutorialGroupSidePanelComponent } from 'app/overview/course-tutorial-groups/course-tutorial-group-side-panel/course-tutorial-group-side-panel.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { generateExampleTutorialGroup } from '../helpers/tutorialGroupExampleModels';
import { Component, Input } from '@angular/core';

@Component({ selector: 'jhi-side-panel', template: '' })
class MockSidePanel {
    @Input() panelHeader: string;
    @Input() panelDescriptionHeader?: string;
}

describe('CourseTutorialGroupSidePanelComponent', () => {
    let component: CourseTutorialGroupSidePanelComponent;
    let fixture: ComponentFixture<CourseTutorialGroupSidePanelComponent>;

    let tutorialGroupTwo: TutorialGroup;
    let tutorialGroupOne: TutorialGroup;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [CourseTutorialGroupSidePanelComponent, MockSidePanel, MockPipe(ArtemisTranslatePipe)],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseTutorialGroupSidePanelComponent);
        component = fixture.componentInstance;
        tutorialGroupOne = generateExampleTutorialGroup({ id: 1, numberOfRegisteredUsers: 5 });
        tutorialGroupTwo = generateExampleTutorialGroup({ id: 2, numberOfRegisteredUsers: 10 });
        component.tutorialGroups = [tutorialGroupOne, tutorialGroupTwo];
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should add the number of registered students together', () => {
        expect(component.totalNumberOfRegistrations).toBe(15);
    });
});
