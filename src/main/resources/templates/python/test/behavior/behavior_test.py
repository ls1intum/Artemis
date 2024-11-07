import unittest
from ${studentParentWorkingDirectoryName}.sorting_algorithms import *
from ${studentParentWorkingDirectoryName}.context import Context
from ${studentParentWorkingDirectoryName}.policy import Policy


class TestSortingBehavior(unittest.TestCase):
    unordered = []
    ordered = [1, 1, 2, 3, 4, 8]

    def setUp(self):
        self.unordered = [3, 4, 2, 1, 8, 1]

    def test_bubble_sort(self):
        bubble_sort = BubbleSort()
        bubble_sort.perform_sort(self.unordered)
        self.assertEqual(self.ordered, self.unordered)

    def test_merge_sort(self):
        merge_sort = MergeSort()
        merge_sort.perform_sort(self.unordered)
        self.assertEqual(self.ordered, self.unordered)

    def test_merge_sort_for_big_list(self):
        big_list = [42] * 11
        chosen_strategy = self._configure_policy_and_context(big_list)

        self.assertTrue(isinstance(chosen_strategy, MergeSort))

    def test_bubble_sort_for_small_list(self):
        small_list = [42] * 3
        chosen_strategy = self._configure_policy_and_context(small_list)

        self.assertTrue(isinstance(chosen_strategy, BubbleSort))

    def _configure_policy_and_context(self, array):
        context = Context()
        context.numbers = array

        policy = Policy(context)
        policy.configure()

        return context.sorting_algorithm
