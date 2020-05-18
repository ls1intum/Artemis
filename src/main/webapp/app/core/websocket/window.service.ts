import { Injectable } from '@angular/core';

/**
 * Return the global native browser window object
 */
function _window(): any {
    return window;
}

@Injectable({ providedIn: 'root' })
export class WindowRef {
    get nativeWindow(): any {
        return _window();
    }
}
