/*
 * Copyright 2005 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import org.drools.compiler.compiler.Dialect;
import org.drools.compiler.compiler.DialectCompiletimeRegistry;
import org.drools.compiler.compiler.DialectConfiguration;
import org.drools.compiler.compiler.PackageRegistry;
import org.drools.compiler.kie.builder.impl.InternalKieModule.CompilationCache;
import org.drools.compiler.rule.builder.ConstraintBuilder;
import org.drools.compiler.rule.builder.EvaluatorDefinition;
import org.drools.compiler.rule.builder.util.AccumulateUtil;
import org.drools.core.definitions.InternalKnowledgePackage;
import org.drools.drl.parser.DrlParser;
import org.drools.util.StringUtils;
import org.drools.wiring.api.classloader.ProjectClassLoader;
import org.kie.api.runtime.rule.AccumulateFunction;
import org.kie.internal.builder.KnowledgeBuilderConfiguration;
import org.kie.internal.builder.ResultSeverity;
import org.kie.internal.builder.conf.AccumulateFunctionOption;
import org.kie.internal.builder.conf.AlphaNetworkCompilerOption;
import org.kie.internal.builder.conf.DefaultDialectOption;
import org.kie.internal.builder.conf.DefaultPackageNameOption;
import org.kie.internal.builder.conf.DumpDirOption;
import org.kie.internal.builder.conf.EvaluatorOption;
import org.kie.internal.builder.conf.ExternaliseCanonicalModelLambdaOption;
import org.kie.internal.builder.conf.GroupDRLsInKieBasesByFolderOption;
import org.kie.internal.builder.conf.KBuilderSeverityOption;
import org.kie.internal.builder.conf.KnowledgeBuilderOption;
import org.kie.internal.builder.conf.LanguageLevelOption;
import org.kie.internal.builder.conf.MultiValueKnowledgeBuilderOption;
import org.kie.internal.builder.conf.ParallelLambdaExternalizationOption;
import org.kie.internal.builder.conf.ParallelRulesBuildThresholdOption;
import org.kie.internal.builder.conf.ProcessStringEscapesOption;
import org.kie.internal.builder.conf.PropertySpecificOption;
import org.kie.internal.builder.conf.SingleValueKnowledgeBuilderOption;
import org.kie.internal.builder.conf.TrimCellsInDTableOption;
import org.kie.internal.utils.ChainedProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class configures the package compiler.
 * Dialects and their DialectConfigurations  are handled by the DialectRegistry
 * Normally you will not need to look at this class, unless you want to override the defaults.
 *
 * This class is not thread safe and it also contains state. Once it is created and used
 * in one or more PackageBuilders it should be considered immutable. Do not modify its
 * properties while it is being used by a PackageBuilder.
 *
 * drools.dialect.default = <String>
 * drools.accumulate.function.<function name> = <qualified class>
 * drools.evaluator.<ident> = <qualified class>
 * drools.dump.dir = <String>
 * drools.classLoaderCacheEnabled = true|false
 * drools.parallelRulesBuildThreshold = <int>
 *
 * default dialect is java.
 * Available preconfigured Accumulate functions are:
 * drools.accumulate.function.average = org.kie.base.accumulators.AverageAccumulateFunction
 * drools.accumulate.function.max = org.kie.base.accumulators.MaxAccumulateFunction
 * drools.accumulate.function.min = org.kie.base.accumulators.MinAccumulateFunction
 * drools.accumulate.function.count = org.kie.base.accumulators.CountAccumulateFunction
 * drools.accumulate.function.sum = org.kie.base.accumulators.SumAccumulateFunction
 * 
 * drools.parser.processStringEscapes = true|false
 * 
 * 
 * drools.problem.severity.<ident> = ERROR|WARNING|INFO
 * 
 */
public class KnowledgeBuilderConfigurationImpl
        implements
        KnowledgeBuilderConfiguration {

    public static final String                DEFAULT_PACKAGE = "defaultpkg";

    private static final int                  DEFAULT_PARALLEL_RULES_BUILD_THRESHOLD = 10;

    private final Map<String, DialectConfiguration> dialectConfigurations = new HashMap<>();

    private DefaultDialectOption              defaultDialect = DefaultDialectOption.get("java");

    private ParallelRulesBuildThresholdOption parallelRulesBuildThreshold = ParallelRulesBuildThresholdOption.get(DEFAULT_PARALLEL_RULES_BUILD_THRESHOLD);

    private ClassLoader                       classLoader;

    private ChainedProperties                 chainedProperties;

    private Map<String, AccumulateFunction>   accumulateFunctions;

    private EvaluatorRegistry                 evaluatorRegistry;

    private File                              dumpDirectory;

    private boolean                           processStringEscapes                  = true;
    private boolean                           trimCellsInDTable                     = true;
    private boolean                           groupDRLsInKieBasesByFolder           = false;

    private boolean                           externaliseCanonicalModelLambda       = true;
    private boolean                           parallelLambdaExternalization         = true;

    private AlphaNetworkCompilerOption        alphaNetworkCompilerOption            = AlphaNetworkCompilerOption.DISABLED;

    private static final PropertySpecificOption DEFAULT_PROP_SPEC_OPT = PropertySpecificOption.ALWAYS;
    private PropertySpecificOption            propertySpecificOption  = DEFAULT_PROP_SPEC_OPT;

    private String                            defaultPackageName;

    private Map<String, ResultSeverity>       severityMap;

    private LanguageLevelOption               languageLevel           = DrlParser.DEFAULT_LANGUAGE_LEVEL;

    private CompilationCache                  compilationCache        = null;

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBuilderConfigurationImpl.class);

     /**
     * Constructor that sets the parent class loader for the package being built/compiled
     */
    public KnowledgeBuilderConfigurationImpl(ClassLoader classLoader) {
        init(null, classLoader);
    }

    /**
     * Programmatic properties file, added with lease precedence
     */
    public KnowledgeBuilderConfigurationImpl(Properties properties) {
        init(properties, null);
    }

    /**
     * Programmatic properties file, added with lease precedence
     */
    public KnowledgeBuilderConfigurationImpl(Properties properties, ClassLoader classLoader) {
        init(properties, classLoader);
    }

    public KnowledgeBuilderConfigurationImpl() {
        init(null, null);
    }

    private void init(Properties properties, ClassLoader classLoader) {
        this.classLoader = ProjectClassLoader.getClassLoader(classLoader, getClass());
        init(properties);
    }

    private void init(Properties properties) {

        this.chainedProperties = ChainedProperties.getChainedProperties( getClassLoader() );

        if (chainedProperties.getProperty("drools.dialect.java", null) == null) {
            // if it couldn't find a conf for java dialect using the project class loader
            // it means it could not load the conf file at all (very likely it is running in
            // an osgi environement) so try with the class loader of this class
            this.chainedProperties = ChainedProperties.getChainedProperties( getClass().getClassLoader() );

            if (this.classLoader instanceof ProjectClassLoader ) {
                ((ProjectClassLoader) classLoader).setDroolsClassLoader(getClass().getClassLoader());
            }
        }

        if (properties != null) {
            this.chainedProperties.addProperties(properties);
        }

        setProperty( TrimCellsInDTableOption.PROPERTY_NAME,
                    this.chainedProperties.getProperty(TrimCellsInDTableOption.PROPERTY_NAME,
                                                       "true"));

        setProperty( GroupDRLsInKieBasesByFolderOption.PROPERTY_NAME,
                    this.chainedProperties.getProperty(GroupDRLsInKieBasesByFolderOption.PROPERTY_NAME,
                                                       "false"));

        setProperty(PropertySpecificOption.PROPERTY_NAME,
                    this.chainedProperties.getProperty(PropertySpecificOption.PROPERTY_NAME,
                                                       DEFAULT_PROP_SPEC_OPT.toString()));

        setProperty(LanguageLevelOption.PROPERTY_NAME,
                    this.chainedProperties.getProperty(LanguageLevelOption.PROPERTY_NAME,
                                                       DrlParser.DEFAULT_LANGUAGE_LEVEL.toString()));

        setProperty(ParallelRulesBuildThresholdOption.PROPERTY_NAME,
        			this.chainedProperties.getProperty(ParallelRulesBuildThresholdOption.PROPERTY_NAME, 
        												String.valueOf(DEFAULT_PARALLEL_RULES_BUILD_THRESHOLD)));
        
        buildDialectConfigurationMap();

        this.accumulateFunctions = AccumulateUtil.buildAccumulateFunctionsMap(chainedProperties, getFunctionFactoryClassLoader() );

        buildEvaluatorRegistry();

        buildDumpDirectory();

        buildSeverityMap();

        setProperty(ProcessStringEscapesOption.PROPERTY_NAME,
                    this.chainedProperties.getProperty(ProcessStringEscapesOption.PROPERTY_NAME,
                                                       "true"));

        setProperty(DefaultPackageNameOption.PROPERTY_NAME,
                    this.chainedProperties.getProperty(DefaultPackageNameOption.PROPERTY_NAME,
                                                       DEFAULT_PACKAGE));

        setProperty(ExternaliseCanonicalModelLambdaOption.PROPERTY_NAME,
                    this.chainedProperties.getProperty(ExternaliseCanonicalModelLambdaOption.PROPERTY_NAME,"true"));

        setProperty(ParallelLambdaExternalizationOption.PROPERTY_NAME,
                    this.chainedProperties.getProperty(ParallelLambdaExternalizationOption.PROPERTY_NAME,"true"));
    }

    protected ClassLoader getFunctionFactoryClassLoader() {
        return getClassLoader();
    }

    private void buildSeverityMap() {
        this.severityMap = new HashMap<String, ResultSeverity>();
        Map<String, String> temp = new HashMap<String, String>();
        this.chainedProperties.mapStartsWith(temp,
                KBuilderSeverityOption.PROPERTY_NAME,
                true);

        int index = KBuilderSeverityOption.PROPERTY_NAME.length();
        for (Map.Entry<String, String> entry : temp.entrySet()) {
            String identifier = entry.getKey().trim().substring(index);
            this.severityMap.put(identifier,
                    KBuilderSeverityOption.get(identifier, entry.getValue()).getSeverity());
        }
    }

    public void setProperty(String name,
            String value) {
        name = name.trim();
        if (StringUtils.isEmpty(name)) {
            return;
        }

        if (name.equals(DefaultDialectOption.PROPERTY_NAME)) {
            setDefaultDialect(value);
        } else if (name.startsWith(AccumulateFunctionOption.PROPERTY_NAME)) {
            addAccumulateFunction(name.substring(AccumulateFunctionOption.PROPERTY_NAME.length()),
                    value);
        } else if (name.startsWith(EvaluatorOption.PROPERTY_NAME)) {
            this.evaluatorRegistry.addEvaluatorDefinition(value);
        } else if (name.equals(DumpDirOption.PROPERTY_NAME)) {
            buildDumpDirectory(value);
        } else if (name.equals(DefaultPackageNameOption.PROPERTY_NAME)) {
            setDefaultPackageName(value);
        } else if (name.equals(ProcessStringEscapesOption.PROPERTY_NAME)) {
            setProcessStringEscapes(Boolean.parseBoolean(value));
        } else if (name.equals(TrimCellsInDTableOption.PROPERTY_NAME)) {
            setTrimCellsInDTable(Boolean.parseBoolean(value));
        } else if (name.equals(GroupDRLsInKieBasesByFolderOption.PROPERTY_NAME)) {
            setGroupDRLsInKieBasesByFolder(Boolean.parseBoolean(value));
        } else if (name.startsWith(KBuilderSeverityOption.PROPERTY_NAME)) {
            String key = name.substring(name.lastIndexOf('.') + 1);
            this.severityMap.put(key, KBuilderSeverityOption.get(key, value).getSeverity());
        } else if (name.equals(PropertySpecificOption.PROPERTY_NAME)) {
            try {
                setPropertySpecificOption(PropertySpecificOption.valueOf(value.toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid value " + value + " for option " + PropertySpecificOption.PROPERTY_NAME);
            }
        } else if (name.equals(LanguageLevelOption.PROPERTY_NAME)) {
            try {
                setLanguageLevel(LanguageLevelOption.valueOf(value.toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid value " + value + " for option " + LanguageLevelOption.PROPERTY_NAME);
            }
        } else if (name.equals(ParallelRulesBuildThresholdOption.PROPERTY_NAME)) {
        	setParallelRulesBuildThreshold(Integer.valueOf(value));
        }  else if (name.equals(ExternaliseCanonicalModelLambdaOption.PROPERTY_NAME)) {
            setExternaliseCanonicalModelLambda(Boolean.valueOf(value));
        } else if (name.equals(ParallelLambdaExternalizationOption.PROPERTY_NAME)) {
            setParallelLambdaExternalization(Boolean.valueOf(value));
        } else if (name.equals(AlphaNetworkCompilerOption.PROPERTY_NAME)) {
            try {
                setAlphaNetworkCompilerOption(AlphaNetworkCompilerOption.determineAlphaNetworkCompilerMode(value.toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid value " + value + " for option " + AlphaNetworkCompilerOption.PROPERTY_NAME);
            }
        } else {
            // if the property from the kmodule was not intercepted above, just add it to the chained properties.
            Properties additionalProperty = new Properties();
            additionalProperty.setProperty(name, value);
            chainedProperties.addProperties(additionalProperty);
        }
    }

    public String getProperty(String name) {
        name = name.trim();
        if (StringUtils.isEmpty(name)) {
            return null;
        }

        if (name.equals(DefaultDialectOption.PROPERTY_NAME)) {
            return getDefaultDialect();
        } else if (name.equals(DefaultPackageNameOption.PROPERTY_NAME)) {
            return getDefaultPackageName();
        } else if (name.startsWith(AccumulateFunctionOption.PROPERTY_NAME)) {
            int index = AccumulateFunctionOption.PROPERTY_NAME.length();
            AccumulateFunction function = this.accumulateFunctions.get(name.substring(index));
            return function != null ? function.getClass().getName() : null;
        } else if (name.startsWith(EvaluatorOption.PROPERTY_NAME)) {
            String key = name.substring(name.lastIndexOf('.') + 1);
            EvaluatorDefinition evalDef = this.evaluatorRegistry.getEvaluatorDefinition(key);
            return evalDef != null ? evalDef.getClass().getName() : null;
        } else if (name.equals(DumpDirOption.PROPERTY_NAME)) {
            return this.dumpDirectory != null ? this.dumpDirectory.toString() : null;
        } else if (name.equals(ProcessStringEscapesOption.PROPERTY_NAME)) {
            return String.valueOf(isProcessStringEscapes());
        } else if (name.equals(TrimCellsInDTableOption.PROPERTY_NAME)) {
            return String.valueOf(isTrimCellsInDTable());
        } else if (name.equals(GroupDRLsInKieBasesByFolderOption.PROPERTY_NAME)) {
            return String.valueOf(isGroupDRLsInKieBasesByFolder());
        } else if (name.startsWith(KBuilderSeverityOption.PROPERTY_NAME)) {
            String key = name.substring(name.lastIndexOf('.') + 1);
            ResultSeverity severity = this.severityMap.get(key);
            return severity.toString();
        } else if (name.equals(LanguageLevelOption.PROPERTY_NAME)) {
            return "" + getLanguageLevel();
        } else if (name.equals(ParallelRulesBuildThresholdOption.PROPERTY_NAME)) {
        	return String.valueOf(getParallelRulesBuildThreshold());
        } else if (name.equals(ExternaliseCanonicalModelLambdaOption.PROPERTY_NAME)) {
        	return String.valueOf(isExternaliseCanonicalModelLambda());
        } else if (name.equals(ParallelLambdaExternalizationOption.PROPERTY_NAME)) {
        	return String.valueOf(isParallelLambdaExternalization());
        }
        return null;
    }

    public ChainedProperties getChainedProperties() {
        return this.chainedProperties;
    }

    private void buildDialectConfigurationMap() {
        DialectConfiguration mvel = ConstraintBuilder.get().createMVELDialectConfiguration(this);
        if (mvel != null) {
            mvel.init( this );
            dialectConfigurations.put( "mvel", mvel );
        }

        DialectConfiguration java = ConstraintBuilder.get().createJavaDialectConfiguration(this);
        java.init(this);
        dialectConfigurations.put("java", java);

        Map<String, String> dialectProperties = new HashMap<>();
        this.chainedProperties.mapStartsWith(dialectProperties, "drools.dialect", false);
        setDefaultDialect(dialectProperties.get(DefaultDialectOption.PROPERTY_NAME));
    }

    public void addDialect(String dialectName, DialectConfiguration dialectConf) {
        dialectConfigurations.put(dialectName, dialectConf);
    }

    public DialectCompiletimeRegistry buildDialectRegistry(ClassLoader rootClassLoader,
            KnowledgeBuilderConfigurationImpl pkgConf,
            PackageRegistry pkgRegistry,
            InternalKnowledgePackage pkg) {
        DialectCompiletimeRegistry registry = new DialectCompiletimeRegistry();
        for (DialectConfiguration conf : this.dialectConfigurations.values()) {
            Dialect dialect = conf.newDialect(rootClassLoader, pkgConf, pkgRegistry, pkg);
            registry.addDialect(dialect.getId(), dialect);
        }
        return registry;
    }

    public String getDefaultDialect() {
        return this.defaultDialect.getName();
    }

    public void setDefaultDialect(String defaultDialect) {
        this.defaultDialect = DefaultDialectOption.get(defaultDialect);
    }

    public DialectConfiguration getDialectConfiguration(String name) {
        return this.dialectConfigurations.get(name);
    }

    public void setDialectConfiguration(String name, DialectConfiguration configuration) {
        this.dialectConfigurations.put(name, configuration);
    }

    public ClassLoader getClassLoader() {
        return this.classLoader;
    }

    public void addAccumulateFunction(String identifier, String className) {
        this.accumulateFunctions.put(identifier,
                                     AccumulateUtil.loadAccumulateFunction(getClassLoader(), identifier,
                        className));
    }

    public void addAccumulateFunction(String identifier,
            Class<? extends AccumulateFunction> clazz) {
        try {
            this.accumulateFunctions.put(identifier,
                    clazz.newInstance());
        } catch (InstantiationException e) {
            throw new RuntimeException("Error loading accumulate function for identifier " + identifier + ". Instantiation failed for class " + clazz.getName(),
                    e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Error loading accumulate function for identifier " + identifier + ". Illegal access to class " + clazz.getName(),
                    e);
        }
    }

    public AccumulateFunction getAccumulateFunction(String identifier) {
        return this.accumulateFunctions.get(identifier);
    }

    // Used by droolsjbpm-tools
    public Collection<String> getAccumulateFunctionNames() {
        return this.accumulateFunctions.keySet();
    }

    private void buildEvaluatorRegistry() {
        this.evaluatorRegistry = new EvaluatorRegistry( getFunctionFactoryClassLoader() );
        Map<String, String> temp = new HashMap<>();
        this.chainedProperties.mapStartsWith(temp,
                EvaluatorOption.PROPERTY_NAME,
                true);
        for (Entry<String, String> e : temp.entrySet()) {
            String key = e.getKey();
            // filtering out unused properties, to avoid failing when an old packagebuilder.conf
            // file is present on the classpath that did define these (for example when parsing
            // a rule in Eclipse plugin using old runtime)
            if ("drools.evaluator.equality".equals(key)
                    || ("drools.evaluator.comparable".equals(key))) {
                continue;
            }
            this.evaluatorRegistry.addEvaluatorDefinition(e.getValue());
        }
    }

    /**
     * Returns the evaluator registry for this package builder configuration
     * @return
     */
    public EvaluatorRegistry getEvaluatorRegistry() {
        return this.evaluatorRegistry;
    }

    /**
     * Adds an evaluator definition class to the registry using the
     * evaluator class name. The class will be loaded and the corresponting
     * evaluator ID will be added to the registry. In case there exists
     * an implementation for that ID already, the new implementation will
     * replace the previous one.
     *
     * @param className the name of the class for the implementation definition.
     *                  The class must implement the EvaluatorDefinition interface.
     */
    public void addEvaluatorDefinition(String className) {
        this.evaluatorRegistry.addEvaluatorDefinition(className);
    }

    /**
     * Adds an evaluator definition class to the registry. In case there exists
     * an implementation for that evaluator ID already, the new implementation will
     * replace the previous one.
     *
     * @param def the evaluator definition to be added.
     */
    public void addEvaluatorDefinition(EvaluatorDefinition def) {
        this.evaluatorRegistry.addEvaluatorDefinition(def);
    }

    private void buildDumpDirectory() {
        String dumpStr = this.chainedProperties.getProperty(DumpDirOption.PROPERTY_NAME,
                null);
        buildDumpDirectory(dumpStr);
    }

    private void buildDumpDirectory(String dumpStr) {
        if (dumpStr != null) {
            setDumpDir(new File(dumpStr));
        }
    }

    public File getDumpDir() {
        return this.dumpDirectory;
    }

    public void setDumpDir(File dumpDir) {
        if (!dumpDir.isDirectory() || !dumpDir.canWrite() || !dumpDir.canRead()) {
            throw new RuntimeException("Drools dump directory is not accessible: " + dumpDir.toString());
        }
        this.dumpDirectory = dumpDir;
    }

    public boolean isProcessStringEscapes() {
        return processStringEscapes;
    }

    public void setProcessStringEscapes(boolean processStringEscapes) {
        this.processStringEscapes = processStringEscapes;
    }

    public boolean isTrimCellsInDTable() {
        return trimCellsInDTable;
    }

    public void setTrimCellsInDTable( boolean trimCellsInDTable ) {
        this.trimCellsInDTable = trimCellsInDTable;
    }

    public boolean isGroupDRLsInKieBasesByFolder() {
        return groupDRLsInKieBasesByFolder;
    }

    public void setGroupDRLsInKieBasesByFolder( boolean groupDRLsInKieBasesByFolder ) {
        this.groupDRLsInKieBasesByFolder = groupDRLsInKieBasesByFolder;
    }

    public int getParallelRulesBuildThreshold() {
    	return parallelRulesBuildThreshold.getParallelRulesBuildThreshold();
    }
    
    public void setParallelRulesBuildThreshold(int parallelRulesBuildThreshold) {
    	this.parallelRulesBuildThreshold = ParallelRulesBuildThresholdOption.get(parallelRulesBuildThreshold);
    }

    public String getDefaultPackageName() {
        return defaultPackageName;
    }

    public void setDefaultPackageName(String defaultPackageName) {
        this.defaultPackageName = defaultPackageName;
    }

    public LanguageLevelOption getLanguageLevel() {
        return languageLevel;
    }

    public void setLanguageLevel(LanguageLevelOption languageLevel) {
        this.languageLevel = languageLevel;
    }

    public PropertySpecificOption getPropertySpecificOption() {
        return propertySpecificOption;
    }

    public void setPropertySpecificOption(PropertySpecificOption propertySpecificOption) {
        this.propertySpecificOption = propertySpecificOption;
    }

    public boolean isExternaliseCanonicalModelLambda() {
        return externaliseCanonicalModelLambda;
    }

    public void setExternaliseCanonicalModelLambda(boolean externaliseCanonicalModelLambda) {
        this.externaliseCanonicalModelLambda = externaliseCanonicalModelLambda;
    }

    public boolean isParallelLambdaExternalization() {
        return parallelLambdaExternalization;
    }

    public void setParallelLambdaExternalization(boolean parallelLambdaExternalization) {
        this.parallelLambdaExternalization = parallelLambdaExternalization;
    }

    public AlphaNetworkCompilerOption getAlphaNetworkCompilerOption() {
        return alphaNetworkCompilerOption;
    }

    public void setAlphaNetworkCompilerOption(AlphaNetworkCompilerOption alphaNetworkCompilerOption) {
        this.alphaNetworkCompilerOption = alphaNetworkCompilerOption;
    }

    @SuppressWarnings("unchecked")
    public <T extends SingleValueKnowledgeBuilderOption> T getOption(Class<T> option) {
        if (DefaultDialectOption.class.equals(option)) {
            return (T) this.defaultDialect;
        } else if (DumpDirOption.class.equals(option)) {
            return (T) DumpDirOption.get(this.dumpDirectory);
        } else if (ProcessStringEscapesOption.class.equals(option)) {
            return (T) (this.processStringEscapes ? ProcessStringEscapesOption.YES : ProcessStringEscapesOption.NO);
        } else if (DefaultPackageNameOption.class.equals(option)) {
            return (T) DefaultPackageNameOption.get(this.defaultPackageName);
        } else if (TrimCellsInDTableOption.class.equals(option)) {
            return (T) (this.trimCellsInDTable ? TrimCellsInDTableOption.ENABLED : TrimCellsInDTableOption.DISABLED);
        } else if (GroupDRLsInKieBasesByFolderOption.class.equals(option)) {
            return (T) (this.groupDRLsInKieBasesByFolder ? GroupDRLsInKieBasesByFolderOption.ENABLED : GroupDRLsInKieBasesByFolderOption.DISABLED);
        } else if (PropertySpecificOption.class.equals(option)) {
            return (T) propertySpecificOption;
        } else if (LanguageLevelOption.class.equals(option)) {
            return (T) languageLevel;
        } else if (ExternaliseCanonicalModelLambdaOption.class.equals(option)) {
            return (T) (externaliseCanonicalModelLambda ? ExternaliseCanonicalModelLambdaOption.ENABLED : ExternaliseCanonicalModelLambdaOption.DISABLED);
        } else if (ParallelLambdaExternalizationOption.class.equals(option)) {
            return (T) (parallelLambdaExternalization ? ParallelLambdaExternalizationOption.ENABLED : ParallelLambdaExternalizationOption.DISABLED);
        } else if (AlphaNetworkCompilerOption.class.equals(option)) {
            return (T) alphaNetworkCompilerOption;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public <T extends MultiValueKnowledgeBuilderOption> T getOption(Class<T> option,
            String key) {
        if (AccumulateFunctionOption.class.equals(option)) {
            return (T) AccumulateFunctionOption.get(key,
                    this.accumulateFunctions.get(key));
        } else if (EvaluatorOption.class.equals(option)) {
            return (T) EvaluatorOption.get(key,
                    this.evaluatorRegistry.getEvaluatorDefinition(key));
        } else if (KBuilderSeverityOption.class.equals(option)) {

            return (T) KBuilderSeverityOption.get(key,
                    this.severityMap.get(key));
        }
        return null;
    }

    public <T extends MultiValueKnowledgeBuilderOption> Set<String> getOptionKeys(
            Class<T> option) {
        if (AccumulateFunctionOption.class.equals(option)) {
            return this.accumulateFunctions.keySet();
        } else if (EvaluatorOption.class.equals(option)) {
            return this.evaluatorRegistry.keySet();
        } else if (KBuilderSeverityOption.class.equals(option)) {
            return this.severityMap.keySet();
        }
        return null;
    }

    public <T extends KnowledgeBuilderOption> void setOption(T option) {
        if (option instanceof DefaultDialectOption) {
            this.defaultDialect = (DefaultDialectOption) option;
        } else if (option instanceof AccumulateFunctionOption) {
            this.accumulateFunctions.put(((AccumulateFunctionOption) option).getName(),
                    ((AccumulateFunctionOption) option).getFunction());
        } else if (option instanceof DumpDirOption) {
            this.dumpDirectory = ((DumpDirOption) option).getDirectory();
        } else if (option instanceof EvaluatorOption) {
            this.evaluatorRegistry.addEvaluatorDefinition((EvaluatorDefinition) ((EvaluatorOption) option).getEvaluatorDefinition());
        } else if (option instanceof ProcessStringEscapesOption) {
            this.processStringEscapes = ((ProcessStringEscapesOption) option).isProcessStringEscapes();
        } else if (option instanceof DefaultPackageNameOption) {
            setDefaultPackageName(((DefaultPackageNameOption) option).getPackageName());
        } else if (option instanceof TrimCellsInDTableOption) {
            setTrimCellsInDTable(((TrimCellsInDTableOption) option).isTrimCellsInDTable());
        } else if (option instanceof GroupDRLsInKieBasesByFolderOption) {
            setGroupDRLsInKieBasesByFolder(((GroupDRLsInKieBasesByFolderOption) option).isGroupDRLsInKieBasesByFolder());
        } else if (option instanceof KBuilderSeverityOption) {
            this.severityMap.put(((KBuilderSeverityOption) option).getName(), ((KBuilderSeverityOption) option).getSeverity());
        } else if (option instanceof PropertySpecificOption) {
            propertySpecificOption = (PropertySpecificOption) option;
        } else if (option instanceof LanguageLevelOption) {
            this.languageLevel = ((LanguageLevelOption) option);
        } else if (option instanceof ExternaliseCanonicalModelLambdaOption) {
            this.externaliseCanonicalModelLambda = ((ExternaliseCanonicalModelLambdaOption) option).isCanonicalModelLambdaExternalized();
        } else if (option instanceof ParallelLambdaExternalizationOption) {
            this.parallelLambdaExternalization = ((ParallelLambdaExternalizationOption) option).isLambdaExternalizationParallel();
        } else if (option instanceof AlphaNetworkCompilerOption) {
            this.alphaNetworkCompilerOption = ((AlphaNetworkCompilerOption) option);
        }
    }

    public CompilationCache getCompilationCache() {
        return compilationCache;
    }

    public void setCompilationCache(CompilationCache cache) {
        this.compilationCache = cache;
    }

    public boolean isPreCompiled() {
        return this.compilationCache != null;
    }

}
