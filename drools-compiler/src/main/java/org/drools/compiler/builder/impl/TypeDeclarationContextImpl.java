/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.drools.compiler.builder.impl;

import org.drools.compiler.builder.PackageRegistryManager;
import org.drools.compiler.compiler.PackageBuilderErrors;
import org.drools.compiler.compiler.PackageRegistry;
import org.drools.core.base.ObjectType;
import org.drools.core.rule.TypeDeclaration;
import org.drools.drl.ast.descr.PackageDescr;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.kie.api.io.Resource;
import org.kie.internal.builder.KnowledgeBuilderResult;
import org.kie.internal.builder.KnowledgeBuilderResults;
import org.kie.internal.builder.ResourceChange;
import org.kie.internal.builder.ResultSeverity;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class TypeDeclarationContextImpl implements TypeDeclarationContext {

    private KnowledgeBuilderConfigurationImpl configuration;
    private final PackageRegistryManager packageRegistryManager;
    private GlobalVariableContext globalVariableContext;
    private final BuildResultCollectorImpl buildResultAccumulator = new BuildResultCollectorImpl();

    private TypeDeclarationManagerImpl typeDeclarationManager;

    public TypeDeclarationContextImpl(KnowledgeBuilderConfigurationImpl configuration, PackageRegistryManager packageRegistryManager, GlobalVariableContext globalVariableContext) {
        this.configuration = configuration;
        this.packageRegistryManager = packageRegistryManager;
        this.globalVariableContext = globalVariableContext;
    }

    public void setTypeDeclarationManager(TypeDeclarationManagerImpl typeDeclarationManagerImpl) {
        this.typeDeclarationManager = typeDeclarationManagerImpl;
    }

    @Override
    public TypeDeclarationBuilder getTypeBuilder() {
        return typeDeclarationManager.getTypeDeclarationBuilder();
    }

    @Override
    public boolean filterAccepts(ResourceChange.Type declaration, String namespace, String typeName) {
        return false;
    }

    @Override
    public TypeDeclaration getAndRegisterTypeDeclaration(Class<?> cls, String name) {
        return typeDeclarationManager.getAndRegisterTypeDeclaration(cls, name);
    }

    @Override
    public TypeDeclaration getTypeDeclaration(ObjectType objectType) {
        return typeDeclarationManager.getTypeDeclaration(objectType);
    }

    @Override
    public TypeDeclaration getTypeDeclaration(Class<?> objectType) {
        return typeDeclarationManager.getTypeDeclaration(objectType);
    }

    @Override
    public List<PackageDescr> getPackageDescrs(String namespace) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public PackageRegistry getPackageRegistry(String packageName) {
        return packageRegistryManager.getPackageRegistry(packageName);
    }

    @Override
    public PackageRegistry getOrCreatePackageRegistry(PackageDescr packageDescr) {
        return packageRegistryManager.getOrCreatePackageRegistry(packageDescr);
    }

    @Override
    public Map<String, PackageRegistry> getPackageRegistry() {
        return packageRegistryManager.getPackageRegistry();
    }

    @Override
    public Collection<String> getPackageNames() {
        // this is not really used by TypeDeclarationContext!!
        return packageRegistryManager.getPackageNames();
    }

    @Override
    public KnowledgeBuilderConfigurationImpl getBuilderConfiguration() {
        return configuration;
    }

    @Override
    public InternalKnowledgeBase getKnowledgeBase() {
        return null;
    }

    @Override
    public ClassLoader getRootClassLoader() {
        return configuration.getClassLoader();
    }

    @Override
    public void addBuilderResult(KnowledgeBuilderResult result) {
        buildResultAccumulator.addBuilderResult(result);
    }

    @Override
    public boolean hasErrors() {
        return buildResultAccumulator.hasErrors();
    }

    public PackageBuilderErrors getErrors() {
        // this is not really used by TypeDeclarationContext!!
        return buildResultAccumulator.getErrors();
    }

    @Override
    public KnowledgeBuilderResults getResults(ResultSeverity... severities) {
        // this is not really used by TypeDeclarationContext!!
        return buildResultAccumulator.getResults(severities);
    }

    @Override
    public boolean hasResults(ResultSeverity... problemTypes) {
        return buildResultAccumulator.hasResults(problemTypes);
    }

    @Override
    public Map<String, Class<?>> getGlobals() {
        return globalVariableContext.getGlobals();
    }

    @Override
    public void addGlobal(String identifier, Class<?> clazz) {
        globalVariableContext.addGlobal(identifier, clazz);
    }
}
