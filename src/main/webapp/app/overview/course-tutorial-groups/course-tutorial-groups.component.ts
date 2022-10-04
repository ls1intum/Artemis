import { AfterViewInit, Component, TemplateRef, ViewChild } from '@angular/core';
import { Subject } from 'rxjs';
import { BarControlConfiguration } from 'app/overview/tab-bar/tab-bar';

@Component({
    selector: 'jhi-course-tutorial-groups',
    templateUrl: './course-tutorial-groups.component.html',
    styleUrls: ['./course-tutorial-groups.component.scss'],
})
export class CourseTutorialGroupsComponent implements AfterViewInit {
    @ViewChild('controls', { static: false }) private controls: TemplateRef<any>;
    public readonly controlConfiguration: BarControlConfiguration = {
        subject: new Subject<TemplateRef<any>>(),
        useIndentation: true,
    };

    selectedFilter: 'all' | 'registered' = 'registered';

    constructor() {}

    ngAfterViewInit(): void {
        this.renderTopBarControls();
    }

    public renderTopBarControls() {
        if (this.controls) {
            this.controlConfiguration.subject!.next(this.controls);
        }
    }
}
