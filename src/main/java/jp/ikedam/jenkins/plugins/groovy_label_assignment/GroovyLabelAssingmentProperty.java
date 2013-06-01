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

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import antlr.ANTLRException;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.matrix.Axis;
import hudson.matrix.AxisList;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.EnvironmentContributingAction;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.Label;
import hudson.model.labels.LabelAssignmentAction;
import hudson.model.labels.LabelExpression;

/**
 * JobProperty that holds configuration for GroovyLabelAssingment.
 */
public class GroovyLabelAssingmentProperty extends JobProperty<AbstractProject<?, ?>>
{
    /**
     * Property name used for job configuration page.
     */
    static public final String PROPERTYNAME = "groovy_label_assignment";
    
    static private final Logger LOGGER = Logger.getLogger(GroovyLabelAssingmentProperty.class.getName());
    
    private String groovyScript;
    
    /**
     * @return the Groovy Script
     */
    public String getGroovyScript()
    {
        return groovyScript;
    }
    
    /**
     * Constructor from the form input.
     * 
     * @param groovyScript
     */
    @DataBoundConstructor
    public GroovyLabelAssingmentProperty(String groovyScript)
    {
        this.groovyScript = groovyScript;
    }
    
    /**
     * Decide label of nodes where the job will run.
     * 
     * @param project The job. This may not be the owner job if it is MatrixConfiguration.
     * @param actions actions of job. and add LabelAssignmentAction.
     * 
     * @return returns properly evaluate script and add label.
     */
    public boolean assignLabel(AbstractProject<?, ?> project, List<Action> actions)
    {
        if(StringUtils.isBlank(getGroovyScript()))
        {
            // groovyScript is not configured collectlt.
            LOGGER.severe(String.format("%s: GroovyScript is not configured.", project.getName()));
            return false;
        }
        
        // Run groovy script.
        Object out;
        try
        {
            Binding binding = createBinding(project, actions);
            out = new GroovyShell(binding).evaluate(getGroovyScript());
        }catch(Exception e)
        {
            LOGGER.log(Level.SEVERE, String.format("%s: Failed to run script", project.getName()), e);
            return false;
        }
        
        String labelString = (out != null)?out.toString():null;
        if(StringUtils.isBlank(labelString))
        {
            LOGGER.info(String.format("%s: label is not modified.", project.getName()));
            return true;
        }
        
        Label label;
        try
        {
            label = LabelExpression.parseExpression(labelString);
        }
        catch(ANTLRException e)
        {
            LOGGER.log(Level.SEVERE, String.format("%s: Invalid label string: %s", project.getName(), labelString), e);
            return false;
        }
        
        LabelAssignmentAction labelAction = new GroovyLabelAssingmentAction(label);
        actions.add(0, labelAction);
        
        LOGGER.info(String.format("%s: label is modified to %s", project.getName(), labelString));
        
        return true;
    }
    
    /**
     * Create variables used in a groovy script.
     * 
     * @param project
     * @param actions
     * @return
     */
    protected Binding createBinding(AbstractProject<?, ?> project, List<Action> actions)
    {
        EnvVars env = new EnvVars();
        
        // add environments
        // ParametersAction is also processed here.
        // some actions may fail for build is null
        for (EnvironmentContributingAction a : Util.filter(actions,EnvironmentContributingAction.class))
        {
            try
            {
                a.buildEnvVars(null,env);
            }
            catch(NullPointerException e)
            {
                // nothing to do.
                LOGGER.log(Level.FINE, String.format("%s: NPE occurred in %s(%s): ignore", project.getName(), a.getDisplayName(), a.getClass().getName()), e);
            }
            catch(Exception e)
            {
                LOGGER.log(Level.WARNING, String.format("%s: Failed to initialize environment %s(%s): skip", project.getName(), a.getDisplayName(), a.getClass().getName()), e);
            }
        }
        
        EnvVars.resolve(env);
        
        //// As in MatrixRun#getBuildVariables
        if(project instanceof MatrixConfiguration)
        {
            MatrixConfiguration child = (MatrixConfiguration)project;
            MatrixProject parent = child.getParent();
            
            // pick up user axes
            AxisList axes = parent.getAxes();
            for(Map.Entry<String,String> e : child.getCombination().entrySet())
            {
                Axis a = axes.find(e.getKey());
                if (a != null)
                {
                    a.addBuildVariable(e.getValue(), env);
                }
                else
                {
                    env.put(e.getKey(), e.getValue());
                }
            }
        }
        
        LOGGER.fine(String.format("%s: set environments %s", project.getName(), env.toString()));
        
        return new Binding(env);
    }
    
    /**
     * Class used for working with view.
     */
    @Extension
    static public class DescriptorImpl extends JobPropertyDescriptor
    {
        /**
         * Name to display.
         * @see hudson.model.Descriptor#getDisplayName()
         */
        @Override
        public String getDisplayName()
        {
            return Messages.GroovyLabelAssingmentProperty_DisplayName();
        }
        
        /** 
         * Create a new instance from the form input.
         * 
         * @see hudson.model.JobPropertyDescriptor#newInstance(org.kohsuke.stapler.StaplerRequest, net.sf.json.JSONObject)
         */
        @Override
        public JobProperty<?> newInstance(
                StaplerRequest req,
                JSONObject formData)
                throws hudson.model.Descriptor.FormException
        {
            super.newInstance(req, formData);
            if(formData.isNullObject())
            {
                return null;
            }
            
            JSONObject form = formData.getJSONObject(PROPERTYNAME);
            if(form == null || form.isNullObject())
            {
                return null;
            }
            
            @SuppressWarnings("unchecked")
            Class<? extends GroovyLabelAssingmentProperty> clazz
                = (Class<? extends GroovyLabelAssingmentProperty>)getClass().getEnclosingClass();
            
            return req.bindJSON(clazz, form);
        }
    }
}
