import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { CourseCompetenciesManagementTableComponent } from 'app/course/competencies/components/course-competencies-management-table/course-competencies-management-table.component';
import { CompetencyWithTailRelationDTO, CourseCompetency, CourseCompetencyStudentProgressDTO, CourseCompetencyType } from 'app/entities/competency.model';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockLocalStorageService } from '../../../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService } from 'ngx-webstorage';
import { Routes, provideRouter } from '@angular/router';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/util/alert.service';
import { MockAlertService } from '../../../helpers/mocks/service/mock-alert.service';
import { of } from 'rxjs';
import { CompetencyService } from '../../../../../../main/webapp/app/course/competencies/competency.service';
import { PrerequisiteService } from '../../../../../../main/webapp/app/course/competencies/prerequisite.service';

describe('CourseCompetenciesManagementTable', () => {
    let component: CourseCompetenciesManagementTableComponent;
    let fixture: ComponentFixture<CourseCompetenciesManagementTableComponent>;
    let alertService: AlertService;
    let competencyService: CompetencyService;

    const courseId = 1;
    const courseCompetencies: CourseCompetencyStudentProgressDTO[] = [
        {
            id: 1,
            type: CourseCompetencyType.COMPETENCY,
            title: 'Competency 1',
            description: 'Description 1',
            numberOfMasteredStudents: 1,
            numberOfStudents: 2,
            optional: false,
        },
        {
            id: 2,
            type: CourseCompetencyType.PREREQUISITE,
            title: 'Prerequisite 1',
            description: 'Description 2',
            numberOfMasteredStudents: 3,
            numberOfStudents: 4,
            optional: false,
        },
    ];
    const courseCompetencyType = CourseCompetencyType.COMPETENCY;
    const standardizedCompetenciesEnabled = false;

    const routes: Routes = [];

    const modalResult = { courseForImportDTO: { id: 2, title: 'Import Course Title' }, importRelations: false };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CourseCompetenciesManagementTableComponent],
            providers: [
                provideRouter(routes),
                provideHttpClient(),
                provideHttpClientTesting(),
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                {
                    provide: LocalStorageService,
                    useClass: MockLocalStorageService,
                },
                {
                    provide: AlertService,
                    useClass: MockAlertService,
                },
                {
                    provide: NgbModal,
                    useValue: {
                        open: jest.fn(),
                    },
                },
                {
                    provide: CompetencyService,
                    useValue: {
                        importAll: jest.fn(),
                        delete: jest.fn(),
                    },
                },
                {
                    provide: PrerequisiteService,
                    useValue: {
                        importAll: jest.fn(),
                        delete: jest.fn(),
                    },
                },
            ],
        }).compileComponents();

        alertService = TestBed.inject(AlertService);
        competencyService = TestBed.inject(CompetencyService);

        fixture = TestBed.createComponent(CourseCompetenciesManagementTableComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('courseId', courseId);
        fixture.componentRef.setInput('courseCompetencies', courseCompetencies);
        fixture.componentRef.setInput('courseCompetencyType', courseCompetencyType);
        fixture.componentRef.setInput('standardizedCompetenciesEnabled', standardizedCompetenciesEnabled);
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should show data in table', () => {
        fixture.detectChanges();

        const tableRows = fixture.nativeElement.querySelectorAll('tr');
        expect(tableRows).toHaveLength(courseCompetencies.length + 1);
    });

    it('should delete course competency', async () => {
        fixture.detectChanges();
        const deleteCourseCompetencySpy = jest.spyOn(competencyService, 'delete').mockReturnValue(of(new HttpResponse<object>({ status: 200 })));
        const dialogErrorSourceEmitSpy = jest.spyOn(component.dialogErrorSource, 'emit');

        await component['deleteCourseCompetency'](1);

        expect(deleteCourseCompetencySpy).toHaveBeenCalledExactlyOnceWith(1, courseId);
        expect(dialogErrorSourceEmitSpy).toHaveBeenCalledExactlyOnceWith('');
    });

    it('should emit error on delete course competency error', async () => {
        fixture.detectChanges();
        jest.spyOn(competencyService, 'delete').mockReturnValue(of(new HttpResponse<object>({ status: 500 })));
        const dialogErrorSourceEmitSpy = jest.spyOn(component.dialogErrorSource, 'emit');

        await component['deleteCourseCompetency'](1);

        expect(dialogErrorSourceEmitSpy).toHaveBeenCalledOnce();
    });

    it('should import competencies via modal', async () => {
        fixture.detectChanges();
        const openSpy = jest.spyOn(fixture.componentRef.injector.get(NgbModal), 'open').mockReturnValue({
            componentInstance: {
                disabledIds: [],
                competencyType: '',
            },
            result: modalResult,
        } as any);
        const importedCompetencies: CompetencyWithTailRelationDTO[] = [
            {
                competency: {
                    id: 3,
                    type: CourseCompetencyType.COMPETENCY,
                    title: 'Imported competency',
                },
            },
        ];
        const importAllCompetenciesSpy = jest.spyOn(competencyService, 'importAll').mockReturnValue(of(new HttpResponse({ status: 200, body: importedCompetencies })));
        const onCourseCompetenciesImportSpy = jest.spyOn(component.onCourseCompetenciesImport, 'emit');
        const successSpy = jest.spyOn(alertService, 'success');

        const importAllButton = fixture.nativeElement.querySelector('#importAllCompetenciesButton');
        importAllButton.click();

        expect(openSpy).toHaveBeenCalledOnce();

        fixture.detectChanges();
        await fixture.whenStable();

        expect(importAllCompetenciesSpy).toHaveBeenCalledExactlyOnceWith(courseId, modalResult.courseForImportDTO.id, modalResult.importRelations);

        fixture.detectChanges();
        await fixture.whenStable();

        expect(successSpy).toHaveBeenCalledOnce();
        const importedMappedCompetencies = importedCompetencies
            .map((dto) => dto.competency)
            .filter((element): element is CourseCompetency => !!element)
            .map(
                (courseCompetency) =>
                    <CourseCompetencyStudentProgressDTO>{
                        id: courseCompetency.id,
                        title: courseCompetency.title,
                        description: courseCompetency.description,
                        numberOfMasteredStudents: 0,
                        numberOfStudents: 0,
                        optional: courseCompetency.optional,
                        softDueDate: courseCompetency.softDueDate,
                        taxonomy: courseCompetency.taxonomy,
                        type: courseCompetency.type,
                    },
            );
        expect(onCourseCompetenciesImportSpy).toHaveBeenCalledExactlyOnceWith(importedMappedCompetencies);
    });

    it('should show warning when no imported competencies exist', async () => {
        fixture.detectChanges();
        jest.spyOn(fixture.componentRef.injector.get(NgbModal), 'open').mockReturnValue({
            componentInstance: {
                disabledIds: [],
                competencyType: '',
            },
            result: modalResult,
        } as any);
        const importedCompetencies: CompetencyWithTailRelationDTO[] = [];
        jest.spyOn(competencyService, 'importAll').mockReturnValue(
            of(
                new HttpResponse({
                    status: 200,
                    body: importedCompetencies,
                }),
            ),
        );
        const warningSpy = jest.spyOn(alertService, 'warning');

        const importAllButton = fixture.nativeElement.querySelector('#importAllCompetenciesButton');
        importAllButton.click();

        await fixture.whenStable();
        fixture.detectChanges();
        await fixture.whenStable();

        expect(warningSpy).toHaveBeenCalledOnce();
    });

    it('should show error on import competencies error', async () => {
        jest.spyOn(fixture.componentRef.injector.get(NgbModal), 'open').mockReturnValue({
            componentInstance: {
                disabledIds: [],
                competencyType: '',
            },
            result: modalResult,
        } as any);
        jest.spyOn(competencyService, 'importAll').mockReturnValue(of(new HttpResponse<CompetencyWithTailRelationDTO[]>({ status: 500 })));
        const errorSpy = jest.spyOn(alertService, 'error');

        const importAllButton = fixture.nativeElement.querySelector('#importAllCompetenciesButton');
        importAllButton.click();

        await fixture.whenStable();
        fixture.detectChanges();
        await fixture.whenStable();

        expect(errorSpy).toHaveBeenCalledOnce();
    });
});
