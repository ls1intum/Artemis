import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { Competency, CompetencyLectureUnitLink, CompetencyWithTailRelationDTO, CourseCompetencyProgress, CourseCompetencyType } from 'app/entities/competency.model';
import { CompetencyManagementComponent } from 'app/course/competencies/competency-management/competency-management.component';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { AccountService } from 'app/core/auth/account.service';
import { ArtemisTestModule } from '../../../test.module';
import { NgbModal, NgbModalRef, NgbProgressbar } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/util/alert.service';
import { MockNgbModalService } from '../../../helpers/mocks/service/mock-ngb-modal.service';
import { DocumentationButtonComponent } from 'app/shared/components/documentation-button/documentation-button.component';
import { MockHasAnyAuthorityDirective } from '../../../helpers/mocks/directive/mock-has-any-authority.directive';
import { By } from '@angular/platform-browser';
import '@angular/localize/init';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { ImportAllCompetenciesComponent } from 'app/course/competencies/competency-management/import-all-competencies.component';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { IrisCourseSettings } from 'app/entities/iris/settings/iris-settings.model';
import { PROFILE_IRIS } from 'app/app.constants';
import { Prerequisite } from 'app/entities/prerequisite.model';
import { CompetencyManagementTableComponent } from 'app/course/competencies/competency-management/competency-management-table.component';
import { CourseCompetencyApiService } from 'app/course/competencies/services/course-competency-api.service';
import {
    ImportAllCourseCompetenciesModalComponent,
    ImportAllCourseCompetenciesResult,
} from 'app/course/competencies/components/import-all-course-competencies-modal/import-all-course-competencies-modal.component';

describe('CompetencyManagementComponent', () => {
    let fixture: ComponentFixture<CompetencyManagementComponent>;
    let component: CompetencyManagementComponent;
    let courseCompetencyApiService: CourseCompetencyApiService;
    let profileService: ProfileService;
    let irisSettingsService: IrisSettingsService;
    let modalService: NgbModal;

    let getAllForCourseSpy: any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgbProgressbar],
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
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CompetencyManagementComponent);
                component = fixture.componentInstance;
                courseCompetencyApiService = TestBed.inject(CourseCompetencyApiService);
                modalService = fixture.debugElement.injector.get(NgbModal);

                const competency: Competency = new Competency();
                const textUnit = new TextUnit();
                competency.id = 1;
                competency.description = 'test';
                competency.lectureUnitLinks = [new CompetencyLectureUnitLink(competency, textUnit, 1)];
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
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should show generate button if IRIS is enabled', async () => {
        profileService = TestBed.inject(ProfileService);
        irisSettingsService = TestBed.inject(IrisSettingsService);
        const profileInfoResponse = {
            activeProfiles: [PROFILE_IRIS],
        } as ProfileInfo;
        const irisSettingsResponse = {
            irisCompetencyGenerationSettings: {
                enabled: true,
            },
        } as IrisCourseSettings;
        const getProfileInfoSpy = jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(of(profileInfoResponse));
        const getIrisSettingsSpy = jest.spyOn(irisSettingsService, 'getCombinedCourseSettings').mockReturnValue(of(irisSettingsResponse));

        component['loadIrisEnabled']();
        fixture.detectChanges();

        const generateButton = fixture.nativeElement.querySelector('#generateButton');

        expect(getProfileInfoSpy).toHaveBeenCalled();
        expect(getIrisSettingsSpy).toHaveBeenCalled();
        expect(generateButton).not.toBeNull();
    });

    it('should load competencies and prerequisites', async () => {
        await component.loadData();

        expect(getAllForCourseSpy).toHaveBeenCalledOnce();

        expect(component.competencies).toHaveLength(2);
        expect(component.prerequisites).toHaveLength(1);
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
        await component.loadData();
        const existingCompetencies = component.competencies.length;

        const importButton = fixture.debugElement.query(By.css('#courseCompetencyImportAllButton'));
        importButton.nativeElement.click();
        fixture.detectChanges();
        await fixture.whenStable();
        expect(modalService.open).toHaveBeenCalledWith(ImportAllCourseCompetenciesModalComponent, {
            size: 'lg',
            backdrop: 'static',
        });
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.competencies).toHaveLength(existingCompetencies + 2);
    });
});
