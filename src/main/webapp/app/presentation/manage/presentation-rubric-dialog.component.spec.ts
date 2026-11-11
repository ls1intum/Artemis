import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiConfirmationService } from '@tumaet/ui-angular';
import { Mock, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AlertService } from 'app/foundation/service/alert.service';
import { PresentationRubricDialogComponent } from 'app/presentation/manage/presentation-rubric-dialog.component';
import { RubricForm } from 'app/presentation/manage/presentation-rubric-form';
import { PresentationRubric, PresentationRubricSave, PresentationRubricScale } from 'app/presentation/shared/entities/presentation-assessment.model';
import { provideArtemisTumAetUiTranslator } from 'app/shared-ui/tum-aet-ui-integration/artemis-tumaet-ui-translator';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

const RUBRIC_URL = 'api/assessment/courses/1/presentation-assessments/42/rubric';

function rubric(assessedInstanceCount: number): PresentationRubric {
    return {
        criteria: [
            { id: 1, name: 'Content', weight: 60, scale: PresentationRubricScale.THREE_POINT, levels: [{ id: 11 }, { id: 12 }, { id: 13 }] },
            { id: 2, name: 'Delivery', weight: 40, scale: PresentationRubricScale.THREE_POINT, levels: [{ id: 21 }, { id: 22 }, { id: 23 }] },
        ],
        assessedInstanceCount,
        version: 7,
    };
}

describe('PresentationRubricDialogComponent', () => {
    let fixture: ComponentFixture<PresentationRubricDialogComponent>;
    let httpMock: HttpTestingController;
    let saved: Mock<(save: PresentationRubricSave) => void>;
    let cancelled: Mock<() => void>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PresentationRubricDialogComponent],
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: TranslateService, useClass: MockTranslateService }, provideArtemisTumAetUiTranslator()],
        }).compileComponents();
        httpMock = TestBed.inject(HttpTestingController);
        fixture = TestBed.createComponent(PresentationRubricDialogComponent);
        fixture.componentRef.setInput('courseId', 1);
        fixture.componentRef.setInput('presentationAssessmentId', 42);
        saved = vi.fn();
        cancelled = vi.fn();
        fixture.componentInstance.saved.subscribe(saved);
        fixture.componentInstance.cancelled.subscribe(cancelled);
    });

    afterEach(() => httpMock.verify());

    async function load(response: PresentationRubric): Promise<RubricForm> {
        fixture.detectChanges();
        httpMock.expectOne({ method: 'GET', url: RUBRIC_URL }).flush(response);
        await fixture.whenStable();
        fixture.detectChanges();
        return fixture.componentInstance['form']()!;
    }

    function query(testId: string): HTMLElement | null {
        return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
    }

    function click(testId: string): void {
        query(testId)!.querySelector('button')!.click();
        fixture.detectChanges();
    }

    function confirmation() {
        return fixture.debugElement.injector.get(TumAetUiConfirmationService).request(undefined);
    }

    it('should show a spinner until the rubric is loaded and then the editor', async () => {
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('tumaet-ui-progress-spinner')).not.toBeNull();

        httpMock.expectOne({ method: 'GET', url: RUBRIC_URL }).flush(rubric(0));
        await fixture.whenStable();
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('tumaet-ui-progress-spinner')).toBeNull();
        expect(query('rubric-criterion-1')).not.toBeNull();
        expect(query('rubric-locked-hint')).toBeNull();
    });

    it('should lock the structure of a rubric whose presentation has assessed instances', async () => {
        const form = await load(rubric(1));

        expect(query('rubric-locked-hint')).not.toBeNull();
        expect(form.at(0).controls.scale.disabled).toBe(true);
    });

    it('should show the errors and not save an invalid rubric', async () => {
        const form = await load(rubric(0));
        form.at(0).controls.weight.setValue(50);

        click('save-rubric-button');

        expect(saved).not.toHaveBeenCalled();
        expect(query('rubric-weight-sum-error')).not.toBeNull();
    });

    it('should save at once while no instance is assessed', async () => {
        const form = await load(rubric(0));
        form.at(0).controls.weight.setValue(70);
        form.at(1).controls.weight.setValue(30);

        click('save-rubric-button');

        expect(confirmation()).toBeUndefined();
        expect(saved).toHaveBeenCalledWith({
            criteria: [expect.objectContaining({ id: 1, weight: 70, scale: PresentationRubricScale.THREE_POINT }), expect.objectContaining({ id: 2, weight: 30 })],
            version: 7,
        });
    });

    it('should save at once when only texts change', async () => {
        const form = await load(rubric(2));
        form.at(0).controls.name.setValue('Structure');

        click('save-rubric-button');

        expect(confirmation()).toBeUndefined();
        expect(saved).toHaveBeenCalledWith({ criteria: [expect.objectContaining({ name: 'Structure', weight: 60 }), expect.objectContaining({ name: 'Delivery' })], version: 7 });
    });

    it('should ask before new weights recalculate assessed results and save once confirmed', async () => {
        const form = await load(rubric(2));
        form.at(0).controls.weight.setValue(70);
        form.at(1).controls.weight.setValue(30);

        click('save-rubric-button');

        expect(saved).not.toHaveBeenCalled();
        expect(confirmation()?.message).toContain('artemisApp.presentationAssessment.rubric.weightChange.message');
        confirmation()!.accept();
        expect(saved).toHaveBeenCalledOnce();
    });

    it('should not save when the recalculation is cancelled', async () => {
        const form = await load(rubric(2));
        form.at(0).controls.weight.setValue(70);
        form.at(1).controls.weight.setValue(30);

        click('save-rubric-button');
        confirmation()!.reject?.();

        expect(saved).not.toHaveBeenCalled();
    });

    it('should report a rubric that cannot be loaded', async () => {
        const errorSpy = vi.spyOn(TestBed.inject(AlertService), 'error');
        fixture.detectChanges();

        httpMock.expectOne({ method: 'GET', url: RUBRIC_URL }).flush(null, { status: 404, statusText: 'Not Found' });
        await fixture.whenStable();
        fixture.detectChanges();

        expect(errorSpy).toHaveBeenCalledWith('error.http.404');
        expect(query('save-rubric-button')).toBeNull();
    });

    it('should not save while saving and close when cancelled', async () => {
        await load(rubric(0));
        fixture.componentRef.setInput('isSaving', true);
        fixture.detectChanges();

        expect((query('save-rubric-button')!.querySelector('button') as HTMLButtonElement).disabled).toBe(true);

        click('cancel-rubric-button');
        expect(cancelled).toHaveBeenCalledOnce();
    });
});
