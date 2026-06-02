import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent } from 'ng-mocks';
import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

import { ExerciseImportTabsComponent } from 'app/exercise/import/exercise-import-tabs/exercise-import-tabs.component';
import { ExerciseImportComponent } from 'app/exercise/import/exercise-import.component';
import { ExerciseImportFromFileComponent } from 'app/exercise/import/from-file/exercise-import-from-file.component';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('ExerciseImportTabsComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ExerciseImportTabsComponent>;
    let comp: ExerciseImportTabsComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideComponent(ExerciseImportTabsComponent, {
                remove: { imports: [ExerciseImportComponent, ExerciseImportFromFileComponent] },
                add: { imports: [MockComponent(ExerciseImportComponent), MockComponent(ExerciseImportFromFileComponent)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ExerciseImportTabsComponent);
        comp = fixture.componentInstance;
        fixture.componentRef.setInput('exerciseType', ExerciseType.PROGRAMMING);
    });

    it('should show first tab when opened', () => {
        fixture.detectChanges();

        expect(comp.activeTab).toBe(1);
    });
});
