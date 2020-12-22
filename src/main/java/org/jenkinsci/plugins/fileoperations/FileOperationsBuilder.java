package org.jenkinsci.plugins.fileoperations;

import hudson.CopyOnWrite;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tools.ToolInstallation;
import hudson.util.ArgumentListBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.jenkinsci.plugins.fileoperations.util.StringUtil;
import org.kohsuke.stapler.DataBoundConstructor;

public class FileOperationsBuilder extends Builder {

    private final String exeName;
    private final boolean failBuild;
    private List<CopyOperation> copyOperations;
	private List<DeleteOperation> deleteOperations;

    /**
     *
     * @param exeName
     * @param failBuild
     * @param copyOperations
     * @param deleteOperations
     */
    @DataBoundConstructor
    public FileOperationsBuilder(String exeName, boolean failBuild, List<CopyOperation> copyOperations, List<DeleteOperation> deleteOperations) {
        this.exeName     = exeName;
        this.failBuild   = failBuild;
        this.copyOperations = copyOperations;
		this.deleteOperations = deleteOperations;
    }

    public String getExeName() {
        return exeName;
    }

    public boolean isFailBuild() {
        return failBuild;
    }

    public List<CopyOperation> getCopyOperations() { return this.copyOperations; }
	
	public List<DeleteOperation> getDeleteOperations() { return this.deleteOperations; }

    public FileOperationsInstallation getInstallation() {
        if (exeName == null) return null;
        for (FileOperationsInstallation i : DESCRIPTOR.getInstallations()) {
            if (exeName.equals(i.getName()))
                return i;
        }
        return null;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {

        ArrayList<String> args = new ArrayList<String>();
        EnvVars env = build.getEnvironment(listener);

        FileOperationsInstallation installation = getInstallation();
        if (installation == null) {
            listener.fatalError("FileOperationsInstallation not found.");
            return false;
        }
        installation = installation.forNode(Computer.currentComputer().getNode(), listener);
        installation = installation.forEnvironment(env);

        // exe path.
        String exePath = getExePath(installation, launcher, listener);
        if (StringUtil.isNullOrSpace(exePath)) return false;
        args.add(exePath);
				
		if (copyOperations != null && !copyOperations.isEmpty())
		{
			for (CopyOperation copyOperation : copyOperations)
			{
				String sourcePath = copyOperation.name;
				String targetPath = copyOperation.targetLocation;
				
				if (!StringUtil.isNullOrSpace(sourcePath) && !StringUtil.isNullOrSpace(targetPath))
				{
					try {
						String valueSource = TokenMacro.expandAll(build, listener, sourcePath);
						String valueTarget = TokenMacro.expandAll(build, listener, targetPath);
						args.add("\"copy=" + valueSource + "=" + valueTarget + "\"");
					} catch (MacroEvaluationException ex) {
						Logger.getLogger(FileOperationsBuilder.class.getName()).log(Level.SEVERE, null, ex);
					}
				}
			}
		}
		
		if (deleteOperations != null && !deleteOperations.isEmpty())
		{
			for (DeleteOperation deleteOperation : deleteOperations)
			{
				String sourcePath = deleteOperation.name;
				
				if (!StringUtil.isNullOrSpace(sourcePath))
				{
					try {
						String valueSource = TokenMacro.expandAll(build, listener, sourcePath);
						args.add("\"del=" + valueSource + "\"");
					} catch (MacroEvaluationException ex) {
						Logger.getLogger(FileOperationsBuilder.class.getName()).log(Level.SEVERE, null, ex);
					}
				}
			}
		}

        // exe run.
        boolean r = exec(args, build, launcher, listener, env);

        return r;
    }


    /**
     *
     * @param  installation
     * @param  launcher
     * @param  listener
     * @return
     * @throws InterruptedException
     * @throws IOException
     */
    private String getExePath(FileOperationsInstallation installation, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        String pathToExe = installation.getHome();
        FilePath exec = new FilePath(launcher.getChannel(), pathToExe);

        try {
            if (!exec.exists()) {
                listener.fatalError(pathToExe + " doesn't exist");
                return null;
            }
        } catch (IOException e) {
            listener.fatalError("Failed checking for existence of " + pathToExe);
            return null;
        }

        listener.getLogger().println("Path To exe: " + pathToExe);
        return StringUtil.appendQuote(pathToExe);
    }

    /**
     *
     * @param  build
     * @param  listener
     * @param  values
     * @return
     * @throws InterruptedException
     * @throws IOException
     */
    private List<String> getArguments(AbstractBuild<?, ?> build, BuildListener listener, String values) throws InterruptedException, IOException {
        ArrayList<String> args = new ArrayList<String>();
        StringTokenizer valuesToknzr = new StringTokenizer(values, " \t\r\n");

        while (valuesToknzr.hasMoreTokens()) {
            String value = valuesToknzr.nextToken();
            try {
                value = TokenMacro.expandAll(build, listener, value);
            } catch (MacroEvaluationException ex) {
                Logger.getLogger(FileOperationsBuilder.class.getName()).log(Level.SEVERE, null, ex);
            }

            if (!StringUtil.isNullOrSpace(value))
                args.add(value);
        }

        return args;
    }

    /**
     *
     * @param  args
     * @param  build
     * @param  launcher
     * @param  listener
     * @param  env
     * @return
     * @throws InterruptedException
     * @throws IOException
     */
    private boolean exec(List<String> args, AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, EnvVars env) throws InterruptedException, IOException {
        ArgumentListBuilder cmdExecArgs = new ArgumentListBuilder();
        FilePath tmpDir = null;
        FilePath pwd = build.getWorkspace();

        if (!launcher.isUnix()) {
            tmpDir = pwd.createTextTempFile("exe_runner_", ".bat", StringUtil.concatString(args), false);
            cmdExecArgs.add("cmd.exe", "/C", tmpDir.getRemote(), "&&", "exit", "%ERRORLEVEL%");
        } else {
            for (String arg : args) {
                cmdExecArgs.add(arg);
            }
        }

        listener.getLogger().println("Executing : " + cmdExecArgs.toStringWithQuote());

        try {
            int r = launcher.launch().cmds(cmdExecArgs).envs(env).stdout(listener).pwd(pwd).join();

            if (failBuild)
                return (r == 0);
            else {
                if (r != 0)
                    build.setResult(Result.UNSTABLE);
                return true;
            }
        } catch (IOException e) {
            Util.displayIOException(e, listener);
            e.printStackTrace(listener.fatalError("execution failed"));
            return false;
        } finally {
            try {
                if (tmpDir != null) tmpDir.delete();
            } catch (IOException e) {
                Util.displayIOException(e, listener);
                e.printStackTrace(listener.fatalError("temporary file delete failed"));
            }
        }
    }


    @Override
    public Descriptor<Builder> getDescriptor() {
         return DESCRIPTOR;
    }

    /**
     * Descriptor should be singleton.
     */
    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    /**
     * @author Yasuyuki Saito
     */
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @CopyOnWrite
        private volatile FileOperationsInstallation[] installations = new FileOperationsInstallation[0];

        DescriptorImpl() {
            super(FileOperationsBuilder.class);
            load();
        }

        @Override
        public String getDisplayName() {
            return Messages.FileOperationsBuilder_DisplayName();
        }

        public FileOperationsInstallation[] getInstallations() {
            return installations;
        }

        public void setInstallations(FileOperationsInstallation... installations) {
            this.installations = installations;
            save();
        }

        /**
         * Obtains the {@link FileOperationsInstallation.DescriptorImpl} instance.
         */
        public FileOperationsInstallation.DescriptorImpl getToolDescriptor() {
            return ToolInstallation.all().get(FileOperationsInstallation.DescriptorImpl.class);
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
}
