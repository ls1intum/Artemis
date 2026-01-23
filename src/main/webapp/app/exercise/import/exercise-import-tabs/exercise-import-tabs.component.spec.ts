import { expect, vi } from 'vitest';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ExerciseImportTabsComponent } from 'app/exercise/import/exercise-import-tabs/exercise-import-tabs.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { NgbNavModule } from '@ng-bootstrap/ng-bootstrap';
import { MockComponent, MockDirective } from 'ng-mocks';
import { FormsModule } from '@angular/forms';
import { ExerciseImportFromFileComponent } from 'app/exercise/import/from-file/exercise-import-from-file.component';
import { ExerciseImportComponent } from 'app/exercise/import/exercise-import.component';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ExerciseImportTabsComponent', () => {
    setupTestBed({ zoneless: true });
    let fixture: ComponentFixture<ExerciseImportTabsComponent>;
    let comp: ExerciseImportTabsComponent;

    beforeEach(() => {
        TestBed.overrideComponent(ExerciseImportTabsComponent, {
            remove: { imports: [ExerciseImportComponent, ExerciseImportFromFileComponent, HelpIconComponent] },
            add: { imports: [MockComponent(ExerciseImportComponent), MockComponent(ExerciseImportFromFileComponent), MockComponent(HelpIconComponent)] },
        });
        TestBed.configureTestingModule({
            imports: [
                ExerciseImportTabsComponent,
                MockDirective(TranslateDirective),
                NgbNavModule,
                MockComponent(ExerciseImportFromFileComponent),
                MockComponent(HelpIconComponent),
                MockComponent(ExerciseImportComponent),
                FormsModule,
            ],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseImportTabsComponent);
                comp = fixture.componentInstance;
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should show first tab when opened', () => {
        // WHEN
        fixture.detectChanges();
        // THEN
        expect(comp.activeTab).toBe(1);
    });
});
