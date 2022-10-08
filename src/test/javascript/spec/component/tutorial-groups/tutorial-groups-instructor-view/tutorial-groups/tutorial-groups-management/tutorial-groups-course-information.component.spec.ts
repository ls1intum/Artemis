import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TutorialGroupsCourseInformationComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/tutorial-groups-management/tutorial-groups-course-information/tutorial-groups-course-information.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { generateExampleTutorialGroup } from '../../../helpers/tutorialGroupExampleModels';
import { Component, Input } from '@angular/core';

@Component({ selector: 'jhi-side-panel', template: '' })
class MockSidePanel {
    @Input() panelHeader: string;
    @Input() panelDescriptionHeader?: string;
}

describe('TutorialGroupsCourseInformationComponent', () => {
    let component: TutorialGroupsCourseInformationComponent;
    let fixture: ComponentFixture<TutorialGroupsCourseInformationComponent>;

    let tutorialGroupTwo: TutorialGroup;
    let tutorialGroupOne: TutorialGroup;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [TutorialGroupsCourseInformationComponent, MockSidePanel, MockPipe(ArtemisTranslatePipe)],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialGroupsCourseInformationComponent);
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
