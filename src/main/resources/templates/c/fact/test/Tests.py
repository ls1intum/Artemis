from fact.tester import Tester

tester = Tester.from_config('exercise.yml')
tester.run()
tester.export_result()
