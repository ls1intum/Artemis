# frozen_string_literal: true

class Context
  attr_accessor :dates, :sort_algorithm

  def sort
    @sort_algorithm.perform_sort(@dates)
  end
end
