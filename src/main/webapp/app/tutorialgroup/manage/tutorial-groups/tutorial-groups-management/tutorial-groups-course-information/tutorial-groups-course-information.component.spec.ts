import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TutorialGroupsCourseInformationComponent } from 'app/tutorialgroup/manage/tutorial-groups/tutorial-groups-management/tutorial-groups-course-information/tutorial-groups-course-information.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { generateExampleTutorialGroup } from 'test/helpers/sample/tutorialgroup/tutorialGroupExampleModels';
import { Component, input } from '@angular/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

@Component({ selector: 'jhi-side-panel', template: '' })
class MockSidePanelComponent {
    panelHeader = input<string>();
    panelDescriptionHeader = input<string>();
}

describe('TutorialGroupsCourseInformationComponent', () => {
    let component: TutorialGroupsCourseInformationComponent;
    let fixture: ComponentFixture<TutorialGroupsCourseInformationComponent>;

    let tutorialGroupTwo: TutorialGroup;
    let tutorialGroupOne: TutorialGroup;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [TutorialGroupsCourseInformationComponent, MockSidePanelComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialGroupsCourseInformationComponent);
        component = fixture.componentInstance;
        tutorialGroupOne = generateExampleTutorialGroup({ id: 1, numberOfRegisteredUsers: 5 });
        tutorialGroupTwo = generateExampleTutorialGroup({ id: 2, numberOfRegisteredUsers: 10 });
        fixture.componentRef.setInput('tutorialGroups', [tutorialGroupOne, tutorialGroupTwo]);
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should add the number of registered students together', () => {
        expect(component.totalNumberOfRegistrations).toBe(15);
    });
});
