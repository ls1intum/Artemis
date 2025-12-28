import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { TutorialGroupFreePeriodRowButtonsComponent } from 'app/tutorialgroup/manage/tutorial-free-periods/tutorial-free-periods-management/tutorial-group-free-period-row-buttons/tutorial-group-free-period-row-buttons.component';
import { TutorialGroupFreePeriodService } from 'app/tutorialgroup/shared/service/tutorial-group-free-period.service';
import { TutorialGroupsConfiguration } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration.model';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { generateExampleTutorialGroupsConfiguration } from 'test/helpers/sample/tutorialgroup/tutorialGroupsConfigurationExampleModels';
import { generateExampleTutorialGroupFreePeriod } from 'test/helpers/sample/tutorialgroup/tutorialGroupFreePeriodExampleModel';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { Course } from 'app/core/course/shared/entities/course.model';
import { EditTutorialGroupFreePeriodComponent } from 'app/tutorialgroup/manage/tutorial-free-periods/crud/edit-tutorial-group-free-period/edit-tutorial-group-free-period.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';

describe('TutorialGroupFreePeriodRowButtonsComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<TutorialGroupFreePeriodRowButtonsComponent>;
    let component: TutorialGroupFreePeriodRowButtonsComponent;
    let periodService: TutorialGroupFreePeriodService;
    const course = { id: 1 } as Course;
    let configuration: TutorialGroupsConfiguration;
    let tutorialFreePeriod: TutorialGroupFreePeriod;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TutorialGroupFreePeriodRowButtonsComponent],
            providers: [
                MockProvider(TutorialGroupFreePeriodService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DialogService, useClass: MockDialogService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialGroupFreePeriodRowButtonsComponent);
        component = fixture.componentInstance;
        periodService = TestBed.inject(TutorialGroupFreePeriodService);
        configuration = generateExampleTutorialGroupsConfiguration({});
        tutorialFreePeriod = generateExampleTutorialGroupFreePeriod({});
        setInputValues();
        fixture.detectChanges();
    });

    const setInputValues = () => {
        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('tutorialGroupConfiguration', configuration);
        fixture.componentRef.setInput('tutorialFreePeriod', tutorialFreePeriod);
    };

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
    });

    it('should open the edit free day dialog when the respective button is clicked', async () => {
        const mockEditDialog = { open: vi.fn() } as unknown as EditTutorialGroupFreePeriodComponent;
        vi.spyOn(component, 'editFreePeriodDialog').mockReturnValue(mockEditDialog);
        const openDialogSpy = vi.spyOn(component, 'openEditFreePeriodDialog');

        const button = fixture.debugElement.nativeElement.querySelector('#edit-' + tutorialFreePeriod.id);
        button.click();

        await fixture.whenStable();
        expect(openDialogSpy).toHaveBeenCalledOnce();
        expect(mockEditDialog.open).toHaveBeenCalledOnce();
    });

    it('should call delete and emit deleted event', () => {
        const deleteSpy = vi.spyOn(periodService, 'delete').mockReturnValue(of(new HttpResponse<void>({})));
        const deleteEventSpy = vi.spyOn(component.tutorialFreePeriodDeleted, 'emit');

        component.deleteTutorialFreePeriod();
        expect(deleteSpy).toHaveBeenCalledWith(course.id, configuration.id, tutorialFreePeriod.id);
        expect(deleteEventSpy).toHaveBeenCalledOnce();
    });
});
