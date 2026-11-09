/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.test.framework.services

import com.intellij.diagnostic.LoadingState
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KaBuiltinsModuleImpl
import org.jetbrains.kotlin.analysis.api.standalone.fir.projectStructure.StandaloneProjectFactory
import org.jetbrains.kotlin.analysis.decompiler.psi.BuiltinsVirtualFileProvider
import org.jetbrains.kotlin.analysis.test.framework.projectStructure.ktTestModuleStructure
import org.jetbrains.kotlin.cli.extensionsStorage
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreApplicationEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreApplicationEnvironmentMode
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreProjectEnvironment
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.compiler.plugin.registerInProject
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.psi.KtPlatformInterface
import org.jetbrains.kotlin.test.services.*

abstract class AnalysisApiEnvironmentManager : TestService {
    abstract val testServices: TestServices
    abstract val testRootDisposable: Disposable
    abstract fun initializeEnvironment()

    fun getProject(): Project =
        getProjectEnvironment().project

    fun getApplication(): Application =
        getApplicationEnvironment().application

    abstract fun initializeProjectStructure()
    abstract fun getProjectEnvironment(): KotlinCoreProjectEnvironment
    abstract fun getApplicationEnvironment(): KotlinCoreApplicationEnvironment
}

class AnalysisApiEnvironmentManagerImpl(
    override val testServices: TestServices,
    override val testRootDisposable: Disposable,
) : AnalysisApiEnvironmentManager() {
    private val _projectEnvironment: KotlinCoreProjectEnvironment by lazy {
        StandaloneProjectFactory.createProjectEnvironment(
            testRootDisposable,
            KotlinCoreApplicationEnvironmentMode.UnitTest,
        )
    }

    override fun initializeEnvironment() {
        testServices.disposableProvider.registerDisposables(
            projectDisposable = _projectEnvironment.parentDisposable,
            applicationDisposable = _projectEnvironment.environment.parentDisposable,
        )

        /**
         * This loading state modification is required to ensure that
         * application components (e.g., Registry service) can be fully used in AA.
         *
         * This must be run right after the environment is initialized since the registry might
         * be accessed during the model construction
         */
        @Suppress("UnstableApiUsage")
        run {
            Registry.markAsLoaded()
            LoadingState.setCurrentState(LoadingState.COMPONENTS_LOADED)
        }
    }

    @OptIn(ExperimentalCompilerApi::class)
    override fun initializeProjectStructure() {
        installDetachedBuiltinsProvider()

        val ktTestModuleStructure = testServices.ktTestModuleStructure
        val useSiteModule = testServices.moduleStructure.modules.first()
        val useSiteCompilerConfiguration =
            testServices.compilerConfigurationProvider.getCompilerConfiguration(useSiteModule, CompilationStage.FIRST)
        val project = getProject()
        val builtinsModule = KaBuiltinsModuleImpl(useSiteModule.targetPlatform(testServices), project)

        val globalLanguageVersionSettings = useSiteModule.languageVersionSettings

        StandaloneProjectFactory.registerServicesForProjectEnvironment(
            _projectEnvironment,
            KotlinTestProjectStructureProvider(globalLanguageVersionSettings, builtinsModule, ktTestModuleStructure),
            useSiteCompilerConfiguration.languageVersionSettings,
            useSiteCompilerConfiguration.get(JVMConfigurationKeys.JDK_HOME)?.toPath(),
        )
        val extensionStorage = useSiteCompilerConfiguration.extensionsStorage ?: error("Extensions storage is not registered")
        testServices.compilerConfigurationProvider.registerCompilerExtensions(extensionStorage, useSiteModule, useSiteCompilerConfiguration)
        extensionStorage.registerInProject(project) { "Error during registering compiler extensions: $it" }
        testServices.compilerConfigurationProvider.configureProject(project, useSiteModule, useSiteCompilerConfiguration)
    }

    /**
     * Installs the detached test [BuiltinsVirtualFileProvider] as the application service for the fallback
     * built-ins, in place of the CLI implementation from `analysis-api-standalone-fir.xml`.
     *
     * On the test classpath, the built-ins resources sit in the same `kotlin-stdlib` jar that test modules also
     * depend on as a binary root. The CLI provider resolves the fallback built-ins to a classpath VirtualFile
     * that then collides with the dependency-mounted one, so the same built-ins file gets decompiled into two
     * PSI trees, and PSI elements from different trees are mutually invalid. The detached provider serves the
     * same built-ins content from in-memory files instead, which cannot collide with the mounted jar.
     *
     * A plain `registerService` call is not enough here: the XML descriptor registers the CLI implementation
     * as a service implementation class, and the container keeps constructing it on `getService`, so the
     * adapter must be unregistered first (the same pattern as the IJPL-237691 workaround in
     * `FirStandaloneServiceRegistrar`).
     *
     * This runs on every test before the modules are indexed: `registerProjectModelServices` reads the
     * provider to decompile the fallback built-ins, but it runs only after the service registrars have loaded
     * the XML descriptor, which is why the provider is replaced here and not at environment creation.
     */
    @OptIn(KtPlatformInterface::class)
    private fun installDetachedBuiltinsProvider() {
        val application = getApplicationEnvironment().application
        KotlinCoreEnvironment.underApplicationLock {
            // Idempotent: a single provider per application keeps the detached built-ins files stable across
            // tests, which lets the application-level KotlinStandaloneIndexCache share them between tests.
            if (application.getServiceIfCreated(BuiltinsVirtualFileProvider::class.java) is BuiltinsVirtualFileProviderTestImpl) {
                return@underApplicationLock
            }
            application.picoContainer.unregisterComponent(BuiltinsVirtualFileProvider::class.java.name)
            application.registerService(BuiltinsVirtualFileProvider::class.java, BuiltinsVirtualFileProviderTestImpl())
        }
    }

    override fun getProjectEnvironment(): KotlinCoreProjectEnvironment =
        _projectEnvironment

    override fun getApplicationEnvironment(): KotlinCoreApplicationEnvironment =
        _projectEnvironment.environment as KotlinCoreApplicationEnvironment

}

val TestServices.environmentManager: AnalysisApiEnvironmentManager by TestServices.testServiceAccessor()
