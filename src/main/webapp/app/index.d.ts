declare module 'showdown-katex' {
    const main: () => ShowDownExtension;
    export = main;
}

declare module 'showdown-highlight' {
    const main: ({ pre: boolean }) => ShowDownExtension;
    export = main;
}
