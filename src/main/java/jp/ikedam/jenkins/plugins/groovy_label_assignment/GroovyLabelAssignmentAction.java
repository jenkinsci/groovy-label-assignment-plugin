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

import jenkins.model.Jenkins;
import hudson.model.Label;
import hudson.model.labels.LabelAssignmentAction;
import hudson.model.queue.SubTask;

/**
 * Holds the label name assigned by GroovyLabelAssignmentProperty.
 */
public class GroovyLabelAssignmentAction implements LabelAssignmentAction
{
    @Deprecated
    transient private Label label;
    
    private final String labelString;
    
    /**
     * Constructor
     * 
     * @param labelString assigned label expression.
     * @since 1.1.1
     */
    public GroovyLabelAssignmentAction(String labelString)
    {
        this.labelString = labelString;
    }
    
    /**
     * @param label
     * @deprecated use {@link GroovyLabelAssignmentAction#GroovyLabelAssignmentAction(String)}
     */
    @Deprecated
    public GroovyLabelAssignmentAction(Label label)
    {
        this(label.getExpression());
    }
    
    private Object readResolve()
    {
        if(label != null)
        {
            return new GroovyLabelAssignmentAction(label.getExpression());
        }
        return this;
    }
    
    /**
     * @return null not for being displayed in the side menu of builds.
     * @see hudson.model.Action#getIconFileName()
     */
    @Override
    public String getIconFileName()
    {
        return null;
    }
    
    /**
     * Returns the display name.
     * 
     * This is never used, for not displayed in the side menu of builds.
     * 
     * @return display name
     * @see hudson.model.Action#getDisplayName()
     */
    @Override
    public String getDisplayName()
    {
        return null;
    }
    
    /**
     * @return null not for being displayed in the side menu of builds.
     * @see hudson.model.Action#getUrlName()
     */
    @Override
    public String getUrlName()
    {
        return null;
    }
    
    /**
     * Returns the label which specifies nodes where this job should run.
     * 
     * Implemented as overridden method.
     * 
     * @return an assigned label.
     * @see hudson.model.labels.LabelAssignmentAction#getAssignedLabel(hudson.model.queue.SubTask)
     */
    @Override
    public Label getAssignedLabel(SubTask task)
    {
        return getAssignedLabel();
    }
    
    
    /**
     * Returns the label which specifies nodes where this job should run.
     * 
     * Implemented as getter.
     * 
     * @return an assigned label.
     */
    public Label getAssignedLabel()
    {
        return Jenkins.getInstance().getLabel(getLabelString());
    }
    
    /**
     * @return the expression string for the assigned label.
     * @since 1.1.1
     */
    public String getLabelString()
    {
        return labelString;
    }
}
