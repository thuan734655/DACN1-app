package com.dacn1.core.repository

enum class MockScenario {
    PASSED,
    FAILED_LIVENESS,
    REVIEW_FACE,
    TIMEOUT
}

data class MockConfig(
    val scenario: MockScenario = MockScenario.PASSED,
    val networkDelayMs: Long = 400,
    val statusAdvanceOnEachCall: Boolean = true
)
