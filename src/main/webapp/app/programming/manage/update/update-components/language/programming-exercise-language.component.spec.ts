import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseLanguageComponent } from 'app/programming/manage/update/update-components/language/programming-exercise-language.component';
import { programmingExerciseCreationConfigMock } from 'test/helpers/mocks/programming-exercise-creation-config-mock';
import { provideHttpClient } from '@angular/common/http';
import { TheiaService } from 'app/programming/shared/services/theia.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('ProgrammingExerciseLanguageComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ProgrammingExerciseLanguageComponent>;
    let comp: ProgrammingExerciseLanguageComponent;

    let theiaServiceMock!: { getTheiaImages: ReturnType<typeof vi.fn> };

    beforeEach(() => {
        theiaServiceMock = {
            getTheiaImages: vi.fn(),
        };
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                {
                    provide: ActivatedRoute,
                    useValue: { queryParams: of({}) },
                },
                {
                    provide: TheiaService,
                    useValue: theiaServiceMock,
                },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        fixture = TestBed.createComponent(ProgrammingExerciseLanguageComponent);
        comp = fixture.componentInstance;
        fixture.componentRef.setInput('programmingExerciseCreationConfig', programmingExerciseCreationConfigMock);
        fixture.componentRef.setInput('programmingExercise', new ProgrammingExercise(undefined, undefined));
        fixture.componentRef.setInput('isEditFieldDisplayedRecord', {
            programmingLanguage: true,
            projectType: true,
            withExemplaryDependency: true,
            packageName: true,
            enableStaticCodeAnalysis: true,
            sequentialTestRuns: true,
            customizeBuildScript: true,
        });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).not.toBeNull();
    });

    it('should not load TheiaComponent when online IDE is not allowed', () => {
        comp.programmingExercise().allowOnlineIde = false;
        fixture.detectChanges();
        expect(comp.programmingExerciseTheiaComponent()).toBeUndefined();
    });

    it('should load TheiaComponent when online IDE is allowed', () => {
        theiaServiceMock.getTheiaImages.mockReturnValue(of({}));
        comp.programmingExercise().allowOnlineIde = true;
        fixture.detectChanges();
        expect(comp.programmingExerciseTheiaComponent()).not.toBeNull();
    });
});
