package com.github.xygeni.intellij.services

import com.intellij.openapi.components.Service
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * A top-level declaration between `@Service` and its class takes the annotation, the class compiles unregistered and
 * `project.service<ScanService>()` throws at runtime. Nothing else catches it: it compiles and no unit test resolves it.
 */
class ServiceAnnotationTest {

    @Test
    fun `services resolved with service() keep their @Service annotation`() {
        for (cls in listOf(ScanService::class.java, InstallerService::class.java, LicenseService::class.java,
            RemediateService::class.java, AIExplainService::class.java)) {
            assertNotNull("${cls.simpleName} lost @Service", cls.getAnnotation(Service::class.java))
        }
    }
}
