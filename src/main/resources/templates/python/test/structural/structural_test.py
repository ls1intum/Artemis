import unittest
from sorting_algorithms import *
from sort_strategy import SortStrategy

class TestSortingStructural(unittest.TestCase):

    def test_sort_strategy_structure(self):
        self.assertTrue('perform_sort' in dir(SortStrategy))

    def test_bubble_sort_structure(self):
        bubble_sort = BubbleSort()

        self.assertTrue(issubclass(BubbleSort, SortStrategy))
        self.assertTrue(callable(bubble_sort.perform_sort))

    def test_merge_sort_structure(self):
        merge_sort = MergeSort()

        self.assertTrue(issubclass(MergeSort, SortStrategy))
        self.assertTrue(callable(merge_sort.perform_sort))
