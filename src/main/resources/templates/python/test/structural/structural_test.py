import unittest
from ${studentParentWorkingDirectoryName} import sorting_algorithms
from ${studentParentWorkingDirectoryName} import sort_strategy
from ${studentParentWorkingDirectoryName} import context
from ${studentParentWorkingDirectoryName} import policy
from structural import structural_helpers


class TestSortingStructural(unittest.TestCase):
    class_error = 'Class %s could not be found in the module %s'
    method_error = 'Method %s could not be found in class %s'
    abstract_method_error = 'Abstract method %s could not be found in class %s'
    attribute_error = 'Attributes %s could not be found in class %s'
    constructor_error = 'Constructor of class %s does not have the following arguments: %s'


    def test_sort_strategy_class(self):
        self.assertTrue(structural_helpers.check_class_names(sort_strategy, 'SortStrategy'),
                        self.class_error % ('SortStrategy', sort_strategy.__name__))

    def test_sort_strategy_methods(self):
        self.assertTrue(structural_helpers.check_abstract_method_names(sort_strategy.SortStrategy, 'perform_sort'),
                        self.abstract_method_error % ('perform_sort', sort_strategy.SortStrategy.__name__))

    def test_context_attributes(self):
        attributes = ['numbers', 'sorting_algorithm']
        self.assertTrue(structural_helpers.check_attributes(context.Context, *attributes),
                        self.attribute_error % (str(attributes), context.Context.__name__))

    def test_context_methods(self):
        self.assertTrue(structural_helpers.check_method_names(context.Context(), 'sort'),
                        self.method_error % ('sort', context.Context.__name__))

    def test_policy_constructor(self):
        self.assertTrue(structural_helpers.check_constructor_args(policy.Policy, 'context'),
                        self.constructor_error % (policy.Policy.__name__, 'context'))

    def test_policy_attributes(self):
        self.assertTrue(structural_helpers.check_attributes(policy.Policy, 'context'),
                        self.attribute_error % ('context', policy.Policy.__name__))

    def test_policy_methods(self):
        ctx = context.Context()
        self.assertTrue(structural_helpers.check_method_names(policy.Policy(ctx), 'configure'),
                        self.method_error % ('configure', policy.Policy.__name__))

    def test_bubble_sort_struct(self):
        bubble_sort = sorting_algorithms.BubbleSort()

        self.assertTrue(issubclass(sorting_algorithms.BubbleSort, sort_strategy.SortStrategy), 'BubbleSort is no subclass of SortStrategy!')
        self.assertTrue(callable(bubble_sort.perform_sort), 'BubbleSort does not implement perform_sort!')

    def test_merge_sort_struct(self):
        merge_sort = sorting_algorithms.MergeSort()

        self.assertTrue(issubclass(sorting_algorithms.MergeSort, sort_strategy.SortStrategy), 'MergSort is no subclass of SortStrategy!')
        self.assertTrue(callable(merge_sort.perform_sort), 'MergeSort does not implement perform_sort!')
