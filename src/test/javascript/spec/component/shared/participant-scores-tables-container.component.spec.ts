import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe } from 'ng-mocks';
import { Component, Input } from '@angular/core';
import { ParticipantScoreAverageDTO, ParticipantScoreDTO } from 'app/shared/participant-scores/participant-scores.service';
import { ParticipantScoresTablesContainerComponent } from 'app/shared/participant-scores/participant-scores-tables-container/participant-scores-tables-container.component';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { Course } from 'app/entities/course.model';

@Component({ selector: 'jhi-participant-scores-table', template: '<div></div>' })
class ParticipantScoresTableStubComponent {
    @Input()
    participantScores: ParticipantScoreDTO[] = [];
    @Input()
    isLoading = false;
    @Input()
    course?: Course;
}

@Component({ selector: 'jhi-participant-scores-average-table', template: '<div></div>' })
class ParticipantScoresAverageTableStubComponent {
    @Input()
    participantAverageScores: ParticipantScoreAverageDTO[] = [];
    @Input()
    isLoading = false;
    @Input()
    isBonus = false;
    @Input()
    course?: Course;
}

describe('ParticipantScoresTablesContainer', () => {
    let fixture: ComponentFixture<ParticipantScoresTablesContainerComponent>;
    let component: ParticipantScoresTablesContainerComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ReactiveFormsModule, FormsModule, NgbTooltipModule],
            declarations: [
                ParticipantScoresTablesContainerComponent,
                ParticipantScoresTableStubComponent,
                ParticipantScoresAverageTableStubComponent,
                MockPipe(ArtemisTranslatePipe),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ParticipantScoresTablesContainerComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        // this is just a simple container component so we test that the component renders correctly
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });
});
