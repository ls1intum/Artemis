import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockRouterLinkDirective } from '../../../../../helpers/mocks/directive/mock-router-link.directive';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { TutorialGroupFreePeriodRowButtonsComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-free-periods/tutorial-free-periods-management/tutorial-group-free-period-row-buttons/tutorial-group-free-period-row-buttons.component';
import { TutorialGroupFreePeriodService } from 'app/course/tutorial-groups/services/tutorial-group-free-period.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import { TutorialGroupFreePeriod } from 'app/entities/tutorial-group/tutorial-group-free-day.model';
import { generateExampleTutorialGroupsConfiguration } from '../../tutorial-groups-configuration/crud/tutorialGroupsConfigurationExampleModels';
import { generateExampleTutorialGroupFreePeriod } from '../crud/tutorialGroupFreePeriodExampleModel';
import { MockRouter } from '../../../../../helpers/mocks/mock-router';
import { Router } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('TutorialGroupFreePeriodRowButtonsComponent', () => {
    let fixture: ComponentFixture<TutorialGroupFreePeriodRowButtonsComponent>;
    let component: TutorialGroupFreePeriodRowButtonsComponent;
    let periodService: TutorialGroupFreePeriodService;
    const courseId = 1;
    let configuration: TutorialGroupsConfiguration;
    let tutorialFreePeriod: TutorialGroupFreePeriod;

    const router = new MockRouter();

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [
                TutorialGroupFreePeriodRowButtonsComponent,
                MockPipe(ArtemisDatePipe),
                MockComponent(FaIconComponent),
                MockRouterLinkDirective,
                MockDirective(DeleteButtonDirective),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [MockProvider(TutorialGroupFreePeriodService), { provide: Router, useValue: router }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorialGroupFreePeriodRowButtonsComponent);
                component = fixture.componentInstance;
                periodService = TestBed.inject(TutorialGroupFreePeriodService);
                configuration = generateExampleTutorialGroupsConfiguration();
                tutorialFreePeriod = generateExampleTutorialGroupFreePeriod();
                setInputValues();
            });
    });

    const setInputValues = () => {
        component.courseId = courseId;
        component.tutorialGroupConfiguration = configuration;
        component.tutorialFreePeriod = tutorialFreePeriod;
    };

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).toBeTruthy();
    });

    it('should navigate to edit page when edit button is clicked', fakeAsync(() => {
        fixture.detectChanges();
        const navigateSpy = jest.spyOn(router, 'navigateByUrl');

        const editButton = fixture.debugElement.nativeElement.querySelector('#edit-' + tutorialFreePeriod.id);
        editButton.click();

        fixture.whenStable().then(() => {
            expect(navigateSpy).toHaveBeenCalledOnce();
            expect(navigateSpy).toHaveBeenCalledWith([
                '/course-management',
                courseId,
                'tutorial-groups-management',
                'configuration',
                configuration.id,
                'tutorial-free-days',
                tutorialFreePeriod.id,
                'edit',
            ]);
        });
    }));

    it('should call delete and emit deleted event', () => {
        const deleteSpy = jest.spyOn(periodService, 'delete').mockReturnValue(of(new HttpResponse<void>({})));
        const deleteEventSpy = jest.spyOn(component.tutorialFreePeriodDeleted, 'emit');

        fixture.detectChanges();
        component.deleteTutorialFreePeriod();
        expect(deleteSpy).toHaveBeenCalledWith(courseId, configuration.id, tutorialFreePeriod.id);
        expect(deleteEventSpy).toHaveBeenCalledOnce();
    });
});
