class Context:
    sorting_algorithm = None
    numbers = None

    def sort(self):
        self.sorting_algorithm.perform_sort(self.numbers)
