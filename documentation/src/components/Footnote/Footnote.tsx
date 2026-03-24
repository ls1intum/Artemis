import React from 'react';

interface FootnoteProps {
    id: string;
}

interface FootnoteTextProps extends FootnoteProps {
    children: React.ReactNode;
}

export const Footnote: React.FC<FootnoteProps> = ({ id }) => {
    return (
        <sup>
            <a href={`#footnote-${id}`} id={`ref-${id}`}>
                [{id}]
            </a>
        </sup>
    );
};

export const FootnoteText: React.FC<FootnoteTextProps> = ({ id, children }) => {
    return (
        <div id={`footnote-${id}`} style={{ display: 'flex', alignItems: 'flex-start', gap: '0.3rem' }}>
            <sup>[{id}]</sup>
            <div>{children}</div>
        </div>
    );
};
