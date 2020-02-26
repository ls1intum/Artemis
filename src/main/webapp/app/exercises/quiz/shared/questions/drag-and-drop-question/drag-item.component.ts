import { Component, Input, OnInit, ViewEncapsulation } from '@angular/core';
import { DeviceDetectorService, DeviceInfo } from 'ngx-device-detector';
import { DragItem } from 'app/entities/quiz/drag-item.model';

@Component({
    selector: 'jhi-drag-item',
    templateUrl: './drag-item.component.html',
    styleUrls: ['./drag-item.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class DragItemComponent implements OnInit {
    @Input() minWidth: string;
    @Input() dragItem: DragItem;
    @Input() clickDisabled: boolean;
    @Input() invalid: boolean;
    deviceInfo: DeviceInfo;
    isMobile = false;

    constructor(private deviceService: DeviceDetectorService) {}

    ngOnInit(): void {
        this.deviceInfo = this.deviceService.getDeviceInfo();
        this.isMobile = this.deviceService.isMobile();
    }
}
