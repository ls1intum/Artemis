import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { TutorialGroupsExportButtonComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups/tutorial-groups-management/tutorial-groups-export-button.component/tutorial-groups-export-button.component';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { MockNgbModalService } from '../../../../../helpers/mocks/service/mock-ngb-modal.service';
import { MockComponent } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { AlertService } from 'app/core/util/alert.service';
import { Directive, Input } from '@angular/core';

@Directive({
    selector: '[jhiTranslate]',
})
class MockTranslateDirective {
    @Input() jhiTranslate: string;
}
describe('TutorialGroupsExportButtonComponent', () => {
    let component: TutorialGroupsExportButtonComponent;
    let fixture: ComponentFixture<TutorialGroupsExportButtonComponent>;
    const exampleCourseId = 1;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [TutorialGroupsExportButtonComponent, MockComponent(FaIconComponent), MockTranslateDirective],
            providers: [
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: TutorialGroupsService, useValue: {} },
                { provide: AlertService, useValue: {} },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialGroupsExportButtonComponent);
        component = fixture.componentInstance;
        component.courseId = exampleCourseId;
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should open the export dialog when the button is clicked', fakeAsync(() => {
        const modalService = TestBed.inject(NgbModal);
        const mockModalRef = {
            result: Promise.resolve(),
        };
        const modalOpenSpy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as NgbModalRef);
        const openDialogSpy = jest.spyOn(component, 'openExportDialog');

        const exportButton = fixture.debugElement.nativeElement.querySelector('#exportDialogButton');
        exportButton.click();

        fixture.whenStable().then(() => {
            expect(openDialogSpy).toHaveBeenCalledOnce();
            expect(modalOpenSpy).toHaveBeenCalledOnce();
        });
    }));

    it('should select all fields when toggleSelectAll is called', () => {
        component.toggleSelectAll();
        expect(component.selectAll).toBeTrue();
        expect(component.selectedFields).toHaveLength(component.availableFields.length);
        expect(component.availableFields.every((field) => field.selected)).toBeTrue();
    });

    it('should deselect all fields when toggleSelectAll is called twice', () => {
        component.toggleSelectAll();
        component.toggleSelectAll();
        expect(component.selectAll).toBeFalse();
        expect(component.selectedFields).toHaveLength(0);
        expect(component.availableFields.every((field) => !field.selected)).toBeTrue();
    });

    it('should update selected fields on field selection change', () => {
        const field = component.availableFields[0];
        component.onFieldSelectionChange(field);
        expect(field.selected).toBeTrue();
        expect(component.selectedFields).toContain(field.value);
    });
});
