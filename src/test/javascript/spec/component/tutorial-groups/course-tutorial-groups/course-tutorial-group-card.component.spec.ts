import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseTutorialGroupCardComponent } from 'app/overview/course-tutorial-groups/course-tutorial-group-card/course-tutorial-group-card.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { Router } from '@angular/router';
import { generateExampleTutorialGroup } from '../helpers/tutorialGroupExampleModels';
import { User } from 'app/core/user/user.model';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { SimpleChange } from '@angular/core';

describe('CourseTutorialGroupCardComponent', () => {
    let component: CourseTutorialGroupCardComponent;
    let fixture: ComponentFixture<CourseTutorialGroupCardComponent>;
    let exampleTutorialGroup: TutorialGroup;
    let exampleTA: User;

    const router = new MockRouter();
    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [CourseTutorialGroupCardComponent, MockComponent(FaIconComponent), MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: Router, useValue: router }],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseTutorialGroupCardComponent);
        component = fixture.componentInstance;
        exampleTA = { id: 1, name: 'TA' } as User;
        exampleTutorialGroup = generateExampleTutorialGroup({ teachingAssistant: exampleTA });
        component.tutorialGroup = exampleTutorialGroup;
        component.courseId = 1;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should load the tutorial group and teaching assistants from input', () => {
        component.ngOnChanges({
            tutorialGroup: new SimpleChange(exampleTutorialGroup, exampleTutorialGroup, false),
        });
        expect(component.tutorialGroup).toEqual(exampleTutorialGroup);
        expect(component.teachingAssistant).toEqual(exampleTA);
    });

    it('should navigate to tutorial group detail page when card is clicked', () => {
        const navigateSpy = jest.spyOn(router, 'navigate');
        const card = fixture.debugElement.nativeElement.querySelector('.card-body');
        card.click();
        expect(navigateSpy).toHaveBeenCalledWith(['/courses', 1, 'tutorial-groups', exampleTutorialGroup.id]);
    });
});
