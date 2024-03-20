from abc import ABC, abstractmethod


class SortStrategy(ABC):

    @abstractmethod
    def perform_sort(self, array):
        pass
