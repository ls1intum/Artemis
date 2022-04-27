import { Component, Input } from '@angular/core';
import { StringCountService } from 'app/exercises/text/participate/string-count.service';

@Component({
    selector: 'jhi-complaints-textarea-counter',
    templateUrl: './complaints-textarea-counter.component.html',
    providers: [],
})
export class ComplaintsTextAreaCounterComponent {
    @Input() maxLength: number;
    @Input() content?: string;
    @Input() visible?: boolean;

    constructor(private stringCountService: StringCountService) {}

    get characterCount(): number {
        return this.stringCountService.countCharacters(this.content);
    }
}
