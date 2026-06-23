import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { ApollonDiagram } from 'app/modeling/shared/entities/apollon-diagram.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { ApollonDiagramCreateFormComponent } from 'app/quiz/manage/apollon-diagrams/create-form/apollon-diagram-create-form.component';
import { ApollonDiagramService } from 'app/quiz/manage/apollon-diagrams/services/apollon-diagram.service';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockRouter } from 'src/test/javascript/spec/helpers/mocks/mock-router';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { UMLDiagramType } from '@tumaet/apollon';

describe('ApollonDiagramCreateForm Component', () => {
    setupTestBed({ zoneless: true });

    let apollonDiagramService: ApollonDiagramService;
    let dialogRef: DynamicDialogRef;
    let fixture: ComponentFixture<ApollonDiagramCreateFormComponent>;

    const diagram: ApollonDiagram = new ApollonDiagram(UMLDiagramType.ClassDiagram, 123);
    const dialogRefMock = { close: vi.fn() } as unknown as DynamicDialogRef;
    const dialogConfigMock = { data: { apollonDiagram: diagram } } as DynamicDialogConfig;

    beforeEach(async () => {
        diagram.id = 1;

        await TestBed.configureTestingModule({
            imports: [ApollonDiagramCreateFormComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                ApollonDiagramService,
                { provide: DynamicDialogRef, useValue: dialogRefMock },
                { provide: DynamicDialogConfig, useValue: dialogConfigMock },
                SessionStorageService,
                LocalStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
            ],
        })
            .overrideTemplate(ApollonDiagramCreateFormComponent, '<input #titleInput />')
            .compileComponents();

        fixture = TestBed.createComponent(ApollonDiagramCreateFormComponent);
        apollonDiagramService = fixture.debugElement.injector.get(ApollonDiagramService);
        dialogRef = fixture.debugElement.injector.get(DynamicDialogRef);
    });

    afterEach(() => {
        vi.restoreAllMocks();
        vi.clearAllMocks();
    });

    it('should read the diagram from the dialog config on init', () => {
        fixture.detectChanges();
        expect(fixture.componentInstance.apollonDiagram).toBe(diagram);
    });

    it('save', async () => {
        const response: HttpResponse<ApollonDiagram> = new HttpResponse({ body: diagram });
        vi.spyOn(apollonDiagramService, 'create').mockReturnValue(of(response));
        const closeSpy = vi.spyOn(dialogRef, 'close');
        fixture.componentInstance.apollonDiagram = new ApollonDiagram(UMLDiagramType.ClassDiagram, 999);

        // test
        fixture.componentInstance.save();
        await fixture.whenStable();
        expect(closeSpy).toHaveBeenCalledOnce();
        expect(closeSpy).toHaveBeenCalledWith(diagram);
    });

    it('should close the dialog without a result on dismiss', () => {
        const closeSpy = vi.spyOn(dialogRef, 'close');

        fixture.componentInstance.dismiss();

        expect(closeSpy).toHaveBeenCalledOnce();
        expect(closeSpy).toHaveBeenCalledWith();
    });
});
