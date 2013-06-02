/*
 * The MIT License
 * 
 * Copyright (c) 2013 IKEDA Yasuyuki
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package jp.ikedam.jenkins.plugins.groovy_label_assignment;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jenkins.model.Jenkins;

import hudson.matrix.AxisList;
import hudson.matrix.MatrixRun;
import hudson.matrix.LabelAxis;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.matrix.TextAxis;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleBuild;
import hudson.model.ParameterValue;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Slave;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.model.labels.LabelExpression;
import hudson.slaves.DumbSlave;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;

import static org.junit.Assert.*;

/**
 * Tests for GroovyLabelAssignmentProperty, working with Jenkins.
 */
public class GroovyLabelAssignmentPropertyJenkinsTest
{
    private static final int BUILD_REPEAT = 3;
    private static final long BUILD_TIMEOUT = 5 * 1000;
    @Rule
    public GroovyLabelAssingmentJenkinsRule j = new GroovyLabelAssingmentJenkinsRule();
    
    @SuppressWarnings("deprecation")
    private <P extends AbstractProject<P,R>,R extends AbstractBuild<P,R>> R scheduleBuildWithParameters(
            P project,
            ParameterValue ... parameters) throws InterruptedException, ExecutionException, TimeoutException
    {
        return project.scheduleBuild2(
                project.getQuietPeriod(),
                new Cause.LegacyCodeCause(),
                new ParametersAction(parameters)
        ).get(BUILD_TIMEOUT + project.getQuietPeriod() * 1000, TimeUnit.MILLISECONDS);
    }
    
    private void assertBuiltOn(Slave slave, AbstractBuild<?,?> build)
    {
        assertEquals(slave.getNodeName(), build.getBuiltOn().getNodeName());
    }
    
    protected DumbSlave slave1;
    protected DumbSlave slave2;
    protected DumbSlave slave3;
    
    @Before
    public void setupSlaves() throws Exception
    {
        slave1 = j.createOnlineSlave("test1 common1");
        slave2 = j.createOnlineSlave("test2 common2");
        slave3 = j.createOnlineSlave("test3 common1 common2");
    }
    
    @Test
    public void testFreeStyleProjectWithoutGroovyLabelAssignmentProperty() throws Exception
    {
        // Test the behavior without GroovyLabelAssignmentProperty.
        // This is a regression test and a test for test methods.
        
        String paramName = "PARAM1";
        String defaultParamValue = "VALUE";
        
        FreeStyleProject project = j.createFreeStyleProject();
        ParametersDefinitionProperty paramProp = new ParametersDefinitionProperty(
                new StringParameterDefinition(paramName, defaultParamValue)
        );
        project.addProperty(paramProp);
        CaptureEnvironmentBuilder ceb = new CaptureEnvironmentBuilder();
        project.getBuildersList().add(ceb);
        
        // Build is done with specified node.
        {
            project.setAssignedLabel(LabelExpression.parseExpression("test2"));
            
            for(int i = 0; i < BUILD_REPEAT; ++i)
            {
                String paramValue = "AnotherValue";
                
                FreeStyleBuild build = scheduleBuildWithParameters(
                        project,
                        new StringParameterValue(paramName, paramValue)
                );
                assertBuiltOn(slave2, build);
                assertEquals(paramValue, ceb.getEnvVars().get(paramName));
            }
        }
        
        // Build is done with specified label combination.
        {
            project.setAssignedLabel(LabelExpression.parseExpression("common1&&common2"));
            
            for(int i = 0; i < BUILD_REPEAT; ++i)
            {
                String paramValue = "AnotherValue";
                
                FreeStyleBuild build = scheduleBuildWithParameters(
                        project,
                        new StringParameterValue(paramName, paramValue)
                );
                assertBuiltOn(slave3, build);
                assertEquals(paramValue, ceb.getEnvVars().get(paramName));
            }
        }
        
        // Build is not done with non-exist label
        {
            project.setAssignedLabel(LabelExpression.parseExpression("nosuchnode"));
            
            String paramValue = "AnotherValue";
            try
            {
                scheduleBuildWithParameters(
                        project,
                        new StringParameterValue(paramName, paramValue)
                );
                fail("Build must be timed out!");
            }
            catch(TimeoutException e)
            {
                assertTrue(Jenkins.getInstance().getQueue().cancel(project));
            }
        }
    }
    
    @Test
    public void testMatrixProjectWithoutGroovyLabelAssignmentProperty() throws Exception
    {
        // Test the behavior without GroovyLabelAssignmentProperty.
        // This is a regression test and a test for test methods.
        
        String paramName = "PARAM1";
        String defaultParamValue = "VALUE";
        
        MatrixProject project = j.createMatrixProject();
        AxisList axes = new AxisList();
        axes.add(new TextAxis("axisParam", "axis1","axis2"));
        axes.add(new LabelAxis("axisLabel", Arrays.asList("test1", "test2")));
        project.setAxes(axes);
        project.setCombinationFilter("(axisParam==\"axis1\" && axisLabel==\"test1\") || (axisParam==\"axis2\" && axisLabel==\"test2\")");
        
        ParametersDefinitionProperty paramProp = new ParametersDefinitionProperty(
                new StringParameterDefinition(paramName, defaultParamValue)
        );
        project.addProperty(paramProp);
        
        // Build is done with specified node.
        {
            for(int i = 0; i < BUILD_REPEAT; ++i)
            {
                String paramValue = "AnotherValue";
                
                MatrixBuild build = scheduleBuildWithParameters(
                        project,
                        new StringParameterValue(paramName, paramValue)
                );
                
                for(MatrixRun child: build.getRuns())
                {
                    String axisParam = child.getProject().getCombination().get("axisParam");
                    if("axis1".equals(axisParam))
                    {
                        assertBuiltOn(slave1, child);
                    }
                    else if("axis2".equals(axisParam))
                    {
                        assertBuiltOn(slave2, child);
                    }
                    else
                    {
                        fail(String.format("unknown combination: %s", child.getProject().getCombination().toString()));
                    }
                }
            }
        }
    }
}
