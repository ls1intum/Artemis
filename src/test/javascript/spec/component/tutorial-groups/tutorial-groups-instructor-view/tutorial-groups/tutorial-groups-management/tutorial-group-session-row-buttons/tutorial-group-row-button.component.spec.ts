import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupRowButtonsComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/tutorial-groups-management/tutorial-group-row-buttons/tutorial-group-row-buttons.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockRouterLinkDirective } from '../../../../../../helpers/mocks/directive/mock-router-link.directive';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('TutorialGroupRowButtonsComponent', () => {
    let fixture: ComponentFixture<TutorialGroupRowButtonsComponent>;
    let component: TutorialGroupRowButtonsComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [
                TutorialGroupRowButtonsComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(FaIconComponent),
                MockRouterLinkDirective,
                MockDirective(DeleteButtonDirective),
            ],
            providers: [MockProvider(TutorialGroupsService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorialGroupRowButtonsComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should call delete and emit deleted event', () => {
        const tutorialGroupService = TestBed.inject(TutorialGroupsService);
        const httpResponse: HttpResponse<void> = new HttpResponse({});
        const deleteSpy = jest.spyOn(tutorialGroupService, 'delete').mockReturnValue(of(httpResponse));
        const deleteEventSpy = jest.spyOn(component.tutorialGroupDeleted, 'emit');

        const tutorialGroup = new TutorialGroup();
        tutorialGroup.id = 1;
        const courseId = 1;
        component.courseId = courseId;
        component.tutorialGroup = tutorialGroup;

        fixture.detectChanges();
        component.deleteTutorialGroup();
        expect(deleteSpy).toHaveBeenCalledWith(courseId, tutorialGroup.id);
        expect(deleteEventSpy).toHaveBeenCalledOnce();
    });
});
