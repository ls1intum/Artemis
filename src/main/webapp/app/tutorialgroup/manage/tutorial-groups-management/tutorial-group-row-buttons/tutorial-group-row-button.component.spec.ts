import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupRowButtonsComponent } from 'app/tutorialgroup/manage/tutorial-groups-management/tutorial-group-row-buttons/tutorial-group-row-buttons.component';
import { of } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { generateExampleTutorialGroup } from 'test/helpers/sample/tutorialgroup/tutorialGroupExampleModels';
import { Course } from 'app/core/course/shared/entities/course.model';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { ActivatedRoute, Router } from '@angular/router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { TutorialGroupApiService } from 'app/openapi/api/tutorialGroupApi.service';
import '@angular/localize/init';

interface TutorialGroupApiServiceMock {
    deleteTutorialGroup: ReturnType<typeof vi.fn>;
}

describe('TutorialGroupRowButtonsComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<TutorialGroupRowButtonsComponent>;
    let component: TutorialGroupRowButtonsComponent;
    const course = {
        id: 1,
    } as Course;
    let tutorialGroup: TutorialGroup;

    let router: MockRouter;
    let tutorialGroupApiServiceMock: TutorialGroupApiServiceMock;

    beforeEach(async () => {
        router = new MockRouter();
        tutorialGroupApiServiceMock = {
            deleteTutorialGroup: vi.fn().mockReturnValue(of(undefined)),
        };
        await TestBed.configureTestingModule({
            imports: [TutorialGroupRowButtonsComponent, OwlNativeDateTimeModule],
            providers: [
                { provide: TutorialGroupApiService, useValue: tutorialGroupApiServiceMock },
                { provide: Router, useValue: router },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: DialogService, useClass: MockDialogService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialGroupRowButtonsComponent);
        component = fixture.componentInstance;
        tutorialGroup = generateExampleTutorialGroup({});
        setInputValues();
        fixture.detectChanges();
    });
    const setInputValues = () => {
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('isAtLeastInstructor', true);
    };

    it('should navigate to registrations', async () => {
        await testButtonLeadsToRouting('registrations-' + tutorialGroup.id, ['/course-management', course.id!, 'tutorial-groups', tutorialGroup.id!, 'registrations']);
    });

    it('should navigate to edit', async () => {
        await testButtonLeadsToRouting('edit-' + tutorialGroup.id, ['/course-management', course.id!, 'tutorial-groups', tutorialGroup.id!, 'edit']);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should call delete and emit deleted event', () => {
        const deleteEventSpy = vi.spyOn(component.tutorialGroupDeleted, 'emit');
        component.deleteTutorialGroup();
        expect(tutorialGroupApiServiceMock.deleteTutorialGroup).toHaveBeenCalledWith(course.id!, tutorialGroup.id);
        expect(deleteEventSpy).toHaveBeenCalledOnce();
    });

    const testButtonLeadsToRouting = async (buttonId: string, expectedRoute: (string | number)[]) => {
        const navigateSpy = vi.spyOn(router, 'navigateByUrl');

        const button = fixture.debugElement.nativeElement.querySelector('#' + buttonId);
        button.click();

        await fixture.whenStable();
        expect(navigateSpy).toHaveBeenCalledOnce();
        const expectedUrlTree = router.createUrlTree(expectedRoute);
        const actualUrlTree = navigateSpy.mock.calls[0][0];

        expect(actualUrlTree).toEqual(expectedUrlTree);

        navigateSpy.mockReset();
    };
});
