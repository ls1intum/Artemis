COMPLEMENTS = {
    'A': 'T',
    'T': 'A',
    'C': 'G',
    'G': 'C'
}


def complementary(strand):
    return ''.join(COMPLEMENTS[character] for character in strand.upper())
