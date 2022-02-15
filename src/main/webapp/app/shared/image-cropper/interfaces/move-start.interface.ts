export interface MoveStart {
    active: boolean;
    type?: MoveTypes;
    position?: string;
    x1: number;
    y1: number;
    x2: number;
    y2: number;
    clientX: number;
    clientY: number;
}

export enum MoveTypes {
    Move = 'move',
    Resize = 'resize',
    Pinch = 'pinch',
}
