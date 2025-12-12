import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockDirective, MockProvider } from 'ng-mocks';
import { CompetencyFormControlsWithViewed, GenerateCompetenciesComponent } from 'app/atlas/manage/generate-competencies/generate-competencies.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { FormControl, FormGroup } from '@angular/forms';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ActivatedRoute, Router } from '@angular/router';
import { CompetencyService } from 'app/atlas/manage/services/competency.service';
import { AlertService } from 'app/shared/service/alert.service';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Subject, of } from 'rxjs';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { Competency, CompetencyTaxonomy } from 'app/atlas/shared/entities/competency.model';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { CompetencyRecommendationDetailComponent } from 'app/atlas/manage/generate-competencies/competency-recommendation-detail.component';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { IrisStageStateDTO } from 'app/iris/shared/entities/iris-stage-dto.model';
import { CourseDescriptionFormComponent } from 'app/atlas/manage/generate-competencies/course-description-form.component';
import { CourseCompetencyService } from 'app/atlas/shared/services/course-competency.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { CourseDescriptionFormStubComponent } from 'test/helpers/stubs/atlas/course-description-form-stub.component';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

describe('GenerateCompetenciesComponent', () => {
    let fixture: ComponentFixture<GenerateCompetenciesComponent>;
    let comp: GenerateCompetenciesComponent;
    let mockWebSocketSubject: Subject<any>;

    beforeEach(() => {
        mockWebSocketSubject = new Subject<any>();

        TestBed.configureTestingModule({
            imports: [GenerateCompetenciesComponent],
            declarations: [
                CourseDescriptionFormStubComponent,
                CompetencyRecommendationDetailComponent,
                DocumentationButtonComponent,
                CourseDescriptionFormComponent,
                ButtonComponent,
                ArtemisTranslatePipe,
                MockDirective(FeatureToggleDirective),
                TranslateDirective,
            ],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({ courseId: 1 }) },
                { provide: Router, useClass: MockRouter },
                {
                    provide: WebsocketService,
                    useValue: {
                        subscribe: jest.fn(() => mockWebSocketSubject.asObservable()),
                    },
                },
                CourseDescriptionFormComponent,
                MockProvider(CourseManagementService),
                MockProvider(CourseCompetencyService),
                MockProvider(CompetencyService),
                AlertService,
                ArtemisTranslatePipe,
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideProvider(NgbModal, { useValue: new MockNgbModalService() })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(GenerateCompetenciesComponent);
                comp = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).toBeDefined();
    });

    it('should handle description submit', () => {
        fixture.detectChanges();
        const getCompetencyRecommendationsSpy = jest.spyOn(comp, 'getCompetencyRecommendations').mockReturnValue();

        const courseDescriptionComponent: CourseDescriptionFormComponent = fixture.debugElement.query(By.directive(CourseDescriptionFormComponent)).componentInstance;
        courseDescriptionComponent.formSubmitted.emit('');

        expect(getCompetencyRecommendationsSpy).toHaveBeenCalledOnce();
    });

    it('should initialize the form with the course description', fakeAsync(() => {
        fixture.detectChanges();
        const courseDescription = 'Course Description';

        const courseDescriptionComponent: CourseDescriptionFormComponent = fixture.debugElement.query(By.directive(CourseDescriptionFormComponent)).componentInstance;
        const setCourseDescriptionSpy = jest.spyOn(courseDescriptionComponent, 'setCourseDescription');

        // mock the course returned by CourseManagementService
        const course = { description: courseDescription };
        const courseManagementService = TestBed.inject(CourseManagementService);
        const getCourseSpy = jest.spyOn(courseManagementService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));

        comp.ngOnInit();
        tick();

        expect(getCourseSpy).toHaveBeenCalledOnce();
        expect(setCourseDescriptionSpy).toHaveBeenCalledWith(courseDescription);
    }));

    it('should add competency recommendations', () => {
        fixture.detectChanges();
        const courseDescription = 'Course Description';
        const response = new HttpResponse({
            body: null,
            status: 200,
        });
        const courseCompetencyService = TestBed.inject(CourseCompetencyService);
        const getSpy = jest.spyOn(courseCompetencyService, 'generateCompetenciesFromCourseDescription').mockReturnValue(of(response));

        //expect no recommendations to exist at the start
        expect(fixture.debugElement.queryAll(By.directive(CompetencyRecommendationDetailComponent))).toHaveLength(0);
        expect(comp.competencies.value).toHaveLength(0);

        comp.getCompetencyRecommendations(courseDescription);
        const websocketMessage = {
            stages: [{ state: IrisStageStateDTO.DONE }],
            result: [{ title: 'Title', description: 'Description', taxonomy: CompetencyTaxonomy.ANALYZE }],
        };
        mockWebSocketSubject.next(websocketMessage);
        fixture.detectChanges();

        expect(fixture.debugElement.queryAll(By.directive(CompetencyRecommendationDetailComponent))).toHaveLength(1);
        expect(comp.competencies.value).toHaveLength(1);
        expect(getSpy).toHaveBeenCalledOnce();
    });

    it('should open modal to remove competency recommendations', () => {
        const modalService = fixture.debugElement.injector.get<NgbModal>(NgbModal);
        const openSpy = jest.spyOn(modalService, 'open');
        comp.competencies.push(createCompetencyFormGroup('Title', 'Description', CompetencyTaxonomy.ANALYZE, true));
        expect(openSpy).not.toHaveBeenCalled();

        comp.onDelete(0);

        expect(openSpy).toHaveBeenCalled();
    });

    it('should cancel', () => {
        fixture.detectChanges();
        const router = fixture.debugElement.injector.get<Router>(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');
        const cancelButton = fixture.debugElement.nativeElement.querySelector('#cancelButton > .jhi-btn');

        cancelButton.click();

        expect(navigateSpy).toHaveBeenCalled();
    });

    it('should deactivate correctly', () => {
        fixture.detectChanges();

        expect(comp.canDeactivate()).toBeTrue();

        comp.isLoading = true;
        expect(comp.canDeactivate()).toBeFalse();

        comp.submitted = true;
        expect(comp.canDeactivate()).toBeTrue();
    });

    it('should not submit for unviewed recommendations', async () => {
        fixture.detectChanges();
        const modalService = fixture.debugElement.injector.get<NgbModal>(NgbModal);
        const openSpy = jest.spyOn(modalService, 'open');
        const saveSpy = jest.spyOn(comp, 'save');

        //create competency recomendations that are UNVIEWED
        comp.competencies.push(createCompetencyFormGroup());
        const saveButton = fixture.debugElement.nativeElement.querySelector('#saveButton > .jhi-btn');
        saveButton.click();

        fixture.detectChanges();

        await fixture.whenStable();
        expect(openSpy).toHaveBeenCalledOnce();
        expect(saveSpy).not.toHaveBeenCalled();
    });

    it('should submit', async () => {
        fixture.detectChanges();
        const router = TestBed.inject(Router);
        const modalService = fixture.debugElement.injector.get<NgbModal>(NgbModal);
        const competencyService = TestBed.inject(CompetencyService);

        const navigateSpy = jest.spyOn(router, 'navigate');
        const openSpy = jest.spyOn(modalService, 'open');
        const response: HttpResponse<Competency[]> = new HttpResponse({
            body: [],
            status: 200,
        });
        const createBulkSpy = jest.spyOn(competencyService, 'createBulk').mockReturnValue(of(response));

        //create competency recomendations that are VIEWED
        comp.competencies.push(createCompetencyFormGroup('Title', 'Description', CompetencyTaxonomy.ANALYZE, true));
        const saveButton = fixture.debugElement.nativeElement.querySelector('#saveButton > .jhi-btn');
        saveButton.click();

        fixture.detectChanges();

        await fixture.whenStable();
        expect(openSpy).not.toHaveBeenCalled();
        expect(createBulkSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledOnce();
    });

    it('should display alerts after generating', () => {
        const alertService = TestBed.inject(AlertService);
        const response = new HttpResponse({
            body: null,
            status: 200,
        });
        const courseCompetencyService = TestBed.inject(CourseCompetencyService);
        const generateCompetenciesMock = jest.spyOn(courseCompetencyService, 'generateCompetenciesFromCourseDescription').mockReturnValue(of(response));

        const successMock = jest.spyOn(alertService, 'success');
        comp.getCompetencyRecommendations('Cool course description');
        const websocketMessage = {
            stages: [{ state: IrisStageStateDTO.DONE }],
            result: [{ title: 'Title', description: 'Description', taxonomy: CompetencyTaxonomy.ANALYZE }],
        };
        mockWebSocketSubject.next(websocketMessage);
        expect(successMock).toHaveBeenCalledOnce();
        expect(generateCompetenciesMock).toHaveBeenCalledOnce();

        const warnMock = jest.spyOn(alertService, 'warning');
        comp.getCompetencyRecommendations('Cool course description');
        const errorMessage = {
            stages: [{ state: IrisStageStateDTO.ERROR }],
            result: [{ title: 'Title', description: 'Description', taxonomy: CompetencyTaxonomy.ANALYZE }],
        };
        mockWebSocketSubject.next(errorMessage);
        expect(warnMock).toHaveBeenCalled();
    });

    it('should not deactivate when loading', () => {
        comp.isLoading = true;
        const canDeactivate = comp.canDeactivate();
        expect(canDeactivate).toBeFalse();
    });

    function createCompetencyFormGroup(title?: string, description?: string, taxonomy?: CompetencyTaxonomy, viewed = false): FormGroup<CompetencyFormControlsWithViewed> {
        return new FormGroup({
            competency: new FormGroup({
                title: new FormControl(title, { nonNullable: true }),
                description: new FormControl(description, { nonNullable: true }),
                taxonomy: new FormControl(taxonomy, { nonNullable: true }),
            }),
            viewed: new FormControl(viewed, { nonNullable: true }),
        });
    }
});
