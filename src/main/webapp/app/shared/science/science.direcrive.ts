import { Directive, HostListener, Input } from '@angular/core';
import { ScienceService } from 'app/shared/science/science.service';
import { ScienceEventType } from 'app/shared/science/science.model';
@Directive({
    selector: '[jhiScience]',
})
export class ScienceDirective {
    @Input() jhiScience: ScienceEventType;
    constructor(private scienceService: ScienceService) {}

    /**
     * Function is executed when a MouseEvent is registered. Sends request to science api
     */
    @HostListener('click')
    onClick() {
        this.scienceService.logEvent(this.jhiScience);
    }
}
