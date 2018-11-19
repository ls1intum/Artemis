export type Color =
    | 'navy'
    | 'blue'
    | 'aqua'
    | 'teal'
    | 'olive'
    | 'green'
    | 'lime'
    | 'yellow'
    | 'orange'
    | 'red'
    | 'maroon'
    | 'fuchsia'
    | 'purple'
    | 'black'
    | 'gray'
    | 'silver';

export const colors: Color[] = [
    'yellow',
    'lime',
    'orange',
    'blue',
    'olive',
    'maroon',
    'aqua',
    'red',
    'teal',
    'green',
    'fuchsia',
    'navy',
    'purple',
    'black',
    'gray',
    'silver'
];

export function colorForIndex(index: number): Color {
    return colors[index % colors.length];
}
