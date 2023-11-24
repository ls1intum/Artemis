import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { DetailOverviewListComponent, DetailOverviewSection, DetailType } from 'app/detail-overview-list/detail-overview-list.component';
import { TranslatePipeMock } from '../helpers/mocks/service/mock-translate.service';
import { MockNgbModalService } from '../helpers/mocks/service/mock-ngb-modal.service';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';

const sections = [
    {
        headline: 'headline.1',
        details: [
            {
                type: DetailType.Text,
                title: 'text',
                data: { text: 'text' },
            },
            false,
        ],
    },
] as DetailOverviewSection[];

describe('DetailOverviewList', () => {
    let component: DetailOverviewListComponent;
    let fixture: ComponentFixture<DetailOverviewListComponent>;
    let modalService: NgbModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [DetailOverviewListComponent, TranslatePipeMock],
            providers: [{ provide: NgbModal, useClass: MockNgbModalService }],
        })
            .overrideTemplate(DetailOverviewListComponent, '')
            .compileComponents()
            .then(() => {
                modalService = fixture.debugElement.injector.get(NgbModal);
            });

        fixture = TestBed.createComponent(DetailOverviewListComponent);
        component = fixture.componentInstance;
    });

    it('should initialize', () => {
        component.sections = sections;
        fixture.detectChanges();
        expect(component.headlines).toStrictEqual([{ id: 'headline-1', translationKey: 'headline.1' }]);
        expect(DetailOverviewListComponent).not.toBeNull();
    });

    it('should return headline id', () => {
        component.headlines = [{ id: 'some-id', translationKey: 'translation.key' }];
        expect(component.getHeadlineId('translation.key')).toBe('some-id');
    });

    it('should open git diff modal', () => {
        const modalSpy = jest.spyOn(modalService, 'open');
        component.showGitDiff(undefined as unknown as ProgrammingExerciseGitDiffReport);
        expect(modalSpy).toHaveBeenCalledOnce();
    });
});
