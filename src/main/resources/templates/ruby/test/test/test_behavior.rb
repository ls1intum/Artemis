# frozen_string_literal: true

require_relative "test_helper"

require "date"

class TestBehavior < Minitest::Test
  def setup
    @dates = [
      Date.new(2018, 11, 8),
      Date.new(2017, 4, 15),
      Date.new(2016, 2, 15),
      Date.new(2017, 9, 15)
    ]
    @ordered_dates = [
      Date.new(2016, 2, 15),
      Date.new(2017, 4, 15),
      Date.new(2017, 9, 15),
      Date.new(2018, 11, 8)
    ]
  end

  def test_bubble_sort_sorts
    require "bubble_sort"

    bubble_sort = BubbleSort.new
    bubble_sort.perform_sort(@dates)
    assert_equal(@ordered_dates, @dates, "dates were not sorted")
  end

  def test_merge_sort_sorts
    require "merge_sort"

    merge_sort = MergeSort.new
    merge_sort.perform_sort(@dates)
    assert_equal(@ordered_dates, @dates, "dates were not sorted")
  end

  def test_use_merge_sort_for_big_list
    require "context"
    require "policy"
    require "bubble_sort"

    dates = Array.new(11, 0)
    context = Context.new
    context.dates = dates
    policy = Policy.new(context)
    policy.configure
    assert(context.sort_algorithm.instance_of?(MergeSort), "selected algorithm was not MergeSort")
  end

  def test_use_bubble_sort_for_small_list
    require "context"
    require "policy"
    require "merge_sort"

    dates = Array.new(3, 0)
    context = Context.new
    context.dates = dates
    policy = Policy.new(context)
    policy.configure
    assert(context.sort_algorithm.instance_of?(BubbleSort), "selected algorithm was not BubbleSort")
  end
end
