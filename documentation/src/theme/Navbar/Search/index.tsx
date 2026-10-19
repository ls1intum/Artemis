import type { ReactNode } from 'react';
import { useLocation } from '@docusaurus/router';
import { SearchModalTrigger } from '../../../components/SearchModal';

export default function NavbarSearchWrapper(): ReactNode {
    const { pathname } = useLocation();

    if (pathname === '/') {
        return null;
    }

    return <SearchModalTrigger variant="navbar" />;
}
