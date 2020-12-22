package org.jenkinsci.plugins.fileoperations;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;

public class DeleteOperation extends AbstractDescribableImpl<DeleteOperation>
{
  public String name;

  @DataBoundConstructor
  public DeleteOperation(String name) {
    this.name = name;
  }

  public String getName() {
    return this.name;
  }

  @Extension
  public static final class DescriptorImpl
      extends Descriptor<DeleteOperation> {

    @Override
    public String getDisplayName() {
      return "Delete operation";
    }
  }

}