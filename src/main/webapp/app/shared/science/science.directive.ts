import { Directive, HostListener, Input, inject } from '@angular/core';
import { ScienceService } from 'app/shared/science/science.service';
import { ScienceEventType } from 'app/shared/science/science.model';
@Directive({
    selector: '[jhiScience]',
})
export class ScienceDirective {
    private scienceService = inject(ScienceService);

    @Input() jhiScience: ScienceEventType;

    /**
     * Function is executed when a MouseEvent is registered. Sends request to science api
     */
    @HostListener('click')
    onClick() {
        this.scienceService.logEvent(this.jhiScience);
    }
}
