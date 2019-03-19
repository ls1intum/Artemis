import main


def test_a():
    assert main.complementary('A') == 'T'


def test_t():
    assert main.complementary('T') == 'A'


def test_g():
    assert main.complementary('G') == 'C'


def test_c():
    assert main.complementary('C') == 'G'


def test_complete():
    assert main.complementary('ATGC') == 'TACG'
