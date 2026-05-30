import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Course } from 'app/course/shared/entities/course.model';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ApollonDiagramService } from 'app/quiz/manage/apollon-diagrams/services/apollon-diagram.service';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { MockProvider } from 'ng-mocks';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Subject, of } from 'rxjs';
import { AlertService } from 'app/foundation/service/alert.service';
import { SortService } from 'app/foundation/service/sort.service';
import { ApollonDiagramListComponent } from 'app/quiz/manage/apollon-diagrams/list/apollon-diagram-list.component';
import { ApollonDiagram } from 'app/modeling/shared/entities/apollon-diagram.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { isEqual } from 'lodash-es';
import { UMLDiagramType } from '@tumaet/apollon';

describe('ApollonDiagramList Component', () => {
    setupTestBed({ zoneless: true });

    let apollonDiagramService: ApollonDiagramService;
    let courseService: CourseManagementService;
    let dialogService: DialogService;
    let fixture: ComponentFixture<ApollonDiagramListComponent>;

    const course: Course = { id: 123 } as Course;

    beforeEach(() => {
        const route = { params: of({ courseId: 123 }), snapshot: { paramMap: convertToParamMap({ courseId: course.id }) } } as any as ActivatedRoute;

        TestBed.configureTestingModule({
            imports: [ApollonDiagramListComponent],
            declarations: [],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                AlertService,
                ApollonDiagramService,
                MockProvider(SortService),
                MockProvider(DialogService),
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                MockProvider(CourseManagementService),
                { provide: AccountService, useClass: MockAccountService },
            ],
        })
            .overrideTemplate(ApollonDiagramListComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ApollonDiagramListComponent);
                apollonDiagramService = fixture.debugElement.injector.get(ApollonDiagramService);
                courseService = fixture.debugElement.injector.get(CourseManagementService);
                dialogService = fixture.debugElement.injector.get(DialogService);
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should load diagrams and course', () => {
        const apollonDiagrams: ApollonDiagram[] = [new ApollonDiagram(UMLDiagramType.ClassDiagram, course.id!), new ApollonDiagram(UMLDiagramType.ActivityDiagram, course.id!)];
        const diagramResponse: HttpResponse<ApollonDiagram[]> = new HttpResponse({ body: apollonDiagrams });
        const courseResponse: HttpResponse<Course> = new HttpResponse({ body: course });

        vi.spyOn(apollonDiagramService, 'getDiagramsByCourse').mockReturnValue(of(diagramResponse));
        vi.spyOn(courseService, 'find').mockReturnValue(of(courseResponse));

        fixture.detectChanges();
        expect(isEqual(fixture.componentInstance.apollonDiagrams(), apollonDiagrams)).toBeTruthy();
    });

    it('delete', () => {
        // setup
        const response: HttpResponse<void> = new HttpResponse();
        vi.spyOn(apollonDiagramService, 'delete').mockReturnValue(of(response));

        const apollonDiagrams = [];
        for (let i = 0; i < 3; i++) {
            const apollonDiagram = new ApollonDiagram(UMLDiagramType.ClassDiagram, course.id!);
            apollonDiagram.id = i;
            apollonDiagrams.push(apollonDiagram);
        }

        const diagramToDelete = apollonDiagrams[0];
        fixture.componentInstance.apollonDiagrams.set(apollonDiagrams);
        fixture.componentInstance.delete(diagramToDelete);
        expect(fixture.componentInstance.apollonDiagrams().find((diagram) => diagram.id === diagramToDelete.id)).toBeFalsy();
    });

    it('openCreateDiagramDialog', () => {
        const onClose = new Subject<ApollonDiagram | undefined>();
        const openDialogSpy = vi.spyOn(dialogService, 'open').mockReturnValue({ onClose } as unknown as DynamicDialogRef);
        const handleOpenSpy = vi.spyOn(fixture.componentInstance, 'handleOpenDialogClick');

        fixture.componentInstance.openCreateDiagramDialog(course.id!);

        expect(openDialogSpy).toHaveBeenCalledOnce();

        // dismiss (undefined) must not open a diagram
        onClose.next(undefined);
        expect(handleOpenSpy).not.toHaveBeenCalled();

        // a created diagram opens the diagram view
        const createdDiagram = new ApollonDiagram(UMLDiagramType.ClassDiagram, course.id!);
        createdDiagram.id = 42;
        onClose.next(createdDiagram);
        expect(handleOpenSpy).toHaveBeenCalledWith(42);
    });

    it('getTitleForApollonDiagram', () => {
        const apollonDiagram = new ApollonDiagram(UMLDiagramType.ClassDiagram, course.id!);
        apollonDiagram.title = 'Title ';
        expect(fixture.componentInstance.getTitleForApollonDiagram(apollonDiagram)).toBe('Title');
    });

    it('handleOpenDialogClick', () => {
        const emitOpenDiagramSpy = vi.spyOn(fixture.componentInstance.openDiagram, 'emit');
        fixture.componentInstance.handleOpenDialogClick(1);
        expect(emitOpenDiagramSpy).toHaveBeenCalledWith(1);
    });

    it('handleCloseDiagramClick', () => {
        const emitCloseDialog = vi.spyOn(fixture.componentInstance.closeDialog, 'emit');
        fixture.componentInstance.handleCloseDiagramClick();
        expect(emitCloseDialog).toHaveBeenCalledOnce();
    });
});
