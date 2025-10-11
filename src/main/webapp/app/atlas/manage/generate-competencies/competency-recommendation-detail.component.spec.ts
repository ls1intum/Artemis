import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CompetencyRecommendationDetailComponent } from 'app/atlas/manage/generate-competencies/competency-recommendation-detail.component';
import { MockProvider } from 'ng-mocks';
import { FormControl, FormGroup } from '@angular/forms';
import { CompetencyTaxonomy } from 'app/atlas/shared/entities/competency.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { AlertService } from 'app/shared/service/alert.service';
import { MonacoEditorService } from 'app/shared/monaco-editor/service/monaco-editor.service';

describe('CompetencyRecommendationDetailComponent', () => {
    let fixture: ComponentFixture<CompetencyRecommendationDetailComponent>;
    let component: CompetencyRecommendationDetailComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CompetencyRecommendationDetailComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(AlertService),
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(MonacoEditorService),
            ],
        })
            .overrideComponent(CompetencyRecommendationDetailComponent, {
                set: {
                    template: `
                        <div [formGroup]="form()!">
                          <div class="rotate-icon" (click)="toggle()"></div>
                          <div id="editButton-{{ index() }}"><div class="jhi-btn" (click)="edit()"></div></div>
                          <div id="saveButton-{{ index() }}"><div class="jhi-btn" (click)="save()"></div></div>
                          <div id="deleteButton-{{ index() }}"><div class="jhi-btn" (click)="delete()"></div></div>
                        </div>
                    `,
                },
            })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CompetencyRecommendationDetailComponent);
                component = fixture.componentInstance;
                global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
                    return new MockResizeObserver(callback);
                });
            });
    });

    beforeEach(() => {
        // initialize inputs
        fixture.componentRef.setInput(
            'form',
            new FormGroup({
                competency: new FormGroup({
                    title: new FormControl('Title' as string | undefined, { nonNullable: true }),
                    description: new FormControl('Description' as string | undefined, { nonNullable: true }),
                    taxonomy: new FormControl(CompetencyTaxonomy.ANALYZE as CompetencyTaxonomy | undefined, { nonNullable: true }),
                }),
                viewed: new FormControl(false, { nonNullable: true }),
            }),
        );
        fixture.componentRef.setInput('index', 0);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).toBeDefined();
    });

    it('should switch between edit and save mode', () => {
        fixture.detectChanges();
        const editSpy = jest.spyOn(component, 'edit');
        const saveSpy = jest.spyOn(component, 'save');

        //component should not start out in edit mode
        expect(component.isInEditMode()).toBeFalse();
        expect(component.form()!.controls.competency.disabled).toBeTrue();

        const editButton = fixture.debugElement.nativeElement.querySelector('#editButton-0 > .jhi-btn');
        editButton.click();

        expect(editSpy).toHaveBeenCalledOnce();
        fixture.detectChanges();

        const saveButton = fixture.debugElement.nativeElement.querySelector('#saveButton-0 > .jhi-btn');
        saveButton.click();

        expect(saveSpy).toHaveBeenCalledOnce();
    });

    it('should delete', () => {
        fixture.detectChanges();
        const deleteSpy = jest.spyOn(component, 'delete');
        const deleteButton = fixture.debugElement.nativeElement.querySelector('#deleteButton-0 > .jhi-btn');

        deleteButton.click();

        expect(deleteSpy).toHaveBeenCalledOnce();
    });

    it('should expand', () => {
        fixture.detectChanges();
        const toggleSpy = jest.spyOn(component, 'toggle');
        const expandIcon = fixture.debugElement.nativeElement.querySelector('.rotate-icon');

        expandIcon.click();

        expect(toggleSpy).toHaveBeenCalledOnce();
    });
});
