import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { TutorialGroupsExportButtonComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups/tutorial-groups-management/tutorial-groups-export-button.component/tutorial-groups-export-button.component';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { of } from 'rxjs';
import { MockComponent } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';

describe('TutorialGroupsExportButtonComponent', () => {
    let component: TutorialGroupsExportButtonComponent;
    let fixture: ComponentFixture<TutorialGroupsExportButtonComponent>;
    let tutorialGroupsService: TutorialGroupsService;
    let mockModalRef: NgbModalRef;

    beforeEach(() => {
        mockModalRef = {
            result: Promise.resolve('closed'),
            close: jest.fn(),
            dismiss: jest.fn(),
        } as unknown as NgbModalRef;

        TestBed.configureTestingModule({
            declarations: [TutorialGroupsExportButtonComponent, MockComponent(AlertService)],
            providers: [
                {
                    provide: TutorialGroupsService,
                    useValue: { exportTutorialGroupsToCSV: jest.fn(), exportToJson: jest.fn() },
                },
                { provide: NgbModal, useValue: { open: jest.fn().mockReturnValue(mockModalRef) } },
                { provide: AlertService, useValue: { error: jest.fn() } },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialGroupsExportButtonComponent);
        component = fixture.componentInstance;
        tutorialGroupsService = TestBed.inject(TutorialGroupsService);
    });

    it('should call exportTutorialGroupsToCSV on exportCSV', () => {
        const mockBlob = new Blob(['test'], { type: 'text/csv' });
        jest.spyOn(tutorialGroupsService, 'exportTutorialGroupsToCSV').mockReturnValue(of(mockBlob));
        component.courseId = 1;
        component.selectedFields = ['ID', 'Title', 'Campus', 'Language'];

        component.exportCSV(mockModalRef);

        expect(tutorialGroupsService.exportTutorialGroupsToCSV).toHaveBeenCalledWith(1, ['ID', 'Title', 'Campus', 'Language']);
    });

    it('should call exportToJson on exportJSON', () => {
        const mockResponse = JSON.stringify({ data: 'test' });
        jest.spyOn(tutorialGroupsService, 'exportToJson').mockReturnValue(of(mockResponse));
        component.courseId = 1;
        component.selectedFields = ['ID', 'Title', 'Campus', 'Language'];

        component.exportJSON(mockModalRef);

        expect(tutorialGroupsService.exportToJson).toHaveBeenCalledWith(1, ['ID', 'Title', 'Campus', 'Language']);
    });
});
