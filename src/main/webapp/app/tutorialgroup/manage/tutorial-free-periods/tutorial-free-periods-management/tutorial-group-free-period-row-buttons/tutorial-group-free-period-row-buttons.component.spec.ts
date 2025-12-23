import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { TutorialGroupFreePeriodRowButtonsComponent } from 'app/tutorialgroup/manage/tutorial-free-periods/tutorial-free-periods-management/tutorial-group-free-period-row-buttons/tutorial-group-free-period-row-buttons.component';
import { TutorialGroupFreePeriodService } from 'app/tutorialgroup/shared/service/tutorial-group-free-period.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { TutorialGroupsConfiguration } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration.model';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { generateExampleTutorialGroupsConfiguration } from 'test/helpers/sample/tutorialgroup/tutorialGroupsConfigurationExampleModels';
import { generateExampleTutorialGroupFreePeriod } from 'test/helpers/sample/tutorialgroup/tutorialGroupFreePeriodExampleModel';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { Course } from 'app/core/course/shared/entities/course.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { EditTutorialGroupFreePeriodComponent } from 'app/tutorialgroup/manage/tutorial-free-periods/crud/edit-tutorial-group-free-period/edit-tutorial-group-free-period.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { signal } from '@angular/core';

describe('TutorialGroupFreePeriodRowButtonsComponent', () => {
    let fixture: ComponentFixture<TutorialGroupFreePeriodRowButtonsComponent>;
    let component: TutorialGroupFreePeriodRowButtonsComponent;
    let periodService: TutorialGroupFreePeriodService;
    const course = { id: 1 } as Course;
    let configuration: TutorialGroupsConfiguration;
    let tutorialFreePeriod: TutorialGroupFreePeriod;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [FaIconComponent],
            declarations: [
                TutorialGroupFreePeriodRowButtonsComponent,
                MockPipe(ArtemisDatePipe),
                MockRouterLinkDirective,
                MockDirective(DeleteButtonDirective),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [MockProvider(TutorialGroupFreePeriodService), MockProvider(NgbModal), { provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorialGroupFreePeriodRowButtonsComponent);
                component = fixture.componentInstance;
                periodService = TestBed.inject(TutorialGroupFreePeriodService);
                configuration = generateExampleTutorialGroupsConfiguration({});
                tutorialFreePeriod = generateExampleTutorialGroupFreePeriod({});
                setInputValues();
                fixture.detectChanges();
            });
    });

    const setInputValues = () => {
        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('tutorialGroupConfiguration', configuration);
        fixture.componentRef.setInput('tutorialFreePeriod', tutorialFreePeriod);
    };

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
    });

    it('should open the edit free day dialog when the respective button is clicked', fakeAsync(() => {
        const modalService = TestBed.inject(NgbModal);
        const mockCourse = { id: 1, title: 'Test Course' };
        const mockTutorialFreePeriod = { id: 42, date: '2025-06-30' };
        const mockConfiguration = { id: 99, timeSlots: ['08:00-09:00'] };

        const mockModalRef = {
            componentInstance: {
                course: signal(mockCourse),
                tutorialGroupFreePeriod: signal(mockTutorialFreePeriod),
                tutorialGroupsConfiguration: signal(mockConfiguration),
                initialize: () => {},
            },
            result: of(),
        };
        const modalOpenSpy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as unknown as NgbModalRef);
        const openDialogSpy = jest.spyOn(component, 'openEditFreePeriodDialog');

        const button = fixture.debugElement.nativeElement.querySelector('#edit-' + tutorialFreePeriod.id);
        button.click();

        fixture.whenStable().then(() => {
            expect(openDialogSpy).toHaveBeenCalledOnce();
            expect(modalOpenSpy).toHaveBeenCalledOnce();
            expect(modalOpenSpy).toHaveBeenCalledWith(EditTutorialGroupFreePeriodComponent, { backdrop: 'static', scrollable: false, size: 'lg', animation: false });
            expect(mockModalRef.componentInstance.tutorialGroupFreePeriod()).toEqual(tutorialFreePeriod);
            expect(mockModalRef.componentInstance.tutorialGroupsConfiguration()).toEqual(configuration);
            expect(mockModalRef.componentInstance.course()).toEqual(course);
        });
    }));

    it('should call delete and emit deleted event', () => {
        const deleteSpy = jest.spyOn(periodService, 'delete').mockReturnValue(of(new HttpResponse<void>({})));
        const deleteEventSpy = jest.spyOn(component.tutorialFreePeriodDeleted, 'emit');

        component.deleteTutorialFreePeriod();
        expect(deleteSpy).toHaveBeenCalledWith(course.id, configuration.id, tutorialFreePeriod.id);
        expect(deleteEventSpy).toHaveBeenCalledOnce();
    });
});
