import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockRouterLinkDirective } from '../../../../../helpers/mocks/directive/mock-router-link.directive';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { TutorialGroupFreePeriodRowButtonsComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-free-periods/tutorial-free-periods-management/tutorial-group-free-period-row-buttons/tutorial-group-free-period-row-buttons.component';
import { TutorialGroupFreePeriodService } from 'app/course/tutorial-groups/services/tutorial-group-free-period.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import { TutorialGroupFreePeriod } from 'app/entities/tutorial-group/tutorial-group-free-day.model';
import { generateExampleTutorialGroupsConfiguration } from '../../../helpers/tutorialGroupsConfigurationExampleModels';
import { generateExampleTutorialGroupFreePeriod } from '../../../helpers/tutorialGroupFreePeriodExampleModel';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { Course } from 'app/entities/course.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { EditTutorialGroupFreePeriodComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-free-periods/crud/edit-tutorial-group-free-period/edit-tutorial-group-free-period.component';

describe('TutorialGroupFreePeriodRowButtonsComponent', () => {
    let fixture: ComponentFixture<TutorialGroupFreePeriodRowButtonsComponent>;
    let component: TutorialGroupFreePeriodRowButtonsComponent;
    let periodService: TutorialGroupFreePeriodService;
    const course = { id: 1 } as Course;
    let configuration: TutorialGroupsConfiguration;
    let tutorialFreePeriod: TutorialGroupFreePeriod;

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
            providers: [MockProvider(TutorialGroupFreePeriodService), MockProvider(NgbModal)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorialGroupFreePeriodRowButtonsComponent);
                component = fixture.componentInstance;
                periodService = TestBed.inject(TutorialGroupFreePeriodService);
                configuration = generateExampleTutorialGroupsConfiguration({});
                tutorialFreePeriod = generateExampleTutorialGroupFreePeriod({});
                setInputValues();
            });
    });

    const setInputValues = () => {
        component.course = course;
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

    it('should open the edit free day dialog when the respective button is clicked', fakeAsync(() => {
        const modalService = TestBed.inject(NgbModal);
        const mockModalRef = {
            componentInstance: {
                course: undefined,
                tutorialGroupFreePeriod: undefined,
                tutorialGroupsConfiguration: undefined,

                initialize: () => {},
            },
            result: of(),
        };
        const modalOpenSpy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as unknown as NgbModalRef);

        fixture.detectChanges();
        const openDialogSpy = jest.spyOn(component, 'openEditFreeDayDialog');

        const button = fixture.debugElement.nativeElement.querySelector('#edit-' + tutorialFreePeriod.id);
        button.click();

        fixture.whenStable().then(() => {
            expect(openDialogSpy).toHaveBeenCalledOnce();
            expect(modalOpenSpy).toHaveBeenCalledOnce();
            expect(modalOpenSpy).toHaveBeenCalledWith(EditTutorialGroupFreePeriodComponent, { backdrop: 'static', scrollable: false, size: 'lg' });
            expect(mockModalRef.componentInstance.tutorialGroupFreePeriod).toEqual(tutorialFreePeriod);
            expect(mockModalRef.componentInstance.tutorialGroupsConfiguration).toEqual(configuration);
            expect(mockModalRef.componentInstance.course).toEqual(course);
        });
    }));

    it('should call delete and emit deleted event', () => {
        const deleteSpy = jest.spyOn(periodService, 'delete').mockReturnValue(of(new HttpResponse<void>({})));
        const deleteEventSpy = jest.spyOn(component.tutorialFreePeriodDeleted, 'emit');

        fixture.detectChanges();
        component.deleteTutorialFreePeriod();
        expect(deleteSpy).toHaveBeenCalledWith(course.id, configuration.id, tutorialFreePeriod.id);
        expect(deleteEventSpy).toHaveBeenCalledOnce();
    });
});
