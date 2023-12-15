import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { CourseTutorialGroupCardComponent } from 'app/overview/course-tutorial-groups/course-tutorial-group-card/course-tutorial-group-card.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { generateExampleTutorialGroup } from '../helpers/tutorialGroupExampleModels';
import { User } from 'app/core/user/user.model';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { Course } from 'app/entities/course.model';

describe('CourseTutorialGroupCardComponent', () => {
    let component: CourseTutorialGroupCardComponent;
    let fixture: ComponentFixture<CourseTutorialGroupCardComponent>;
    let exampleTutorialGroup: TutorialGroup;
    let exampleTA: User;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [RouterTestingModule.withRoutes([])],
            declarations: [CourseTutorialGroupCardComponent, MockComponent(FaIconComponent), MockPipe(ArtemisTranslatePipe)],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseTutorialGroupCardComponent);
        component = fixture.componentInstance;
        exampleTA = { id: 1, name: 'TA' } as User;
        exampleTutorialGroup = generateExampleTutorialGroup({ teachingAssistant: exampleTA });
        component.tutorialGroup = exampleTutorialGroup;
        component.course = { id: 1 } as Course;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
