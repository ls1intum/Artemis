import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { UnitCreationCardComponent } from 'app/lecture/manage/lecture-units/unit-creation-card/unit-creation-card.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { RouterModule } from '@angular/router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('UnitCreationCardComponent', () => {
    setupTestBed({ zoneless: true });

    let unitCreationCardComponentFixture: ComponentFixture<UnitCreationCardComponent>;
    let unitCreationCardComponent: UnitCreationCardComponent;
    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                RouterModule.forRoot([]),
                FaIconComponent,
                UnitCreationCardComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(DocumentationButtonComponent),
                MockDirective(TranslateDirective),
            ],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        unitCreationCardComponentFixture = TestBed.createComponent(UnitCreationCardComponent);
        unitCreationCardComponent = unitCreationCardComponentFixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        unitCreationCardComponentFixture.detectChanges();
        expect(unitCreationCardComponent).not.toBeNull();
    });

    it('should emit creation card event', () => {
        const emitSpy = vi.spyOn(unitCreationCardComponent.onUnitCreationCardClicked, 'emit');
        unitCreationCardComponentFixture.componentRef.setInput('emitEvents', true);
        unitCreationCardComponent.onButtonClicked(LectureUnitType.ONLINE);
        expect(emitSpy).toHaveBeenCalledWith(LectureUnitType.ONLINE);
    });
});
