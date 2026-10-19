export function getPositionForKey(key: string): string {
    switch (key) {
        case 'ArrowUp':
            return 'top';
        case 'ArrowRight':
            return 'right';
        case 'ArrowDown':
            return 'bottom';
        case 'ArrowLeft':
        default:
            return 'left';
    }
}

export function getInvertedPositionForKey(key: string): string {
    switch (key) {
        case 'ArrowUp':
            return 'bottom';
        case 'ArrowRight':
            return 'left';
        case 'ArrowDown':
            return 'top';
        case 'ArrowLeft':
        default:
            return 'right';
    }
}

// Synthetic keyboard-driven move events only carry the client coordinates consumed by the cropper's move
// handler; they are cast to MouseEvent to match how the component feeds them into `moveImg`/`getClientX`.
export function getEventForKey(key: string, stepSize: number): MouseEvent {
    let coordinates: Partial<MouseEvent>;
    switch (key) {
        case 'ArrowUp':
            coordinates = { clientX: 0, clientY: stepSize * -1 };
            break;
        case 'ArrowRight':
            coordinates = { clientX: stepSize, clientY: 0 };
            break;
        case 'ArrowDown':
            coordinates = { clientX: 0, clientY: stepSize };
            break;
        case 'ArrowLeft':
        default:
            coordinates = { clientX: stepSize * -1, clientY: 0 };
            break;
    }
    return coordinates as MouseEvent;
}
