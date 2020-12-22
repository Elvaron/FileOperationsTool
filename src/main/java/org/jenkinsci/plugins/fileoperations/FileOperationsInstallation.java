package org.jenkinsci.plugins.fileoperations;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentSpecific;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import java.io.IOException;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

public final class FileOperationsInstallation extends ToolInstallation implements NodeSpecific<FileOperationsInstallation>, EnvironmentSpecific<FileOperationsInstallation> {

    /** */
    private transient String pathToExe;

    @DataBoundConstructor
    public FileOperationsInstallation(String name, String home) {
        super(name, home, null);
    }

    @Override
    public FileOperationsInstallation forNode(Node node, TaskListener log) throws IOException, InterruptedException {
        return new FileOperationsInstallation(getName(), translateFor(node, log));
    }

    @Override
    public FileOperationsInstallation forEnvironment(EnvVars environment) {
        return new FileOperationsInstallation(getName(), environment.expand(getHome()));
    }

    @Override
    protected Object readResolve() {
        if (this.pathToExe != null) {
            return new FileOperationsInstallation(this.getName(), this.pathToExe);
        }
        return this;
    }

    @Extension
    public static class DescriptorImpl extends ToolDescriptor<FileOperationsInstallation> {

        @Override
        public String getDisplayName() {
            return Messages.FileOperationsInstallation_DisplayName();
        }

        @Override
        public FileOperationsInstallation[] getInstallations() {
            return Jenkins.getInstance().getDescriptorByType(FileOperationsBuilder.DescriptorImpl.class).getInstallations();
        }

        @Override
        public void setInstallations(FileOperationsInstallation... installations) {
            Jenkins.getInstance().getDescriptorByType(FileOperationsBuilder.DescriptorImpl.class).setInstallations(installations);
        }

    }
}
