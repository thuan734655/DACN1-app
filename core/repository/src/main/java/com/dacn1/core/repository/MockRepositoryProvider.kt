package com.dacn1.core.repository

object MockRepositoryProvider {
    fun create(scenario: MockScenario = MockScenario.PASSED): EkycRepository {
        return MockEkycRepository(
            config = MockConfig(
                scenario = scenario,
                networkDelayMs = 400,
                statusAdvanceOnEachCall = true
            )
        )
    }
}
