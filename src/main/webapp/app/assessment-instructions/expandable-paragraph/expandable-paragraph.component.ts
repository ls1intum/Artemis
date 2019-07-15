import { Component, OnInit, Input } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';

@Component({
    selector: 'jhi-expandable-paragraph',
    templateUrl: './expandable-paragraph.component.html',
    styleUrls: ['../assessment-instructions.scss'],
})
export class ExpandableParagraphComponent implements OnInit {
    @Input() header = 'Toggle paragraph';
    @Input() text: SafeHtml;
    @Input() isCollapsed = false;
    constructor() {}

    ngOnInit() {}
}
