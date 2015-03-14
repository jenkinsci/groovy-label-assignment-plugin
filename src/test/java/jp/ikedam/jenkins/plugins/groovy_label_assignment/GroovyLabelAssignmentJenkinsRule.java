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

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutionException;

import jenkins.model.Jenkins;
import hudson.Functions;
import hudson.Util;
import hudson.PluginWrapper;
import hudson.matrix.MatrixProject;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.slaves.DumbSlave;
import hudson.util.IOUtils;

import org.junit.Before;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestEnvironment;
import org.jvnet.hudson.test.TestPluginManager;

/**
 * JenkinsRule used for testing GroovyLabelAssignment
 * 
 * mainly fix for Windows.
 * SEE https://wiki.jenkins-ci.org/display/JENKINS/Unit+Test+on+Windows for details.
 */
public class GroovyLabelAssignmentJenkinsRule extends JenkinsRule
{
    private static Thread deleteThread = null;
    
    static {
        registerCleanup();
    }
    
    @Before
    protected void before() throws Throwable {
        super.before();
    }
    
    // TestPluginManager leaves jar files open,
    // and fails to delete temporary directories in Windows.
    public static synchronized void registerCleanup() {
        if(deleteThread != null) {
            return;
        }
        deleteThread = new Thread("HOTFIX: cleanup " + TestPluginManager.INSTANCE.rootDir) {
            @Override public void run() {
                if(TestPluginManager.INSTANCE != null
                        && TestPluginManager.INSTANCE.rootDir != null
                        && TestPluginManager.INSTANCE.rootDir.exists()) {
                    // Work as PluginManager#stop
                    for(PluginWrapper p: TestPluginManager.INSTANCE.getPlugins())
                    {
                        p.stop();
                        p.releaseClassLoader();
                    }
                    TestPluginManager.INSTANCE.getPlugins().clear();
                    System.gc();
                    try {
                        Util.deleteRecursive(TestPluginManager.INSTANCE.rootDir);
                    } catch (IOException x) {
                        x.printStackTrace();
                    }
                }
            }
        };
        Runtime.getRuntime().addShutdownHook(deleteThread);
    }
    
    @Override
    protected void after()
    {
        try
        {
            removeSlaves();
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
        super.after();
        // Jenkins < 1.482, JenkinsRule leaves temporary directories.
        if(TestEnvironment.get() != null)
        {
            try
            {
                TestEnvironment.get().dispose();
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }
    }
    
    private void removeSlaves() throws ExecutionException, InterruptedException, IOException
    {
        // In Jenkins < 1.441, log files of slave nodes are not closed here,
        // so tearDown fails in Windows.
        // Close files to avoid this failure.
        // NOTE: This seems happen even with 1.466...
        if(Functions.isWindows()) {
            for(Node node: Jenkins.getInstance().getNodes()) {
                if(!(node instanceof DumbSlave))
                {
                    continue;
                }
                DumbSlave slave = (DumbSlave)node;
                slave.getComputer().cliDisconnect("tearDown");
                OutputStream out = slave.getComputer().openLogFile();
                Jenkins.getInstance().removeNode(slave);
                IOUtils.closeQuietly(out);
            }
        }
    }

    @Override
    public FreeStyleProject createFreeStyleProject() throws IOException
    {
        // createFreeStyleProject is protected with Jenkins < 1.479
        return super.createFreeStyleProject();
    }
    
    @Override
    public FreeStyleProject createFreeStyleProject(String name) throws IOException
    {
        // createFreeStyleProject is protected with Jenkins < 1.479
        return super.createFreeStyleProject(name);
    }
    
    @Override
    public MatrixProject createMatrixProject() throws IOException
    {
        // createMatrixProject is protected with Jenkins < 1.479
        return super.createMatrixProject();
    }
    
    public DumbSlave createOnlineSlave(String labelString) throws Exception
    {
        // QuickHack to set multiple labels.
        return super.createOnlineSlave(new LabelAtom(labelString){
            @Override
            public String getExpression()
            {
                return name;
            }
        });
    }
}
