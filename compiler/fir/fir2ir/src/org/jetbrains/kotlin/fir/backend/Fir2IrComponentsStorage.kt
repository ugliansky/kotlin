/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.backend

import org.jetbrains.kotlin.backend.common.IrSpecialAnnotationsProvider
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.backend.Fir2IrCommonMemberStorage.DataValueClassGeneratedMembersInfo
import org.jetbrains.kotlin.fir.backend.generators.*
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.descriptors.FirModuleDescriptor
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.ir.IrLock
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.impl.IrModuleFragmentImpl
import org.jetbrains.kotlin.ir.moduleVisibilityProvider
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.KotlinMangler
import org.jetbrains.kotlin.ir.util.ModuleVisibilityProvider
import org.jetbrains.kotlin.ir.util.SymbolRemapper
import org.jetbrains.kotlin.ir.util.moduleFragment
import org.jetbrains.kotlin.utils.addToStdlib.runIf

class Fir2IrComponentsStorage(
    override val session: FirSession,
    override val scopeSession: ScopeSession,
    val fir: List<FirFile>,
    override val extensions: Fir2IrExtensions,
    override val configuration: Fir2IrConfiguration,
    override val visibilityConverter: Fir2IrVisibilityConverter,
    commonMemberStorage: Fir2IrCommonMemberStorage,
    generatedDataValueClassSyntheticFunctionsStorage: MutableMap<IrClass, DataValueClassGeneratedMembersInfo>,
    override val irMangler: KotlinMangler.IrMangler,
    kotlinBuiltIns: KotlinBuiltIns,
    createSpecialAnnotationsProvider: ((IrModuleFragment) -> IrSpecialAnnotationsProvider)?,
    override val firProvider: FirProviderWithGeneratedFiles,
    syntheticIrBuiltinsSymbolsContainer: Fir2IrSyntheticIrBuiltinsSymbolsContainer,
    fakeOverrideResolver: SymbolRemapper,
) : Fir2IrComponents {
    override val lock: IrLock = commonMemberStorage.lock

    override val filesBeingCompiled: Set<FirFile>? = runIf(configuration.allowNonCachedDeclarations) { fir.toSet() }

    val module: IrModuleFragment = IrModuleFragmentImpl(
        FirModuleDescriptor.createSourceModuleDescriptor(session, kotlinBuiltIns)
    ).also {
        it.moduleVisibilityProvider = Fir2IrModuleVisibilityProvider(it.descriptor)
    }

    private val conversionScope = Fir2IrConversionScope(configuration)

    override val converter: Fir2IrConverter = Fir2IrConverter(this, conversionScope)

    override val classifierStorage: Fir2IrClassifierStorage = Fir2IrClassifierStorage(this, commonMemberStorage, conversionScope)
    override val declarationStorage: Fir2IrDeclarationStorage = Fir2IrDeclarationStorage(this, module, commonMemberStorage)

    override val callablesGenerator: Fir2IrCallableDeclarationsGenerator = Fir2IrCallableDeclarationsGenerator(this)
    override val classifiersGenerator: Fir2IrClassifiersGenerator = Fir2IrClassifiersGenerator(this)
    override val lazyDeclarationsGenerator: Fir2IrLazyDeclarationsGenerator = Fir2IrLazyDeclarationsGenerator(this)
    override val dataClassMembersGenerator: Fir2IrDataClassMembersGenerator =
        Fir2IrDataClassMembersGenerator(this, generatedDataValueClassSyntheticFunctionsStorage)

    // builtins should go after storages and generators, because they use them during initialization
    override val builtins: Fir2IrBuiltinSymbolsContainer = Fir2IrBuiltinSymbolsContainer(this, syntheticIrBuiltinsSymbolsContainer)

    override val typeConverter: Fir2IrTypeConverter = Fir2IrTypeConverter(this, conversionScope)

    val fir2IrVisitor: Fir2IrVisitor = Fir2IrVisitor(this, conversionScope)

    override val annotationGenerator: AnnotationGenerator = AnnotationGenerator(this)
    override val callGenerator: CallAndReferenceGenerator = CallAndReferenceGenerator(this, fir2IrVisitor, conversionScope)

    override val lazyFakeOverrideGenerator: Fir2IrLazyFakeOverrideGenerator = Fir2IrLazyFakeOverrideGenerator(this)
    override val symbolsMappingForLazyClasses: Fir2IrSymbolsMappingForLazyClasses =
        Fir2IrSymbolsMappingForLazyClasses(fakeOverrideResolver)

    override val annotationsFromPluginRegistrar: Fir2IrIrGeneratedDeclarationsRegistrar = Fir2IrIrGeneratedDeclarationsRegistrar(this)

    override val adapterGenerator: AdapterGenerator = AdapterGenerator(this, fir2IrVisitor, conversionScope)
    override val implicitCastInserter: Fir2IrImplicitCastInserter = Fir2IrImplicitCastInserter(this, conversionScope)

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override val specialAnnotationsProvider: IrSpecialAnnotationsProvider? by lazy {
        createSpecialAnnotationsProvider?.invoke(builtins.anyClass.owner.moduleFragment)
    }
}

internal class Fir2IrModuleVisibilityProvider(
    private val leafModuleDescriptor: ModuleDescriptor,
) : ModuleVisibilityProvider {
    override fun canSeeInternals(thisModule: IrModuleFragment, memberModule: IrModuleFragment): Boolean {
        if (memberModule.moduleVisibilityProvider !is Fir2IrModuleVisibilityProvider) return false

        val thisModuleDesc = thisModule.descriptor
        val memberModuleDesc = memberModule.descriptor
        require(thisModuleDesc is FirModuleDescriptor && memberModuleDesc is FirModuleDescriptor)
        if (thisModuleDesc.shouldSeeInternalsOf(memberModuleDesc)) return true
        /*
         * There could be a case when one declaration is located in common sources and another is located in
         * platform dependencies. For the frontend these two module data don't see each other, but from the
         * backend point of view all sources belong to the same module. So in this situation we also should
         * consider if the other declaration is visible from the leaf source module.
         */

        if (thisModuleDesc.session.kind == FirSession.Kind.Source && thisModuleDesc != leafModuleDescriptor) {
            return leafModuleDescriptor.shouldSeeInternalsOf(memberModuleDesc)
        }

        /*
         * This branch could be useful for the case with classpath substitution, if we would make a fake-override
         * for a library class if it has a supertype from source. But at the moment of writing this code it's
         * effectively unreachable, as fake overrides for library classes are built in the `Fir2IrLazyClass.declarations`
         * which doesn't invoke this code. But it was decided to keep it for symmetry.
         */
        if (memberModuleDesc.session.kind == FirSession.Kind.Source && memberModuleDesc != leafModuleDescriptor) {
            return thisModuleDesc.shouldSeeInternalsOf(leafModuleDescriptor)
        }
        return false
    }
}
