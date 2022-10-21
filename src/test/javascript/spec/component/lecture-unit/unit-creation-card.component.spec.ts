import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { UnitCreationCardComponent } from 'app/lecture/lecture-unit/lecture-unit-management/unit-creation-card/unit-creation-card.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';

describe('UnitCreationCardComponent', () => {
    let unitCreationCardComponentFixture: ComponentFixture<UnitCreationCardComponent>;
    let unitCreationCardComponent: UnitCreationCardComponent;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [RouterTestingModule],
            declarations: [UnitCreationCardComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FaIconComponent)],
            providers: [],
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
});
