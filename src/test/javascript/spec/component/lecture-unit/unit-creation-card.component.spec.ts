import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { UnitCreationCardComponent } from 'app/lecture/lecture-unit/lecture-unit-management/unit-creation-card/unit-creation-card.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { RouterTestingModule } from '@angular/router/testing';
import { DocumentationButtonComponent } from 'app/shared/components/documentation-button/documentation-button.component';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';

describe('UnitCreationCardComponent', () => {
    let unitCreationCardComponentFixture: ComponentFixture<UnitCreationCardComponent>;
    let unitCreationCardComponent: UnitCreationCardComponent;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [RouterTestingModule],
            declarations: [
                UnitCreationCardComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(FaIconComponent),
                MockComponent(DocumentationButtonComponent),
                MockDirective(TranslateDirective),
            ],
            providers: [MockProvider(TranslateService)],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                unitCreationCardComponentFixture = TestBed.createComponent(UnitCreationCardComponent);
                unitCreationCardComponent = unitCreationCardComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        unitCreationCardComponentFixture.detectChanges();
        expect(unitCreationCardComponent).not.toBeNull();
    });

    it('should emit creation card event', () => {
        const emitSpy = jest.spyOn(unitCreationCardComponent.onUnitCreationCardClicked, 'emit');
        unitCreationCardComponent.emitEvents = true;
        unitCreationCardComponent.onButtonClicked(LectureUnitType.ONLINE);
        expect(emitSpy).toHaveBeenCalledWith(LectureUnitType.ONLINE);
    });
});
