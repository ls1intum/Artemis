import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslatePipe } from '@ngx-translate/core';
import { MockPipe } from 'ng-mocks';
import { Component, Input } from '@angular/core';
import { ParticipantScoreAverageDTO, ParticipantScoreDTO } from 'app/shared/participant-scores/participant-scores.service';
import * as chai from 'chai';
import { ParticipantScoresTablesContainerComponent } from 'app/shared/participant-scores/participant-scores-tables-container/participant-scores-tables-container.component';
import { NgbButtonsModule, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { FormsModule } from '@angular/forms';

chai.use(sinonChai);
const expect = chai.expect;

@Component({ selector: 'jhi-participant-scores-table', template: '<div></div>' })
class ParticipantScoresTableStubComponent {
    @Input()
    participantScores: ParticipantScoreDTO[] = [];
    @Input()
    isLoading = false;
}

@Component({ selector: 'jhi-participant-scores-average-table', template: '<div></div>' })
class ParticipantScoresAverageTableStubComponent {
    @Input()
    participantAverageScores: ParticipantScoreAverageDTO[] = [];
    @Input()
    isLoading = false;
}

describe('ParticipantScoresTablesContainer', () => {
    let fixture: ComponentFixture<ParticipantScoresTablesContainerComponent>;
    let component: ParticipantScoresTablesContainerComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [FormsModule, NgbTooltipModule, NgbButtonsModule],
            declarations: [ParticipantScoresTablesContainerComponent, ParticipantScoresTableStubComponent, ParticipantScoresAverageTableStubComponent, MockPipe(TranslatePipe)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ParticipantScoresTablesContainerComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize', () => {
        // this is just a simple container component so we test that the component renders correctly
        fixture.detectChanges();
        expect(component).to.be.ok;
    });
});
