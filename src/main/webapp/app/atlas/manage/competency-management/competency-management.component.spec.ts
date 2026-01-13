import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { Competency, CompetencyWithTailRelationDTO, CourseCompetencyProgress, CourseCompetencyType } from 'app/atlas/shared/entities/competency.model';
import { CompetencyManagementComponent } from 'app/atlas/manage/competency-management/competency-management.component';
import { AgentChatModalComponent } from 'app/atlas/manage/agent-chat-modal/agent-chat-modal.component';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { AccountService } from 'app/core/auth/account.service';
import { NgbModal, NgbModalRef, NgbProgressbar } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/shared/service/alert.service';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { MockHasAnyAuthorityDirective } from 'test/helpers/mocks/directive/mock-has-any-authority.directive';
import '@angular/localize/init';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { ImportAllCompetenciesComponent } from 'app/atlas/manage/competency-management/import-all-competencies.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { IrisCourseSettingsWithRateLimitDTO } from 'app/iris/shared/entities/settings/iris-course-settings.model';
import { PROFILE_IRIS } from 'app/app.constants';
import { Prerequisite } from 'app/atlas/shared/entities/prerequisite.model';
import { CompetencyManagementTableComponent } from 'app/atlas/manage/competency-management/competency-management-table.component';
import { CourseCompetencyApiService } from 'app/atlas/shared/services/course-competency-api.service';
import {
    ImportAllCourseCompetenciesModalComponent,
    ImportAllCourseCompetenciesResult,
} from 'app/atlas/manage/import-all-course-competencies-modal/import-all-course-competencies-modal.component';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { LocalStorageService } from 'app/shared/service/local-storage.service';

describe('CompetencyManagementComponent', () => {
    let fixture: ComponentFixture<CompetencyManagementComponent>;
    let component: CompetencyManagementComponent;
    let courseCompetencyApiService: CourseCompetencyApiService;
    let profileService: ProfileService;
    let irisSettingsService: IrisSettingsService;
    let modalService: NgbModal;
    let alertService: AlertService;
    let localStorageService: LocalStorageService;

    let getProfileInfoSpy: jest.SpyInstance;
    let getAllForCourseSpy: jest.SpyInstance;
    let getIrisSettingsSpy: jest.SpyInstance;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [NgbProgressbar],
            declarations: [
                CompetencyManagementComponent,
                MockHasAnyAuthorityDirective,
                MockComponent(DocumentationButtonComponent),
                MockComponent(ImportAllCompetenciesComponent),
                MockComponent(CompetencyManagementTableComponent),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(HtmlForMarkdownPipe),
                MockPipe(ArtemisDatePipe),
                MockDirective(DeleteButtonDirective),
            ],
            providers: [
                provideRouter([]),
                MockProvider(AccountService),
                MockProvider(AlertService),
                {
                    provide: AlertService,
                    useClass: MockAlertService,
                },
                {
                    provide: ProfileService,
                    useClass: MockProfileService,
                },
                { provide: NgbModal, useClass: MockNgbModalService },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            params: of({
                                courseId: 1,
                            }),
                        },
                    },
                },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        courseCompetencyApiService = TestBed.inject(CourseCompetencyApiService);
        irisSettingsService = TestBed.inject(IrisSettingsService);
        profileService = TestBed.inject(ProfileService);
        alertService = TestBed.inject(AlertService);
        localStorageService = TestBed.inject(LocalStorageService);

        const competency: Competency = new Competency();
        competency.id = 1;
        competency.description = 'test';
        const courseCompetencyProgress = new CourseCompetencyProgress();
        courseCompetencyProgress.competencyId = 1;
        courseCompetencyProgress.numberOfStudents = 8;
        courseCompetencyProgress.numberOfMasteredStudents = 5;
        courseCompetencyProgress.averageStudentScore = 90;

        getAllForCourseSpy = jest.spyOn(courseCompetencyApiService, 'getCourseCompetenciesByCourseId').mockResolvedValue([
            competency,
            { id: 5, type: CourseCompetencyType.COMPETENCY } as Competency,
            {
                id: 3,
                type: CourseCompetencyType.PREREQUISITE,
            } as Prerequisite,
        ]);

        const profileInfoResponse = {
            activeProfiles: [PROFILE_IRIS],
        } as ProfileInfo;
        getProfileInfoSpy = jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(profileInfoResponse);

        getIrisSettingsSpy = jest.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit');

        fixture = TestBed.createComponent(CompetencyManagementComponent);
        component = fixture.componentInstance;

        modalService = TestBed.inject(NgbModal);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should show generate button if IRIS is enabled', async () => {
        const irisSettingsResponse = {
            courseId: 1,
            settings: {
                enabled: true,
                variant: 'default',
                rateLimit: {},
            },
        } as IrisCourseSettingsWithRateLimitDTO;
        getIrisSettingsSpy.mockReturnValue(of(irisSettingsResponse));

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();

        expect(getProfileInfoSpy).toHaveBeenCalled();
        expect(getIrisSettingsSpy).toHaveBeenCalled();
    });

    it('should load competencies and prerequisites', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        expect(getAllForCourseSpy).toHaveBeenCalledExactlyOnceWith(1);

        expect(component.competencies()).toHaveLength(2);
        expect(component.prerequisites()).toHaveLength(1);
    });

    it('should set isLoading correctly', async () => {
        const isLoadingSpy = jest.spyOn(component.isLoading, 'set');

        fixture.detectChanges();
        await fixture.whenStable();

        expect(isLoadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(isLoadingSpy).toHaveBeenNthCalledWith(2, false);
    });

    it('should show alert when loading iris settings fails', async () => {
        const errorSpy = jest.spyOn(alertService, 'error');
        getIrisSettingsSpy.mockRejectedValueOnce({});

        fixture.detectChanges();
        await fixture.whenStable();

        expect(errorSpy).toHaveBeenCalledOnce();
    });

    it('should show alert when loading course competencies fails', async () => {
        const errorSpy = jest.spyOn(alertService, 'error');
        getAllForCourseSpy.mockRejectedValueOnce({});

        fixture.detectChanges();
        await fixture.whenStable();

        expect(errorSpy).toHaveBeenCalledOnce();
    });

    it('should open course competency explanation', () => {
        localStorageService.store<boolean>('alreadyVisitedCompetencyManagement', true);
        const openModalSpy = jest.spyOn(modalService, 'open');
        fixture.detectChanges();

        component.openCourseCompetencyExplanation();
        expect(openModalSpy).toHaveBeenCalledOnce();
    });

    it('should open import modal and update values', async () => {
        fixture.detectChanges();
        const modalResult: ImportAllCourseCompetenciesResult = {
            course: { id: 1, title: 'Course 1' },
            courseCompetencyImportOptions: {
                sourceCourseId: 3,
                importRelations: true,
                competencyIds: [5, 4],
                importLectures: true,
                importExercises: true,
            },
        };
        const modalRef = {
            result: Promise.resolve(modalResult),
            componentInstance: {},
        } as NgbModalRef;
        const importedCompetencies: CompetencyWithTailRelationDTO[] = [
            { competency: { id: 1, type: CourseCompetencyType.COMPETENCY }, tailRelations: [{ id: 11 }] },
            { competency: { id: 2, type: CourseCompetencyType.COMPETENCY } },
        ];

        jest.spyOn(modalService, 'open').mockReturnValue(modalRef);
        jest.spyOn(courseCompetencyApiService, 'importAllByCourseId').mockResolvedValue(importedCompetencies);
        component.courseCompetencies.set([]);
        fixture.changeDetectorRef.detectChanges();
        const existingCompetencies = component.competencies().length;

        await component.openImportAllModal();
        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();
        expect(modalService.open).toHaveBeenCalledExactlyOnceWith(ImportAllCourseCompetenciesModalComponent, {
            size: 'lg',
            backdrop: 'static',
        });

        expect(component.competencies()).toHaveLength(existingCompetencies + 3);
    });

    it('should open agent chat modal and set courseId', () => {
        localStorageService.store<boolean>('alreadyVisitedCompetencyManagement', true);
        const modalRef = {
            componentInstance: {
                courseId: signal<number | null>(1),
                competencyChanged: {
                    subscribe: jest.fn(),
                },
            },
        } as any;
        const openModalSpy = jest.spyOn(modalService, 'open').mockReturnValue(modalRef);
        fixture.detectChanges();

        component['openAgentChatModal']();

        expect(openModalSpy).toHaveBeenCalledWith(AgentChatModalComponent, {
            size: 'lg',
            backdrop: true,
        });
        expect(modalRef.componentInstance.courseId()).toBe(1);
    });
});
