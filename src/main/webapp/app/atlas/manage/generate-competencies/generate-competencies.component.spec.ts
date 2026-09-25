import { vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockDirective, MockProvider } from 'ng-mocks';
import { CompetencyFormControlsWithViewed, GenerateCompetenciesComponent } from 'app/atlas/manage/generate-competencies/generate-competencies.component';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';
import { FormControl, FormGroup } from '@angular/forms';
import { FeatureToggleDirective } from 'app/foundation/feature-toggle/feature-toggle.directive';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ActivatedRoute, Router } from '@angular/router';
import { CompetencyService } from 'app/atlas/manage/services/competency.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { Subject, of } from 'rxjs';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { Competency, CompetencyTaxonomy } from 'app/atlas/shared/entities/competency.model';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { CompetencyRecommendationDetailComponent } from 'app/atlas/manage/generate-competencies/competency-recommendation-detail.component';
import { DocumentationButtonComponent } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { CourseDescriptionFormComponent } from 'app/atlas/manage/generate-competencies/course-description-form.component';
import { CourseCompetencyService } from 'app/atlas/shared/services/course-competency.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { DialogService } from 'primeng/dynamicdialog';
import { IrisRunState } from 'app/iris/shared/entities/iris-activity.model';
import { Course } from 'app/course/shared/entities/course.model';
import { CompetencyRecommendation } from 'app/atlas/manage/generate-competencies/generate-competencies.component';

describe('GenerateCompetenciesComponent', () => {
    let fixture: ComponentFixture<GenerateCompetenciesComponent>;
    let comp: GenerateCompetenciesComponent;
    type GenerationUpdate = { runState: IrisRunState; result?: CompetencyRecommendation[] };
    let mockWebSocketSubject: Subject<GenerationUpdate>;
    let dialogClose: Subject<{ confirmed: boolean } | undefined>;

    beforeEach(() => {
        mockWebSocketSubject = new Subject<GenerationUpdate>();
        dialogClose = new Subject<{ confirmed: boolean } | undefined>();

        TestBed.configureTestingModule({
            imports: [
                GenerateCompetenciesComponent,
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
                        subscribe: vi.fn(() => mockWebSocketSubject.asObservable()),
                    },
                },
                MockProvider(CourseManagementService),
                MockProvider(CourseCompetencyService),
                MockProvider(CompetencyService),
                AlertService,
                ArtemisTranslatePipe,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DialogService, useValue: { open: vi.fn(() => ({ onClose: dialogClose.asObservable() })) } },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(GenerateCompetenciesComponent);
                comp = fixture.componentInstance;
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize the form with the course description', async () => {
        const courseDescription = 'Course Description';
        vi.spyOn(TestBed.inject(CourseManagementService), 'find').mockReturnValue(of(new HttpResponse({ body: { description: courseDescription } })));
        fixture.detectChanges();
        await fixture.whenStable();
        expect(comp.courseDescriptionForm().courseDescriptionControl.value).toBe(courseDescription);
    });

    it('should add competency recommendations', () => {
        fixture.detectChanges();
        const courseDescription = 'Course Description';
        const response = new HttpResponse({
            body: null,
            status: 200,
        });
        const courseCompetencyService = TestBed.inject(CourseCompetencyService);
        const getSpy = vi.spyOn(courseCompetencyService, 'generateCompetenciesFromCourseDescription').mockReturnValue(of(response));

        //expect no recommendations to exist at the start
        expect(fixture.debugElement.queryAll(By.directive(CompetencyRecommendationDetailComponent))).toHaveLength(0);
        expect(comp.competencies.value).toHaveLength(0);

        comp.getCompetencyRecommendations(courseDescription);
        const websocketMessage = {
            runState: IrisRunState.FINISHED,
            result: [{ title: 'Title', description: 'Description', taxonomy: CompetencyTaxonomy.ANALYZE }],
        };
        mockWebSocketSubject.next(websocketMessage);
        fixture.changeDetectorRef.detectChanges();

        expect(fixture.debugElement.queryAll(By.directive(CompetencyRecommendationDetailComponent))).toHaveLength(1);
        expect(comp.competencies.value).toHaveLength(1);
        expect(getSpy).toHaveBeenCalledOnce();
    });

    it('should open modal to remove competency recommendations', () => {
        const dialogService = fixture.debugElement.injector.get<DialogService>(DialogService);
        const openSpy = vi.spyOn(dialogService, 'open');
        comp.competencies.push(createCompetencyFormGroup('Title', 'Description', CompetencyTaxonomy.ANALYZE, true));
        expect(openSpy).not.toHaveBeenCalled();

        comp.onDelete(0);

        expect(openSpy).toHaveBeenCalled();
    });

    it('should cancel', () => {
        fixture.detectChanges();
        const router = fixture.debugElement.injector.get<Router>(Router);
        const navigateSpy = vi.spyOn(router, 'navigate');
        const cancelButton = fixture.debugElement.nativeElement.querySelector('#cancelButton > .jhi-btn');

        cancelButton.click();

        expect(navigateSpy).toHaveBeenCalled();
    });

    it('should deactivate correctly', () => {
        fixture.detectChanges();

        expect(comp.canDeactivate()).toBeTruthy();

        comp.isLoading.set(true);
        expect(comp.canDeactivate()).toBeFalsy();

        comp.submitted = true;
        expect(comp.canDeactivate()).toBeTruthy();
    });

    it('should not submit for unviewed recommendations', async () => {
        fixture.detectChanges();
        const dialogService = fixture.debugElement.injector.get<DialogService>(DialogService);
        const openSpy = vi.spyOn(dialogService, 'open');
        const saveSpy = vi.spyOn(comp, 'save');

        //create competency recomendations that are UNVIEWED
        comp.competencies.push(createCompetencyFormGroup());
        const saveButton = fixture.debugElement.nativeElement.querySelector('#saveButton > .jhi-btn');
        saveButton.click();

        fixture.changeDetectorRef.detectChanges();

        await fixture.whenStable();
        expect(openSpy).toHaveBeenCalledOnce();
        expect(saveSpy).not.toHaveBeenCalled();
    });

    it('should submit', async () => {
        fixture.detectChanges();
        const router = TestBed.inject(Router);
        const dialogService = fixture.debugElement.injector.get<DialogService>(DialogService);
        const competencyService = TestBed.inject(CompetencyService);

        const navigateSpy = vi.spyOn(router, 'navigate');
        const openSpy = vi.spyOn(dialogService, 'open');
        const response: HttpResponse<Competency[]> = new HttpResponse({
            body: [],
            status: 200,
        });
        const createBulkSpy = vi.spyOn(competencyService, 'createBulk').mockReturnValue(of(response));

        //create competency recomendations that are VIEWED
        comp.competencies.push(createCompetencyFormGroup('Title', 'Description', CompetencyTaxonomy.ANALYZE, true));
        const saveButton = fixture.debugElement.nativeElement.querySelector('#saveButton > .jhi-btn');
        saveButton.click();

        fixture.changeDetectorRef.detectChanges();

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
        const generateCompetenciesMock = vi.spyOn(courseCompetencyService, 'generateCompetenciesFromCourseDescription').mockReturnValue(of(response));

        const successMock = vi.spyOn(alertService, 'success');
        comp.getCompetencyRecommendations('Cool course description');
        const websocketMessage = {
            runState: IrisRunState.FINISHED,
            result: [{ title: 'Title', description: 'Description', taxonomy: CompetencyTaxonomy.ANALYZE }],
        };
        mockWebSocketSubject.next(websocketMessage);
        expect(successMock).toHaveBeenCalledOnce();
        expect(generateCompetenciesMock).toHaveBeenCalledOnce();

        const warnMock = vi.spyOn(alertService, 'warning');
        comp.getCompetencyRecommendations('Cool course description');
        const errorMessage = {
            runState: IrisRunState.FAILED,
            result: [{ title: 'Title', description: 'Description', taxonomy: CompetencyTaxonomy.ANALYZE }],
        };
        mockWebSocketSubject.next(errorMessage);
        expect(warnMock).toHaveBeenCalled();
    });

    it('should not deactivate when loading', () => {
        comp.isLoading.set(true);
        const canDeactivate = comp.canDeactivate();
        expect(canDeactivate).toBeFalsy();
    });

    describe('Route and generation lifetime', () => {
        let route: MockActivatedRoute;
        let a: Subject<GenerationUpdate>;
        let b: Subject<GenerationUpdate>;

        beforeEach(() => {
            route = TestBed.inject(ActivatedRoute) as MockActivatedRoute;
            a = new Subject<GenerationUpdate>();
            b = new Subject<GenerationUpdate>();
            const streams: Record<string, Subject<GenerationUpdate>> = { '/user/topic/iris/competencies/1': a, '/user/topic/iris/competencies/2': b };
            vi.spyOn(TestBed.inject(WebsocketService), 'subscribe').mockImplementation((destination) => streams[destination]);
            vi.spyOn(TestBed.inject(CourseManagementService), 'find').mockImplementation((id) => of(new HttpResponse({ body: { id, description: `Course ${id}` } })));
            vi.spyOn(TestBed.inject(CourseCompetencyService), 'getAllForCourse').mockReturnValue(of(new HttpResponse({ body: [] })));
            vi.spyOn(TestBed.inject(CourseCompetencyService), 'generateCompetenciesFromCourseDescription').mockReturnValue(of(new HttpResponse<void>()));
        });

        it('cancels the old competency fetch before another course can receive its description', () => {
            const pending = new Subject<HttpResponse<Competency[]>>();
            vi.mocked(TestBed.inject(CourseCompetencyService).getAllForCourse).mockReturnValue(pending);
            fixture.detectChanges();
            comp.getCompetencyRecommendations('Course A description');
            route.setParameters({ courseId: 2 });
            pending.next(new HttpResponse({ body: [] }));
            expect(TestBed.inject(CourseCompetencyService).generateCompetenciesFromCourseDescription).not.toHaveBeenCalled();
            expect(comp.courseId).toBe(2);
            expect(comp.isLoading()).toBe(false);
        });

        it('cancels a pending generation response without creating a subscription for the new course', () => {
            const pending = new Subject<HttpResponse<void>>();
            vi.mocked(TestBed.inject(CourseCompetencyService).generateCompetenciesFromCourseDescription).mockReturnValue(pending);
            fixture.detectChanges();
            comp.getCompetencyRecommendations('Course A description');
            route.setParameters({ courseId: 2 });
            pending.next(new HttpResponse<void>());
            expect(TestBed.inject(WebsocketService).subscribe).not.toHaveBeenCalled();
            expect(comp.competencies.length).toBe(0);
        });

        it('drops old results and recommendations while the new course can finish normally', () => {
            fixture.detectChanges();
            comp.getCompetencyRecommendations('A');
            a.next({ runState: IrisRunState.RUNNING, result: [{ title: 'A recommendation' }] });
            expect(comp.competencies.getRawValue()[0].competency.title).toBe('A recommendation');
            route.setParameters({ courseId: 2 });
            expect(comp.competencies.length).toBe(0);
            comp.getCompetencyRecommendations('B');
            a.next({ runState: IrisRunState.FINISHED, result: [{ title: 'Late A' }] });
            expect(comp.competencies.length).toBe(0);
            expect(comp.isLoading()).toBe(true);
            b.next({ runState: IrisRunState.FINISHED, result: [{ title: 'B recommendation' }] });
            expect(comp.competencies.getRawValue().map((value) => value.competency.title)).toEqual(['B recommendation']);
            expect(comp.isLoading()).toBe(false);
        });

        it('ignores a stale description response after navigation', async () => {
            const oldDescription = new Subject<HttpResponse<Course>>();
            const newDescription = new Subject<HttpResponse<Course>>();
            vi.mocked(TestBed.inject(CourseManagementService).find).mockImplementation((id) => (id === 1 ? oldDescription : newDescription));
            fixture.detectChanges();
            route.setParameters({ courseId: 2 });
            newDescription.next(new HttpResponse({ body: { description: 'B' } }));
            await Promise.resolve();
            oldDescription.next(new HttpResponse({ body: { description: 'A' } }));
            await Promise.resolve();
            expect(comp.courseDescriptionForm().courseDescriptionControl.value).toBe('B');
        });

        it('ignores duplicate submissions and same-course route emissions during generation', () => {
            fixture.detectChanges();
            comp.getCompetencyRecommendations('A');
            route.setParameters({ courseId: 1 });
            comp.getCompetencyRecommendations('Duplicate');
            expect(TestBed.inject(CourseCompetencyService).generateCompetenciesFromCourseDescription).toHaveBeenCalledExactlyOnceWith(1, 'A', []);
            a.next({ runState: IrisRunState.FINISHED, result: [{ title: 'A' }] });
            expect(comp.competencies.getRawValue().map((value) => value.competency.title)).toEqual(['A']);
        });

        it('silences pending generation and route callbacks on destruction', () => {
            const pending = new Subject<HttpResponse<void>>();
            vi.mocked(TestBed.inject(CourseCompetencyService).generateCompetenciesFromCourseDescription).mockReturnValue(pending);
            fixture.detectChanges();
            comp.getCompetencyRecommendations('A');
            fixture.destroy();
            pending.next(new HttpResponse<void>());
            route.setParameters({ courseId: 2 });
            expect(TestBed.inject(WebsocketService).subscribe).not.toHaveBeenCalled();
            expect(comp.courseId).toBe(1);
        });
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
