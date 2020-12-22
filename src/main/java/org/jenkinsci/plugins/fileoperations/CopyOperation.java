package org.jenkinsci.plugins.fileoperations;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;

public class CopyOperation extends AbstractDescribableImpl<CopyOperation>
{
  public String name;
  public String targetLocation;

  @DataBoundConstructor
  public CopyOperation(String name, String targetLocation) {
    this.name = name;
	this.targetLocation = targetLocation;
  }

  public String getName() {
    return this.name;
  }
  
  public String getTargetLocation() {
	  return this.targetLocation;
  }

  @Extension
  public static final class DescriptorImpl
      extends Descriptor<CopyOperation> {

    @Override
    public String getDisplayName() {
      return "Copy operation";
    }
  }

}