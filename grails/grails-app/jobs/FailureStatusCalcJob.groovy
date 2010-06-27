import cuanto.TestOutcome
import cuanto.FailureStatusUpdateTask
import cuanto.TestRun

/*
	Copyright (c) 2010 Suk-Hyun Cho

	This file is part of Cuanto, a test results repository and analysis program.

	This program is free software: you can redistribute it and/or modify
	it under the terms of the GNU Lesser General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	This program is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU Lesser General Public License for more details.

	You should have received a copy of the GNU Lesser General Public License
	along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

class FailureStatusCalcJob {
	def dataService
	def testOutcomeService
	def statisticService
	def transactional = true
	def concurrent = false
	static final long INTERVAL = 15000

	static triggers = {
		simple name: "FailureStatusCalc", startDelay: 10000, repeatInterval: INTERVAL
	}

	def execute() {
		def updateTasks = getFailureStatusUpdateTasks(1000)
		def updatedTestOutcomes = []

		updateTasks.each { FailureStatusUpdateTask updateTask ->

			switch (updateTask.type) {
				case TestOutcome.class:
					def updatedTestOutcome = updateTestOutcome(updateTask.targetId)
					if (updatedTestOutcome) {
						updatedTestOutcomes << updatedTestOutcome
						statisticService.queueTestRunStats(updatedTestOutcome.testRun?.id)
					}
					break
				case TestRun.class:
					updatedTestOutcomes + updateTestOutcomesForTestRun(updateTask.targetId)
					statisticService.queueTestRunStats(updateTask.targetId)
					break
			}

			updateTask.delete()
		}

		if (updatedTestOutcomes) {
			dataService.saveTestOutcomes(updatedTestOutcomes)
			log.info "Re-initialized isFailureStatusChanged for ${updatedTestOutcomes.size()} TestOutcomes."
		}
	}

	TestOutcome updateTestOutcome(Long testOutcomeId) {
		def testOutcome = TestOutcome.get(testOutcomeId)
		if (testOutcome) {
			def previousValue = testOutcome.isFailureStatusChanged
			def newValue = testOutcomeService.isFailureStatusChanged(testOutcome)
			if (previousValue != newValue) {
				testOutcome.isFailureStatusChanged = testOutcomeService.isFailureStatusChanged(testOutcome)
				dataService.saveDomainObject testOutcome
				return testOutcome
			}
		}

		log.info "Ignoring TestOutcome $testOutcomeId, " +
			"because it either does not exist or the failure status did not change."

		return null
	}

	List updateTestOutcomesForTestRun(Long testRunId) {
		def updatedOutcomes = []
		def currentBatch = dataService.getTestOutcomesForTestRun(testRunId, 1000, 0)
		while (currentBatch) {
			for (TestOutcome outcome: currentBatch) {
				outcome.isFailureStatusChanged = testOutcomeService.isFailureStatusChanged(outcome)
				updatedOutcomes << outcome
			}

			dataService.saveTestOutcomes currentBatch

			log.info "re-initialized isFailureStatusChanged for ${currentBatch.size()} test outcomes"
			log.info "sleeping ${INTERVAL}ms before updating more test outcomes for test run $testRunId"
			sleep(INTERVAL)

			currentBatch = dataService.getTestOutcomesForTestRun(testRunId, 1000, updatedOutcomes.size() - 1)
		}

		// if there are some outcomes left, save them.
		// this happens when currentBatch.size() > 0 && currentBatch.size() > 1000
		if (currentBatch) {
			log.info "re-initialized isFailureStatusChanged for ${currentBatch.size()} test outcomes"
			dataService.saveTestOutcomes currentBatch
		}

		return updatedOutcomes
	}

	List<FailureStatusUpdateTask> getFailureStatusUpdateTasks(int numToGet) {
		return FailureStatusUpdateTask.list(max: numToGet)
	}
}