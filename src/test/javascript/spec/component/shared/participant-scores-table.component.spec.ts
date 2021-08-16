import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { MockDirective, MockPipe } from 'ng-mocks';
import { JhiTranslateDirective } from 'ng-jhipster';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import * as chai from 'chai';
import { NgbTypeahead } from '@ng-bootstrap/ng-bootstrap';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ArtemisTestModule } from '../../test.module';
import { ParticipantScoreDTO } from 'app/shared/participant-scores/participant-scores.service';
import { By } from '@angular/platform-browser';
import { ParticipantScoresTableComponent } from 'app/shared/participant-scores/participant-scores-table/participant-scores-table.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('ParticipantScoresTable', () => {
    let fixture: ComponentFixture<ParticipantScoresTableComponent>;
    let component: ParticipantScoresTableComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgxDatatableModule],
            declarations: [ParticipantScoresTableComponent, MockPipe(ArtemisTranslatePipe), MockDirective(JhiTranslateDirective), DataTableComponent, MockDirective(NgbTypeahead)],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                {
                    provide: SessionStorageService,
                    useClass: MockSyncStorage,
                },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ParticipantScoresTableComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).to.be.ok;
    });

    it('should render the data in a row', () => {
        const participantScoreDTO = new ParticipantScoreDTO();
        participantScoreDTO.id = 1;
        participantScoreDTO.userId = 42;
        participantScoreDTO.userName = 'testUser';
        participantScoreDTO.exerciseId = 99;
        participantScoreDTO.exerciseTitle = 'testExercise';
        participantScoreDTO.lastResultId = 12;
        participantScoreDTO.lastResultScore = 50;
        participantScoreDTO.lastRatedResultId = 20;
        participantScoreDTO.lastRatedResultScore = 100;
        participantScoreDTO.lastPoints = 13.3;
        participantScoreDTO.lastRatedPoints = 44.4;
        component.isLoading = false;
        component.participantScores = [participantScoreDTO];

        fixture.detectChanges();

        const cellElements = fixture.debugElement.queryAll(By.css('.datatable-body-cell-label > span'));
        expect(cellElements.length).to.equal(13);
        expect(cellElements[0].nativeElement.innerHTML).to.contain(participantScoreDTO.id);
        expect(cellElements[1].nativeElement.innerHTML).to.contain(participantScoreDTO.userId);
        expect(cellElements[2].nativeElement.innerHTML).to.contain(participantScoreDTO.userName);
        expect(cellElements[3].nativeElement.innerHTML).to.contain('');
        expect(cellElements[4].nativeElement.innerHTML).to.contain('');
        expect(cellElements[5].nativeElement.innerHTML).to.contain(participantScoreDTO.exerciseId);
        expect(cellElements[6].nativeElement.innerHTML).to.contain(participantScoreDTO.exerciseTitle);
        expect(cellElements[7].nativeElement.innerHTML).to.contain(participantScoreDTO.lastResultId);
        expect(cellElements[8].nativeElement.innerHTML).to.contain(participantScoreDTO.lastResultScore);
        expect(cellElements[9].nativeElement.innerHTML).to.contain(participantScoreDTO.lastPoints);
        expect(cellElements[10].nativeElement.innerHTML).to.contain(participantScoreDTO.lastRatedResultId);
        expect(cellElements[11].nativeElement.innerHTML).to.contain(participantScoreDTO.lastRatedResultScore);
        expect(cellElements[12].nativeElement.innerHTML).to.contain(participantScoreDTO.lastRatedPoints);
    });

    it('should extract participant name correctly', () => {
        let participantScoreDTO = new ParticipantScoreDTO();
        participantScoreDTO.userName = 'testUser';

        expect(component.extractParticipantName(participantScoreDTO)).to.equal(participantScoreDTO.userName);

        participantScoreDTO = new ParticipantScoreDTO();
        participantScoreDTO.teamName = 'testTeam';
        expect(component.extractParticipantName(participantScoreDTO)).to.equal(participantScoreDTO.teamName);
    });
});
