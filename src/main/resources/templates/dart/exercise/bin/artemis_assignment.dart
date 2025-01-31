import 'dart:math';

const iterations = 10;
const datesLengthMin = 5;
const datesLengthMax = 15;

final random = Random();

/// Main function.
///
/// Add code to demonstrate your implementation here.
void main() {
  // TODO: Init Context and Policy

  // Run multiple times to simulate different sorting strategies
  for (var i = 0; i < iterations; i++) {
    final dates = createRandomDates();

    // TODO: Configure context

    print("Unsorted List of dates:");
    printDates(dates);

    // TODO: Sort dates

    print("Sorted List of dates:");
    printDates(dates);
  }
}

/// Generates a List of random Date objects with random List size between
/// [datesLengthMin] and [datesLengthMax].
List<DateTime> createRandomDates() {
  final datesLength = randomLargeIntegerWithin(datesLengthMin, datesLengthMax);

  final lowestDate = DateTime(2024, 9, 15);
  final highestDate = DateTime(2025, 1, 15);

  return List.generate(
      datesLength, (_) => randomDateWithin(lowestDate, highestDate));
}

/// Creates a random Date between [minimum] and [maximum].
DateTime randomDateWithin(DateTime minimum, DateTime maximum) {
  final randomMilliseconds = randomLargeIntegerWithin(
      minimum.millisecondsSinceEpoch, maximum.millisecondsSinceEpoch);
  return DateTime.fromMillisecondsSinceEpoch(randomMilliseconds);
}

/// Creates a random 64 bit integer between [minimum] and [maximum].
int randomLargeIntegerWithin(int minimum, int maximum) {
  final low = random.nextInt(1 << 32);
  final high = random.nextInt(1 << 32);
  final randomInteger = (high << 32) | low;
  return randomInteger % (maximum - minimum + 1) + minimum;
}

/// Prints out the given List of dates.
void printDates(List<DateTime> dates) {
  final formattedDates =
      dates.map((date) => date.toIso8601String().substring(0, 10)).join(', ');
  print(formattedDates);
}
