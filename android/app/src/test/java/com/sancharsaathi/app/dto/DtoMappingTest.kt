package com.sancharsaathi.app.dto

import com.sancharsaathi.app.data.local.DemoScenarioProvider
import com.sancharsaathi.app.domain.model.CaptureSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DtoMappingTest {

    @Test
    fun testDemoScenariosExistAndStructured() {
        val low = DemoScenarioProvider.scenario1LowRisk()
        val susp = DemoScenarioProvider.scenario2Suspicious()
        val high = DemoScenarioProvider.scenario3HighRisk()

        assertEquals("DEMO-LOW-001", low.messageId)
        assertEquals(CaptureSource.DEMO, low.source)
        assertNotNull(low.urls.firstOrNull())

        assertEquals("DEMO-SUSP-002", susp.messageId)
        assertEquals("DEMO-HIGH-003", high.messageId)
    }
}
