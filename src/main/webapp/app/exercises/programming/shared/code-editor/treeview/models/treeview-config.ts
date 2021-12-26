import { Injectable } from '@angular/core';

@Injectable()
export class TreeviewConfig {
    hasAllCheckBox = true;
    hasFilter = false;
    hasCollapseExpand = false;
    decoupleChildFromParent = false;
    maxHeight = 500;

    get hasDivider(): boolean {
        return this.hasFilter || this.hasAllCheckBox || this.hasCollapseExpand;
    }

    public static create(fields?: {
        hasAllCheckBox?: boolean;
        hasFilter?: boolean;
        hasCollapseExpand?: boolean;
        decoupleChildFromParent?: boolean;
        maxHeight?: number;
    }): TreeviewConfig {
        const config = new TreeviewConfig();
        Object.assign(config, fields);
        return config;
    }
}
