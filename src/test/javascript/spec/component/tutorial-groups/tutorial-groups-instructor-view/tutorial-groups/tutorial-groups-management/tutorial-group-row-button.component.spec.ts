import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupRowButtonsComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/tutorial-groups-management/tutorial-group-row-buttons/tutorial-group-row-buttons.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockRouterLinkDirective } from '../../../../../helpers/mocks/directive/mock-router-link.directive';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { MockRouter } from '../../../../../helpers/mocks/mock-router';
import { Router } from '@angular/router';
import { generateExampleTutorialGroup } from '../../../helpers/tutorialGroupExampleModels';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('TutorialGroupRowButtonsComponent', () => {
    let fixture: ComponentFixture<TutorialGroupRowButtonsComponent>;
    let component: TutorialGroupRowButtonsComponent;
    const courseId = 1;
    let tutorialGroup: TutorialGroup;

    const router = new MockRouter();

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [
                TutorialGroupRowButtonsComponent,
                MockComponent(FaIconComponent),
                MockRouterLinkDirective,
                MockDirective(DeleteButtonDirective),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [MockProvider(TutorialGroupsService), { provide: Router, useValue: router }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorialGroupRowButtonsComponent);
                component = fixture.componentInstance;
                tutorialGroup = generateExampleTutorialGroup();
                setInputValues();
            });
    });
    const setInputValues = () => {
        component.courseId = courseId;
        component.tutorialGroup = tutorialGroup;
    };

    it('should navigate to session management', fakeAsync(() => {
        testButtonLeadsToRouting('sessions-' + tutorialGroup.id, ['/course-management', courseId, 'tutorial-groups-management', tutorialGroup.id!, 'sessions']);
    }));

    it('should navigate to registrations management', fakeAsync(() => {
        testButtonLeadsToRouting('registrations-' + tutorialGroup.id, ['/course-management', courseId, 'tutorial-groups-management', tutorialGroup.id!, 'registered-students']);
    }));

    it('should navigate to edit', fakeAsync(() => {
        testButtonLeadsToRouting('edit-' + tutorialGroup.id, ['/course-management', courseId, 'tutorial-groups-management', tutorialGroup.id!, 'edit']);
    }));

    it('should call delete and emit deleted event', () => {
        const tutorialGroupService = TestBed.inject(TutorialGroupsService);
        const deleteSpy = jest.spyOn(tutorialGroupService, 'delete').mockReturnValue(of(new HttpResponse<void>({})));
        const deleteEventSpy = jest.spyOn(component.tutorialGroupDeleted, 'emit');

        fixture.detectChanges();
        component.deleteTutorialGroup();
        expect(deleteSpy).toHaveBeenCalledWith(courseId, tutorialGroup.id);
        expect(deleteEventSpy).toHaveBeenCalledOnce();
    });

    const testButtonLeadsToRouting = (buttonId: string, expectedRoute: (string | number)[]) => {
        fixture.detectChanges();
        const navigateSpy = jest.spyOn(router, 'navigateByUrl');

        const button = fixture.debugElement.nativeElement.querySelector('#' + buttonId);
        button.click();

        fixture.whenStable().then(() => {
            expect(navigateSpy).toHaveBeenCalledOnce();
            expect(navigateSpy).toHaveBeenCalledWith(expectedRoute);
            navigateSpy.mockReset();
        });
    };
});
