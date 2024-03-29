from .sorting_algorithms import *


class Policy:
    context = None

    def __init__(self, context):
        self.context = context

    def configure(self):
        if len(self.context.numbers) > 10:
            print('More than 10 numbers, choosing merge sort!')
            self.context.sorting_algorithm = MergeSort()
        else:
            print('Less or equal than 10 numbers, choosing bubble sort!')
            self.context.sorting_algorithm = BubbleSort()
