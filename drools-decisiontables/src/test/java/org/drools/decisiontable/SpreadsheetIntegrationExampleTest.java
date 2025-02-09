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

package org.drools.decisiontable;

import java.util.ArrayList;
import java.util.List;

import org.acme.insurance.launcher.PricingRuleLauncher;
import org.junit.Test;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.model.KieModuleModel;
import org.kie.api.io.Resource;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.internal.builder.DecisionTableConfiguration;
import org.kie.internal.builder.DecisionTableInputType;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.utils.KieHelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SpreadsheetIntegrationExampleTest {

    @Test
    public void testExecuteUsingKieAPI() throws Exception {
        // get the resource
        Resource dt = ResourceFactory.newClassPathResource("/data/IntegrationExampleTest.drl.xls", getClass());
        
        // create the builder
        KieSession ksession = getKieSession( dt );

        ksession.insert( new Cheese( "stilton",
                                    42 ) );
        ksession.insert( new Person( "michael",
                                    "stilton",
                                    42 ) );
        final List<String> list = new ArrayList<String>();
        ksession.setGlobal( "list",
                           list );
        ksession.fireAllRules();
        assertEquals( 1,
                      list.size() );
        assertEquals( "Old man stilton",
                      list.get( 0 ) );
    }

    private KieSession getKieSession(Resource dt) {
        KieServices ks = KieServices.Factory.get();

        KieFileSystem kfs = ks.newKieFileSystem().write( dt );
        KieBuilder kb = ks.newKieBuilder( kfs ).buildAll();
        assertTrue( kb.getResults().getMessages().isEmpty() );

        // get the session
        KieSession ksession = ks.newKieContainer(ks.getRepository().getDefaultReleaseId()).newKieSession();
        return ksession;
    }

    @Test
    public void testExecuteJBRULES3005() throws Exception {
        Resource dt = ResourceFactory.newClassPathResource("/data/IntegrationExampleTest.drl.xls", getClass() );
        KieSession ksession = getKieSession( dt );

        //ASSERT AND FIRE
        ksession.insert( new Cheese( "stilton",
                                    42 ) );
        ksession.insert( new Person( "michael",
                                    "stilton",
                                    42 ) );
        final List<String> list = new ArrayList<String>();
        ksession.setGlobal( "list",
                           list );
        ksession.fireAllRules();
        assertEquals( 1,
                      list.size() );
        assertEquals( "Old man stilton",
                      list.get( 0 ) );
    }
    
    @Test 
    public void testNamedWorksheet() throws Exception {
        DecisionTableConfiguration dtconf = KnowledgeBuilderFactory.newDecisionTableConfiguration();
        dtconf.setInputType( DecisionTableInputType.XLS );
        dtconf.setWorksheetName( "Tables_2" );

        Resource dt = ResourceFactory.newClassPathResource("/data/IntegrationExampleTest.drl.xls", getClass() )
                                     .setConfiguration( dtconf );
        KieSession ksession = getKieSession( dt );

        //ASSERT AND FIRE
        ksession.insert( new Cheese( "cheddar",
                                    42 ) );
        ksession.insert( new Person( "michael",
                                    "stilton",
                                    25 ) );
        final List<String> list = new ArrayList<String>();
        ksession.setGlobal( "list",
                           list );
        ksession.fireAllRules();
        assertEquals( 1,
                      list.size() );
        assertEquals( "Young man cheddar",
                      list.get( 0 ) );
    }

    /**
     * A smoke test mainly.
     */
    @Test
    public void testInsuranceExample() throws Exception {
        PricingRuleLauncher launcher = new PricingRuleLauncher();
        assertEquals( 120,
                      launcher.executeExample() );
    }

    @Test
    public void testBooleanField() throws Exception {
        Resource dt = ResourceFactory.newClassPathResource("/data/ShopRules.drl.xls", getClass());
        KieSession ksession = getKieSession( dt );

        Person p = new Person( "michael", "stilton", 42 );
        ksession.insert( p );

        ksession.fireAllRules();

        assertTrue( p.getCanBuyAlcohol() );
    }

    @Test
    public void testHeadingWhitespace() throws Exception {
        System.setProperty( "drools.trimCellsInDTable", "false" );
        try {
            Resource dt = ResourceFactory.newClassPathResource("/data/HeadingWhitespace.drl.xls", getClass() );
            KieSession ksession = getKieSession( dt );

            Person p = new Person( " me" );
            ksession.insert( p );

            ksession.fireAllRules();

            assertTrue( p.getCanBuyAlcohol() );
        } finally {
            System.clearProperty( "drools.trimCellsInDTable" );
        }
    }

    @Test
    public void testPackageName() throws Exception {
        // DROOLS-4967
        KieServices ks = KieServices.get();

        KieModuleModel kmodel = ks.newKieModuleModel();
        kmodel.newKieBaseModel( "kbase1" )
                .addPackage( "org.drools.simple.candrink" )
                .setDefault( true );

        KieBase kbase = new KieHelper().setKieModuleModel( kmodel )
                .addResource( ks.getResources().newClassPathResource("/data/CanNotDrink2.drl.xls", getClass() ), ResourceType.DTABLE )
                .build();

        assertEquals( 2, kbase.getKiePackage( "org.drools.simple.candrink" ).getRules().size() );
    }
}
