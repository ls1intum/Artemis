import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { PlagiarismHeaderComponent } from 'app/exercises/shared/plagiarism/plagiarism-header/plagiarism-header.component';
import { ArtemisTestModule } from '../../test.module';
import { MockTranslateService, TranslateTestingModule } from '../../helpers/mocks/service/mock-translate.service';

describe('Plagiarism Header Component', () => {
    let comp: PlagiarismHeaderComponent;
    let fixture: ComponentFixture<PlagiarismHeaderComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateTestingModule],
            declarations: [PlagiarismHeaderComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(PlagiarismHeaderComponent);
        comp = fixture.componentInstance;
    });

    it('should emit when confirming a plagiarism', () => {
        spyOn(comp.plagiarismStatusChange, 'emit');

        const nativeElement = fixture.nativeElement;
        const confirmButton = nativeElement.querySelector("[data-qa='confirm-plagiarism-button']");
        confirmButton.dispatchEvent(new Event('click'));

        fixture.detectChanges();

        expect(comp.plagiarismStatusChange.emit).toHaveBeenCalledWith(true);
    });

    it('should emit when denying a plagiarism', () => {
        spyOn(comp.plagiarismStatusChange, 'emit');

        const nativeElement = fixture.nativeElement;
        const denyButton = nativeElement.querySelector("[data-qa='deny-plagiarism-button']");
        denyButton.dispatchEvent(new Event('click'));

        fixture.detectChanges();

        expect(comp.plagiarismStatusChange.emit).toHaveBeenCalledWith(false);
    });

    it('should emit when toggling split view on the left', () => {
        spyOn(comp.splitViewChange, 'emit');

        const nativeElement = fixture.nativeElement;
        const splitLeftButton = nativeElement.querySelector("[data-qa='split-view-left']");
        splitLeftButton.dispatchEvent(new Event('click'));

        fixture.detectChanges();

        expect(comp.splitViewChange.emit).toHaveBeenCalledWith('left');
    });

    it('should emit when toggling split view on the right', () => {
        spyOn(comp.splitViewChange, 'emit');

        const nativeElement = fixture.nativeElement;
        const splitRightButton = nativeElement.querySelector("[data-qa='split-view-right']");
        splitRightButton.dispatchEvent(new Event('click'));

        fixture.detectChanges();

        expect(comp.splitViewChange.emit).toHaveBeenCalledWith('right');
    });

    it('should emit when toggling split view evenly', () => {
        spyOn(comp.splitViewChange, 'emit');

        const nativeElement = fixture.nativeElement;
        const splitHalfButton = nativeElement.querySelector("[data-qa='split-view-even']");
        splitHalfButton.dispatchEvent(new Event('click'));

        fixture.detectChanges();

        expect(comp.splitViewChange.emit).toHaveBeenCalledWith('even');
    });
});
