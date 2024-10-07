import { ComponentFixture, TestBed } from '@angular/core/testing';
import {} from '@angular/router/testing';
import { CourseTutorialGroupCardComponent } from 'app/overview/course-tutorial-groups/course-tutorial-group-card/course-tutorial-group-card.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { generateExampleTutorialGroup } from '../helpers/tutorialGroupExampleModels';
import { User } from 'app/core/user/user.model';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { Course } from 'app/entities/course.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockComponent, MockDirective } from 'ng-mocks';
import { TranslatePipeMock } from '../../../helpers/mocks/service/mock-translate.service';
import { RouterModule } from '@angular/router';

describe('CourseTutorialGroupCardComponent', () => {
    let component: CourseTutorialGroupCardComponent;
    let fixture: ComponentFixture<CourseTutorialGroupCardComponent>;
    let exampleTutorialGroup: TutorialGroup;
    let exampleTA: User;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [RouterModule.forRoot([])],
            declarations: [CourseTutorialGroupCardComponent, MockComponent(FaIconComponent), TranslatePipeMock, MockDirective(TranslateDirective)],
            providers: [
                {
                    provide: TranslateService,
                    useValue: {
                        instant: (key: string) => key,
                        get: (key: string) => key,
                    },
                },
            ],
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
