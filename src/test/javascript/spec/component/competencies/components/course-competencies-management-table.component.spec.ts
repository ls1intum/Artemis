import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { CourseCompetenciesManagementTableComponent } from 'app/course/competencies/components/course-competencies-management-table/course-competencies-management-table.component';
import { CompetencyWithTailRelationDTO, CourseCompetency, CourseCompetencyType } from 'app/entities/competency.model';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockLocalStorageService } from '../../../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService } from 'ngx-webstorage';
import { Routes, provideRouter } from '@angular/router';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/util/alert.service';
import { CourseCompetencyApiService } from 'app/course/competencies/services/course-competency-api.service';
import { MockAlertService } from '../../../helpers/mocks/service/mock-alert.service';
import { of, throwError } from 'rxjs';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { PROFILE_IRIS } from 'app/app.constants';
import { IrisSettings } from 'app/entities/iris/settings/iris-settings.model';

describe('CourseCompetenciesManagementTable', () => {
    let component: CourseCompetenciesManagementTableComponent;
    let fixture: ComponentFixture<CourseCompetenciesManagementTableComponent>;
    let profileService: ProfileService;
    let irisSettingsService: IrisSettingsService;
    let alertService: AlertService;
    let courseCompetencyApiService: CourseCompetencyApiService;

    const courseId = 1;
    const courseCompetencies: CourseCompetency[] = [
        { id: 1, type: CourseCompetencyType.COMPETENCY },
        { id: 2, type: CourseCompetencyType.PREREQUISITE },
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
                    provide: IrisSettingsService,
                    useValue: {
                        getCombinedCourseSettings: jest.fn(() => of(<IrisSettings>{ irisCompetencyGenerationSettings: { enabled: true } })),
                    },
                },
                {
                    provide: ProfileService,
                    useValue: {
                        getProfileInfo: jest.fn(() => of(<ProfileInfo>{ activeProfiles: [PROFILE_IRIS] })),
                    },
                },
                {
                    provide: NgbModal,
                    useValue: {
                        open: jest.fn(),
                    },
                },
                {
                    provide: CourseCompetencyApiService,
                    useValue: {
                        importAll: jest.fn(),
                        deleteCourseCompetency: jest.fn(),
                    },
                },
            ],
        }).compileComponents();

        profileService = TestBed.inject(ProfileService);
        irisSettingsService = TestBed.inject(IrisSettingsService);
        alertService = TestBed.inject(AlertService);
        courseCompetencyApiService = TestBed.inject(CourseCompetencyApiService);

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

    it('should initialize', () => {
        expect(component).toBeDefined();
    });

    it('should load profileInfo', async () => {
        const getProfileInfoSpy = jest.spyOn(profileService, 'getProfileInfo');
        const getCourseSettingsSpy = jest.spyOn(irisSettingsService, 'getCombinedCourseSettings');

        fixture.detectChanges();
        await fixture.whenStable();

        expect(getProfileInfoSpy).toHaveBeenCalledOnce();
        expect(getCourseSettingsSpy).toHaveBeenCalledExactlyOnceWith(courseId);
        expect(component.irisCompetencyGenerationEnabled()).toBeTrue();
    });

    it('should show error on load profileInfo error', async () => {
        jest.spyOn(irisSettingsService, 'getCombinedCourseSettings').mockReturnValueOnce(throwError(() => new Error('Error')));
        const errorSpy = jest.spyOn(alertService, 'addAlert');

        fixture.detectChanges();
        await fixture.whenStable();

        expect(errorSpy).toHaveBeenCalledOnce();
    });

    it('should show data in table', () => {
        fixture.detectChanges();

        const tableRows = fixture.nativeElement.querySelectorAll('tr');
        expect(tableRows).toHaveLength(courseCompetencies.length + 1);
    });

    it('should delete course competency', async () => {
        const deleteCourseCompetencySpy = jest.spyOn(courseCompetencyApiService, 'deleteCourseCompetency').mockResolvedValueOnce();
        const dialogErrorSourceEmitSpy = jest.spyOn(component.dialogErrorSource, 'emit');

        await component['deleteCourseCompetency'](1);

        expect(deleteCourseCompetencySpy).toHaveBeenCalledExactlyOnceWith(courseId, 1);
        expect(dialogErrorSourceEmitSpy).toHaveBeenCalledExactlyOnceWith('');
    });

    it('should emit error on delete course competency error', async () => {
        jest.spyOn(courseCompetencyApiService, 'deleteCourseCompetency').mockRejectedValueOnce(new Error('Error'));
        const dialogErrorSourceEmitSpy = jest.spyOn(component.dialogErrorSource, 'emit');

        await component['deleteCourseCompetency'](1);

        expect(dialogErrorSourceEmitSpy).toHaveBeenCalledExactlyOnceWith('Error');
    });

    it('should import competencies via modal', async () => {
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
        const importAllCompetenciesSpy = jest.spyOn(courseCompetencyApiService, 'importAll').mockResolvedValue(importedCompetencies);
        const onCourseCompetenciesSpy = jest.spyOn(component.onCourseCompetenciesImport, 'emit');
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
        expect(onCourseCompetenciesSpy).toHaveBeenCalledExactlyOnceWith(importedCompetencies.map((dto) => dto.competency));
    });

    it('should show warning when no imported competencies exist', async () => {
        jest.spyOn(fixture.componentRef.injector.get(NgbModal), 'open').mockReturnValue({
            componentInstance: {
                disabledIds: [],
                competencyType: '',
            },
            result: modalResult,
        } as any);
        const importedCompetencies: CompetencyWithTailRelationDTO[] = [];
        jest.spyOn(courseCompetencyApiService, 'importAll').mockResolvedValue(importedCompetencies);
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
        jest.spyOn(courseCompetencyApiService, 'importAll').mockRejectedValue(new Error('Error'));
        const errorSpy = jest.spyOn(alertService, 'addAlert');

        const importAllButton = fixture.nativeElement.querySelector('#importAllCompetenciesButton');
        importAllButton.click();

        await fixture.whenStable();
        fixture.detectChanges();
        await fixture.whenStable();

        expect(errorSpy).toHaveBeenCalledOnce();
    });
});
