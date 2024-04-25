import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { of } from 'rxjs';
import { Competency, CompetencyRelation, CompetencyWithTailRelationDTO, CourseCompetencyProgress } from 'app/entities/competency.model';
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
import { PrerequisiteImportComponent } from 'app/course/competencies/competency-management/prerequisite-import.component';
import { DocumentationButtonComponent } from 'app/shared/components/documentation-button/documentation-button.component';
import { MockHasAnyAuthorityDirective } from '../../../helpers/mocks/directive/mock-has-any-authority.directive';
import { By } from '@angular/platform-browser';
import '@angular/localize/init';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { CompetencyImportCourseComponent, ImportAllFromCourseResult } from 'app/course/competencies/competency-management/competency-import-course.component';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { IrisCourseSettings } from 'app/entities/iris/settings/iris-settings.model';
import { PROFILE_IRIS } from 'app/app.constants';
import { CompetencyRelationGraphStubComponent } from './competency-relation-graph-stub.component';

describe('CompetencyManagementComponent', () => {
    let fixture: ComponentFixture<CompetencyManagementComponent>;
    let component: CompetencyManagementComponent;
    let competencyService: CompetencyService;
    let profileService: ProfileService;
    let irisSettingsService: IrisSettingsService;
    let modalService: NgbModal;

    let getAllForCourseSpy: any;
    let getCourseProgressSpy: any;
    let getAllPrerequisitesForCourseSpy: any;
    let getCompetencyRelationsSpy: any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([]), NgbProgressbar],
            declarations: [
                CompetencyManagementComponent,
                MockHasAnyAuthorityDirective,
                CompetencyRelationGraphStubComponent,
                MockComponent(DocumentationButtonComponent),
                MockComponent(CompetencyImportCourseComponent),
                MockComponent(PrerequisiteImportComponent),
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
                competencyService = TestBed.inject(CompetencyService);
                modalService = fixture.debugElement.injector.get(NgbModal);

                const competency = new Competency();
                const textUnit = new TextUnit();
                competency.id = 1;
                competency.description = 'test';
                competency.lectureUnits = [textUnit];
                const courseCompetencyProgress = new CourseCompetencyProgress();
                courseCompetencyProgress.competencyId = 1;
                courseCompetencyProgress.numberOfStudents = 8;
                courseCompetencyProgress.numberOfMasteredStudents = 5;
                courseCompetencyProgress.averageStudentScore = 90;

                getAllForCourseSpy = jest.spyOn(competencyService, 'getAllForCourse').mockReturnValue(
                    of(
                        new HttpResponse({
                            body: [competency, { id: 5 } as Competency],
                            status: 200,
                        }),
                    ),
                );
                getCourseProgressSpy = jest.spyOn(competencyService, 'getCourseProgress').mockReturnValue(
                    of(
                        new HttpResponse({
                            body: courseCompetencyProgress,
                            status: 200,
                        }),
                    ),
                );
                getAllPrerequisitesForCourseSpy = jest.spyOn(competencyService, 'getAllPrerequisitesForCourse').mockReturnValue(
                    of(
                        new HttpResponse({
                            body: [{ id: 3 } as Competency],
                            status: 200,
                        }),
                    ),
                );
                getCompetencyRelationsSpy = jest
                    .spyOn(competencyService, 'getCompetencyRelations')
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
        fixture.detectChanges();

        expect(getAllForCourseSpy).toHaveBeenCalledOnce();
        expect(getCompetencyRelationsSpy).toHaveBeenCalledOnce();
        expect(getCourseProgressSpy).toHaveBeenCalledTimes(2);
        expect(getAllPrerequisitesForCourseSpy).toHaveBeenCalledOnce();

        expect(component.competencies).toHaveLength(2);
        expect(component.prerequisites).toHaveLength(1);
    });

    it('should delete competency', () => {
        const deleteSpy = jest.spyOn(competencyService, 'delete').mockReturnValue(of(new HttpResponse({ body: {}, status: 200 })));
        fixture.detectChanges();

        component.deleteCompetency(123);

        expect(deleteSpy).toHaveBeenCalledOnce();
        expect(deleteSpy).toHaveBeenCalledWith(123, 1);
    });

    it('should remove prerequisite', () => {
        const removePrerequisiteSpy = jest.spyOn(competencyService, 'removePrerequisite').mockReturnValue(of(new HttpResponse({ body: {}, status: 200 })));
        fixture.detectChanges();

        component.removePrerequisite(123);

        expect(removePrerequisiteSpy).toHaveBeenCalledOnce();
        expect(removePrerequisiteSpy).toHaveBeenCalledWith(123, 1);
    });

    it('should open import modal for prerequisites', () => {
        const importedCompetency: Competency = { id: 456 };
        const modalRef = {
            result: Promise.resolve(importedCompetency),
            componentInstance: {},
        } as NgbModalRef;
        const response = new HttpResponse({
            body: importedCompetency,
            status: 200,
        });
        jest.spyOn(modalService, 'open').mockReturnValue(modalRef);
        jest.spyOn(competencyService, 'addPrerequisite').mockReturnValue(of(response));

        fixture.detectChanges();
        const existingPrerequisites = component.prerequisites.length;

        const importButton = fixture.debugElement.query(By.css('#prerequisiteImportButton'));
        importButton.nativeElement.click();

        expect(modalService.open).toHaveBeenCalledOnce();
        expect(modalService.open).toHaveBeenCalledWith(PrerequisiteImportComponent, { size: 'lg', backdrop: 'static' });
        expect(modalRef.componentInstance.disabledIds).toBeArrayOfSize(3);
        expect(modalRef.componentInstance.disabledIds).toContainAllValues([1, 5, 3]);
        expect(component.prerequisites).toHaveLength(existingPrerequisites + 1);
    });

    it('should open and import modal and update values', () => {
        const modalResult: ImportAllFromCourseResult = {
            courseForImportDTO: { id: 1 },
            importRelations: false,
        };
        const modalRef = {
            result: Promise.resolve(modalResult),
            componentInstance: {},
        } as NgbModalRef;
        const importedCompetencies: CompetencyWithTailRelationDTO[] = [{ competency: { id: 1 }, tailRelations: [{ id: 11 }] }, { competency: { id: 2 } }];
        const response = new HttpResponse({
            body: importedCompetencies,
            status: 200,
        });

        jest.spyOn(modalService, 'open').mockReturnValue(modalRef);
        jest.spyOn(competencyService, 'importAll').mockReturnValue(of(response));
        fixture.detectChanges();
        const existingCompetencies = component.competencies.length;
        const existingRelations = component.relations.length;

        const importButton = fixture.debugElement.query(By.css('#competencyImportAllButton'));
        importButton.nativeElement.click();

        expect(modalService.open).toHaveBeenCalledWith(CompetencyImportCourseComponent, { size: 'lg', backdrop: 'static' });
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
        jest.spyOn(competencyService, 'createCompetencyRelation').mockReturnValue(of(response));
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
        jest.spyOn(competencyService, 'removeCompetencyRelation').mockReturnValue(of(new HttpResponse<any>()));
        fixture.detectChanges();
        component.relations = [{ id: 1 }, { id: 2 }];

        component['removeRelation'](1);

        expect(component.relations).toHaveLength(1);
        expect(component.relations.at(0)?.id).toBe(2);
    });
});
