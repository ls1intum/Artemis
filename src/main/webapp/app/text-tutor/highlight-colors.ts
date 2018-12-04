export module HighlightColors {

    export enum Color {
        yellow = 'yellow',
        lime = 'lime',
        orange = 'orange',
        blue = 'blue',
        olive = 'olive',
        maroon = 'maroon',
        aqua = 'aqua',
        red = 'red',
        teal = 'teal',
        green = 'green',
        fuchsia = 'fuchsia',
        navy = 'navy',
        purple = 'purple',
        black = 'black',
        gray = 'gray',
        silver = 'silver'
    }

    export function forIndex(index: number): Color {
        const colors = Object.values(Color);
        return colors[index % colors.length];
    }

}
