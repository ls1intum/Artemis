from .sort_strategy import SortStrategy


class BubbleSort(SortStrategy):

    def perform_sort(self, arr):
        if arr is None:
            return

        for i in range(len(arr))[::-1]:
            for j in range(i):
                if arr[j] > arr[j + 1]:
                    arr[j], arr[j + 1] = arr[j + 1], arr[j]


class MergeSort(SortStrategy):

    def perform_sort(self, arr):
        self.__merge_sort(arr, 0, len(arr) - 1)

    def __merge_sort(self, arr, low, high):
        if high - low < 1:
            return

        mid = int((low + high) / 2)
        self.__merge_sort(arr, low, mid)
        self.__merge_sort(arr, mid + 1, high)
        self.__merge(arr, low, mid, high)

    def __merge(self, arr, low, mid, high):
        temp = [None] * (high - low + 1)

        left_index = low
        right_index = mid + 1
        whole_index = 0

        while left_index <= mid and right_index <= high:
            if arr[left_index] <= arr[right_index]:
                temp[whole_index] = arr[left_index]
                left_index += 1
            else:
                temp[whole_index] = arr[right_index]
                right_index += 1
            whole_index += 1

        if left_index <= mid and right_index > high:
            while left_index <= mid:
                temp[whole_index] = arr[left_index]
                whole_index += 1
                left_index += 1
        else:
            while right_index <= high:
                temp[whole_index] = arr[right_index]
                whole_index += 1
                right_index += 1

        for whole_index in range(len(temp)):
            arr[whole_index + low] = temp[whole_index]
