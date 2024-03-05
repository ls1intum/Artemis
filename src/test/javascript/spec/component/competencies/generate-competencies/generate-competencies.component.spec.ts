import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTestModule } from '../../../test.module';
import { CompetencyFormControlsWithViewed, GenerateCompetenciesComponent } from 'app/course/competencies/generate-competencies/generate-competencies.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { NgbTooltipMocksModule } from '../../../helpers/mocks/directive/ngbTooltipMocks.module';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ActivatedRoute, Router } from '@angular/router';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { AlertService } from 'app/core/util/alert.service';
import { MockNgbModalService } from '../../../helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { of } from 'rxjs';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { CourseDescriptionFormStubComponent } from './course-description-form-stub.component';
import { MockActivatedRoute } from '../../../helpers/mocks/activated-route/mock-activated-route';
import { Competency, CompetencyTaxonomy } from 'app/entities/competency.model';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { CompetencyRecommendationDetailComponent } from 'app/course/competencies/generate-competencies/competency-recommendation-detail.component';

describe('GenerateCompetenciesComponent', () => {
    let generateCompetenciesComponentFixture: ComponentFixture<GenerateCompetenciesComponent>;
    let generateCompetenciesComponent: GenerateCompetenciesComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ReactiveFormsModule, NgbTooltipMocksModule],
            declarations: [
                GenerateCompetenciesComponent,
                CourseDescriptionFormStubComponent,
                MockComponent(CompetencyRecommendationDetailComponent),
                ButtonComponent,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(FeatureToggleDirective),
                MockDirective(TranslateDirective),
            ],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: new MockActivatedRoute({ courseId: 1 }),
                },
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: Router, useClass: MockRouter },
                MockProvider(CompetencyService),
                MockProvider(AlertService),
                MockProvider(ArtemisTranslatePipe),
            ],
        })
            .compileComponents()
            .then(() => {
                generateCompetenciesComponentFixture = TestBed.createComponent(GenerateCompetenciesComponent);
                generateCompetenciesComponent = generateCompetenciesComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        generateCompetenciesComponentFixture.detectChanges();
        expect(generateCompetenciesComponent).toBeDefined();
    });

    it('should handle description submit', () => {
        generateCompetenciesComponentFixture.detectChanges();
        const getCompetencyRecommendationsSpy = jest.spyOn(generateCompetenciesComponent, 'getCompetencyRecommendations').mockReturnValue();

        const courseDescriptionComponent: CourseDescriptionFormStubComponent = generateCompetenciesComponentFixture.debugElement.query(
            By.directive(CourseDescriptionFormStubComponent),
        ).componentInstance;
        courseDescriptionComponent.formSubmitted.emit('');

        expect(getCompetencyRecommendationsSpy).toHaveBeenCalledOnce();
    });

    it('should add competency recommendations', () => {
        generateCompetenciesComponentFixture.detectChanges();
        const courseDescription = 'Course Description';
        const response = new HttpResponse({
            body: [new Competency(), new Competency()],
            status: 200,
        });
        const competencyService = TestBed.inject(CompetencyService);
        const getSpy = jest.spyOn(competencyService, 'generateCompetenciesFromCourseDescription').mockReturnValue(of(response));

        //expect no recommendations to exist at the start
        expect(generateCompetenciesComponentFixture.debugElement.queryAll(By.directive(CompetencyRecommendationDetailComponent))).toHaveLength(0);
        expect(generateCompetenciesComponent.competencies.value).toHaveLength(0);

        generateCompetenciesComponent.getCompetencyRecommendations(courseDescription);
        generateCompetenciesComponentFixture.detectChanges();

        expect(generateCompetenciesComponentFixture.debugElement.queryAll(By.directive(CompetencyRecommendationDetailComponent))).toHaveLength(2);
        expect(generateCompetenciesComponent.competencies.value).toHaveLength(2);
        expect(getSpy).toHaveBeenCalledOnce();
    });

    it('should open modal to remove competency recommendations', () => {
        const modalService: NgbModal = TestBed.inject(NgbModal);
        const openSpy = jest.spyOn(modalService, 'open');
        generateCompetenciesComponent.competencies.push(createCompetencyFormGroup('Title', 'Description', CompetencyTaxonomy.ANALYZE, true));
        expect(openSpy).not.toHaveBeenCalled();

        generateCompetenciesComponent.onDelete(0);

        expect(openSpy).toHaveBeenCalled();
    });

    it('should cancel', () => {
        generateCompetenciesComponentFixture.detectChanges();
        const router: Router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');
        const cancelButton = generateCompetenciesComponentFixture.debugElement.nativeElement.querySelector('#cancelButton > .jhi-btn');

        cancelButton.click();

        expect(navigateSpy).toHaveBeenCalled();
    });

    it('should deactivate correctly', () => {
        generateCompetenciesComponentFixture.detectChanges();

        expect(generateCompetenciesComponent.canDeactivate()).toBeTrue();

        generateCompetenciesComponent.isLoading = true;
        expect(generateCompetenciesComponent.canDeactivate()).toBeFalse();

        generateCompetenciesComponent.submitted = true;
        expect(generateCompetenciesComponent.canDeactivate()).toBeTrue();
    });

    it('should not submit for unviewed recommendations', () => {
        generateCompetenciesComponentFixture.detectChanges();
        const modalService: NgbModal = TestBed.inject(NgbModal);
        const openSpy = jest.spyOn(modalService, 'open');
        const saveSpy = jest.spyOn(generateCompetenciesComponent, 'save');

        //create competency recomendations that are UNVIEWED
        generateCompetenciesComponent.competencies.push(createCompetencyFormGroup());
        const saveButton = generateCompetenciesComponentFixture.debugElement.nativeElement.querySelector('#saveButton > .jhi-btn');
        saveButton.click();

        generateCompetenciesComponentFixture.detectChanges();

        return generateCompetenciesComponentFixture.whenStable().then(() => {
            expect(openSpy).toHaveBeenCalledOnce();
            expect(saveSpy).not.toHaveBeenCalled();
        });
    });

    it('should submit', () => {
        generateCompetenciesComponentFixture.detectChanges();
        const router: Router = TestBed.inject(Router);
        const modalService: NgbModal = TestBed.inject(NgbModal);
        const competencyService: CompetencyService = TestBed.inject(CompetencyService);

        const navigateSpy = jest.spyOn(router, 'navigate');
        const openSpy = jest.spyOn(modalService, 'open');
        const response: HttpResponse<Competency[]> = new HttpResponse({
            body: [],
            status: 200,
        });
        const createBulkSpy = jest.spyOn(competencyService, 'createBulk').mockReturnValue(of(response));

        //create competency recomendations that are VIEWED
        generateCompetenciesComponent.competencies.push(createCompetencyFormGroup('Title', 'Description', CompetencyTaxonomy.ANALYZE, true));
        const saveButton = generateCompetenciesComponentFixture.debugElement.nativeElement.querySelector('#saveButton > .jhi-btn');
        saveButton.click();

        generateCompetenciesComponentFixture.detectChanges();

        return generateCompetenciesComponentFixture.whenStable().then(() => {
            expect(openSpy).not.toHaveBeenCalled();
            expect(createBulkSpy).toHaveBeenCalledOnce();
            expect(navigateSpy).toHaveBeenCalledOnce();
        });
    });

    it('should display alerts after generating', () => {
        const alertService = TestBed.inject(AlertService);
        const competencyService = TestBed.inject(CompetencyService);
        const generateCompetenciesMock = jest.spyOn(competencyService, 'generateCompetenciesFromCourseDescription');

        generateCompetenciesMock.mockReturnValue(of({ body: [{ id: 1 }] } as HttpResponse<Competency[]>));
        const successMock = jest.spyOn(alertService, 'success');
        generateCompetenciesComponent.getCompetencyRecommendations('');
        expect(successMock).toHaveBeenCalledOnce();

        generateCompetenciesMock.mockReturnValue(of({} as HttpResponse<Competency[]>));
        const warnMock = jest.spyOn(alertService, 'warning');
        generateCompetenciesComponent.getCompetencyRecommendations('');
        expect(warnMock).toHaveBeenCalled();
    });

    it('should send a warning when trying to reload', () => {
        generateCompetenciesComponent.isLoading = true;
        const event = { returnValue: undefined };
        generateCompetenciesComponent.unloadNotification(event);
        expect(event.returnValue).toBe(generateCompetenciesComponent.canDeactivateWarning);
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
