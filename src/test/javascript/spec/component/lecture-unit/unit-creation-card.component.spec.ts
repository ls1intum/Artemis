import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { UnitCreationCardComponent } from 'app/lecture/lecture-unit/lecture-unit-management/unit-creation-card/unit-creation-card.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { RouterTestingModule } from '@angular/router/testing';

chai.use(sinonChai);
const expect = chai.expect;
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

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize', () => {
        unitCreationCardComponentFixture.detectChanges();
        expect(unitCreationCardComponent).to.be.ok;
    });
});
