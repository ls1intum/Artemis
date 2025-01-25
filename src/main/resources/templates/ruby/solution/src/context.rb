# frozen_string_literal: true

class Context
  attr_accessor :dates, :sort_algorithm

  # Runs the configured sort algorithm.
  def sort
    raise "sort_algorithm not set" if @sort_algorithm.nil?
    raise "dates not set" if @dates.nil?

    @sort_algorithm.perform_sort(@dates)
  end
end
