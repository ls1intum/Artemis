import type { ReactNode } from 'react';
import Link from '@docusaurus/Link';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faArrowUpRightFromSquare } from '@fortawesome/free-solid-svg-icons';

interface LinkButtonProps {
    to: string;
    children: ReactNode;
    className?: string;
}

export default function LinkButton({ to, children, className = "button button--secondary button--lg" }: LinkButtonProps) {
    return (
        <Link className={className} to={to}>
            {children}
            <FontAwesomeIcon icon={faArrowUpRightFromSquare} style={{ marginLeft: '0.5rem' }} />
        </Link>
    );
}
