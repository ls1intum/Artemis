import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { Competency, CompetencyRelation, CompetencyWithTailRelationDTO, CourseCompetencyProgress, CourseCompetencyType } from 'app/entities/competency.model';
import { CompetencyManagementComponent } from 'app/course/competencies/competency-management/competency-management.component';
import { ActivatedRoute } from '@angular/router';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { RouterTestingModule } from '@angular/router/testing';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { HttpResponse } from '@angular/common/http';
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
import { ImportAllCompetenciesComponent, ImportAllFromCourseResult } from 'app/course/competencies/competency-management/import-all-competencies.component';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { IrisCourseSettings } from 'app/entities/iris/settings/iris-settings.model';
import { PROFILE_IRIS } from 'app/app.constants';
import { CompetencyRelationGraphStubComponent } from './competency-relation-graph-stub.component';
import { Prerequisite } from 'app/entities/prerequisite.model';
import { CompetencyManagementTableComponent } from 'app/course/competencies/competency-management/competency-management-table.component';
import { CourseCompetencyService } from 'app/course/competencies/course-competency.service';

describe('CompetencyManagementComponent', () => {
    let fixture: ComponentFixture<CompetencyManagementComponent>;
    let component: CompetencyManagementComponent;
    let courseCompetencyService: CourseCompetencyService;
    let profileService: ProfileService;
    let irisSettingsService: IrisSettingsService;
    let modalService: NgbModal;

    let getAllForCourseSpy: any;
    let getCompetencyRelationsSpy: any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([]), NgbProgressbar],
            declarations: [
                CompetencyManagementComponent,
                MockHasAnyAuthorityDirective,
                CompetencyRelationGraphStubComponent,
                MockComponent(DocumentationButtonComponent),
                MockComponent(ImportAllCompetenciesComponent),
                MockComponent(CompetencyManagementTableComponent),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(HtmlForMarkdownPipe),
                MockPipe(ArtemisDatePipe),
                MockDirective(DeleteButtonDirective),
            ],
            providers: [
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
                courseCompetencyService = TestBed.inject(CourseCompetencyService);
                modalService = fixture.debugElement.injector.get(NgbModal);

                const competency: Competency = new Competency();
                const textUnit = new TextUnit();
                competency.id = 1;
                competency.description = 'test';
                competency.lectureUnits = [textUnit];
                const courseCompetencyProgress = new CourseCompetencyProgress();
                courseCompetencyProgress.competencyId = 1;
                courseCompetencyProgress.numberOfStudents = 8;
                courseCompetencyProgress.numberOfMasteredStudents = 5;
                courseCompetencyProgress.averageStudentScore = 90;

                getAllForCourseSpy = jest.spyOn(courseCompetencyService, 'getAllForCourse').mockReturnValue(
                    of(
                        new HttpResponse({
                            body: [competency, { id: 5, type: CourseCompetencyType.COMPETENCY } as Competency, { id: 3, type: CourseCompetencyType.PREREQUISITE } as Prerequisite],
                            status: 200,
                        }),
                    ),
                );
                getCompetencyRelationsSpy = jest
                    .spyOn(courseCompetencyService, 'getCompetencyRelations')
                    .mockReturnValue(of(new HttpResponse({ body: [{ id: 1 } as CompetencyRelation], status: 200 })));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should show generate button if IRIS is enabled', () => {
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

        fixture.detectChanges();
        return fixture.whenStable().then(() => {
            const generateButton = fixture.debugElement.query(By.css('#generateButton'));

            expect(getProfileInfoSpy).toHaveBeenCalled();
            expect(getIrisSettingsSpy).toHaveBeenCalled();
            expect(generateButton).not.toBeNull();
        });
    });

    it('should load competencies and prerequisites', () => {
        component.loadData();

        expect(getAllForCourseSpy).toHaveBeenCalledOnce();
        expect(getCompetencyRelationsSpy).toHaveBeenCalledOnce();

        expect(component.competencies).toHaveLength(2);
        expect(component.prerequisites).toHaveLength(1);
    });

    it('should open import modal and update values', () => {
        const modalResult: ImportAllFromCourseResult = {
            courseForImportDTO: { id: 1 },
            importRelations: false,
        };
        const modalRef = {
            result: Promise.resolve(modalResult),
            componentInstance: {},
        } as NgbModalRef;
        const importedCompetencies: CompetencyWithTailRelationDTO[] = [
            { competency: { id: 1, type: CourseCompetencyType.COMPETENCY }, tailRelations: [{ id: 11 }] },
            { competency: { id: 2, type: CourseCompetencyType.COMPETENCY } },
        ];
        const response = new HttpResponse({
            body: importedCompetencies,
            status: 200,
        });

        jest.spyOn(modalService, 'open').mockReturnValue(modalRef);
        jest.spyOn(courseCompetencyService, 'importAll').mockReturnValue(of(response));
        fixture.detectChanges();
        const existingCompetencies = component.competencies.length;
        const existingRelations = component.relations.length;

        const importButton = fixture.debugElement.query(By.css('#courseCompetencyImportAllButton'));
        importButton.nativeElement.click();

        expect(modalService.open).toHaveBeenCalledWith(ImportAllCompetenciesComponent, { size: 'lg', backdrop: 'static' });
        expect(modalRef.componentInstance.disabledIds).toEqual([1]);
        expect(component.competencies).toHaveLength(existingCompetencies + 2);
        expect(component.relations).toHaveLength(existingRelations + 1);
    });

    it('should handle create relation callback', () => {
        const relation: CompetencyRelation = { id: 1 };
        const response = new HttpResponse({
            body: relation,
            status: 200,
        });
        jest.spyOn(courseCompetencyService, 'createCompetencyRelation').mockReturnValue(of(response));
        fixture.detectChanges();
        const existingRelations = component.relations.length;

        const relationGraph: CompetencyRelationGraphStubComponent = fixture.debugElement.query(By.directive(CompetencyRelationGraphStubComponent)).componentInstance;
        relationGraph.onCreateRelation.emit(relation);

        expect(component.relations).toHaveLength(existingRelations + 1);
    });

    it('should handle remove relation callback', () => {
        const modalRef = {
            result: Promise.resolve(),
            componentInstance: {},
        } as NgbModalRef;
        jest.spyOn(modalService, 'open').mockReturnValue(modalRef);

        fixture.detectChanges();

        const relationGraph: CompetencyRelationGraphStubComponent = fixture.debugElement.query(By.directive(CompetencyRelationGraphStubComponent)).componentInstance;
        relationGraph.onRemoveRelation.emit(1);
        fixture.detectChanges();

        expect(modalService.open).toHaveBeenCalledOnce();
    });

    it('should remove relation', () => {
        jest.spyOn(courseCompetencyService, 'removeCompetencyRelation').mockReturnValue(of(new HttpResponse<any>()));
        fixture.detectChanges();
        component.relations = [{ id: 1 }, { id: 2 }];

        component['removeRelation'](1);

        expect(component.relations).toHaveLength(1);
        expect(component.relations.at(0)?.id).toBe(2);
    });

    it('should remove competency and its relation', () => {
        component.competencies = [
            { id: 1, type: CourseCompetencyType.COMPETENCY },
            { id: 2, type: CourseCompetencyType.COMPETENCY },
        ];
        component.prerequisites = [{ id: 3, type: CourseCompetencyType.PREREQUISITE }];
        component.courseCompetencies = component.competencies.concat(component.prerequisites);
        component.relations = [
            { id: 1, tailCompetency: component.competencies.first(), headCompetency: component.competencies.last() },
            { id: 2, tailCompetency: component.competencies.last(), headCompetency: component.prerequisites.first() },
            { id: 3, tailCompetency: component.prerequisites.first(), headCompetency: component.competencies.first() },
        ];

        component.onRemoveCompetency(2);

        expect(component.relations).toHaveLength(1);
        expect(component.relations.first()?.id).toBe(3);
        expect(component.competencies).toHaveLength(1);
        expect(component.competencies.first()?.id).toBe(1);
        expect(component.prerequisites).toHaveLength(1);
        expect(component.prerequisites.first()?.id).toBe(3);
    });
});
