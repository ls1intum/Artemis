import unittest
import sorting_algorithms
import sort_strategy
import context
import policy
import structural_helpers


class TestSortingStructural(unittest.TestCase):

    def test_sort_strategy_class(self):
        structural_helpers.check_class_names(sort_strategy, 'SortStrategy')

    def test_sort_strategy_methods(self):
        structural_helpers.check_abstract_method_names(sort_strategy.SortStrategy, 'perform_sort')

    def test_context_class(self):
        structural_helpers.check_class_names(context, 'Context')

    def test_context_attributes(self):
        structural_helpers.check_attributes(context.Context, 'numbers', 'sorting_algorithm')

    def test_context_methods(self):
        structural_helpers.check_method_names(context.Context(), 'sort')

    def test_policy_class(self):
        structural_helpers.check_class_names(policy, 'Policy')

    def test_policy_constructor(self):
        structural_helpers.check_constructor_args(policy.Policy, 'context')

    def test_policy_attributes(self):
        structural_helpers.check_attributes(policy.Policy, 'context')

    def test_policy_methods(self):
        ctx = context.Context()
        structural_helpers.check_method_names(policy.Policy(ctx), 'configure')

    def test_bubble_sort_struct(self):
        bubble_sort = sorting_algorithms.BubbleSort()

        self.assertTrue(issubclass(sorting_algorithms.BubbleSort, sort_strategy.SortStrategy))
        self.assertTrue(callable(bubble_sort.perform_sort))

    def test_merge_sort_struct(self):
        merge_sort = sorting_algorithms.MergeSort()

        self.assertTrue(issubclass(sorting_algorithms.MergeSort, sort_strategy.SortStrategy))
        self.assertTrue(callable(merge_sort.perform_sort))
