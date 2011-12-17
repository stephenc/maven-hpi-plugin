package org.jenkinsci.maven.plugins.jpi;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.archiver.jar.Manifest;
import org.codehaus.plexus.archiver.jar.Manifest.Attribute;
import org.codehaus.plexus.archiver.jar.Manifest.Section;
import org.codehaus.plexus.archiver.jar.ManifestException;
import org.codehaus.plexus.util.IOUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Set;

/**
 * Generate .jpl file.
 *
 * @author Kohsuke Kawaguchi
 * @goal jpl
 * @requiresDependencyResolution runtime
 */
public class JplMojo extends AbstractJpiMojo {
    /**
     * Path to <tt>$JENKINS_HOME</tt>. A .jpl file will be generated to this location.
     *
     * @parameter expression="${jenkinsHome}
     */
    private File jenkinsHome;

    public void setHudsonHome(File hudsonHome) {
        this.jenkinsHome = hudsonHome;
    }

    public void execute() throws MojoExecutionException, MojoFailureException {
        if(!project.getPackaging().equals("jpi")) {
            getLog().info("Skipping "+project.getName()+" because it's not <packaging>jpi</packaging>");
            return;
        }

        File jplFile = computeJplFile();
        getLog().info("Generating "+jplFile);

        PrintWriter printWriter = null;
        try {
            Manifest mf = new Manifest();
            Section mainSection = mf.getMainSection();
            setAttributes(mainSection);

            // compute Libraries entry
            StringBuilder buf = new StringBuilder();

            // we want resources to be picked up before target/classes,
            // so that the original (not in the copy) will be picked up first.
            for (Resource r : (List<Resource>) project.getBuild().getResources()) {
                if(buf.length()>0)
                    buf.append(',');
                if(new File(project.getBasedir(),r.getDirectory()).exists())
                    buf.append(r.getDirectory());
            }
            if(buf.length()>0)
                buf.append(',');
            buf.append(new File(project.getBuild().getOutputDirectory()).getAbsoluteFile());
            for (Artifact a : (Set<Artifact>) project.getArtifacts()) {
                if ("provided".equals(a.getScope()))
                    continue;   // to simulate the real environment, drop the "provided" scope dependencies from the list
                if ("pom".equals(a.getType()))
                    continue;   // pom dependency is sometimes used so that one can depend on its transitive dependencies
                buf.append(',').append(a.getFile());
            }
            mainSection.addAttributeAndCheck(new Attribute("Libraries",buf.toString()));

            // compute Resource-Path entry
            mainSection.addAttributeAndCheck(new Attribute("Resource-Path",warSourceDirectory.getAbsolutePath()));

            printWriter = new PrintWriter(new FileWriter(jplFile));
            mf.write(printWriter);
        } catch (ManifestException e) {
            throw new MojoExecutionException("Error preparing the manifest: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new MojoExecutionException("Error preparing the manifest: " + e.getMessage(), e);
        } finally {
            IOUtil.close(printWriter);
        }
    }

    /**
     * Determine where to produce the .jpl file.
     */
    protected File computeJplFile() throws MojoExecutionException {
        if(jenkinsHome==null) {
            throw new MojoExecutionException(
                "Property jenkinsHome needs to be set to $JENKINS_HOME. Please use 'mvn -DjenkinsHome=...' or" +
                "put <settings><profiles><profile><properties><property><jenkinsHome>...</...>"
            );
        }

        File jplFile = new File(jenkinsHome, "plugins/" + project.getBuild().getFinalName() + ".jpl");
        return jplFile;
    }
}