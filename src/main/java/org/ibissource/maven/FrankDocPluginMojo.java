/*
   Copyright 2021-2022 WeAreFrank!

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package org.ibissource.maven;

import java.io.File;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugins.javadoc.AggregatorJavadocJar;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.MavenReportException;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.codehaus.plexus.components.io.fileselectors.IncludeExcludeFileSelector;

@Mojo(
	name = "aggregate-jar",
	defaultPhase = LifecyclePhase.PROCESS_SOURCES,
	aggregator = true,
	threadSafe = true,
	requiresDependencyResolution = ResolutionScope.COMPILE
)
public class FrankDocPluginMojo extends AggregatorJavadocJar {
	public static final String DEFAULT_SOURCE_PATH = String.format("%ssrc%smain%sjava", File.separator, File.separator, File.separator);
	private static final String FRANK_CONFIG_COMPATIBILITY = "xml/xsd/FrankConfig-compatibility.xsd";
	private static final String FRANK_CONFIG_STRICT = "xml/xsd/FrankConfig.xsd";
	private static final String FRANK_CONFIG_JSON = "js/frankdoc.json";
	private static final String FRANK_CONFIG_SUMMARY = "txt/elementSummary.txt";

	@Component
	private ArchiverManager archiverManager;

	@Parameter(property = "includeDeLombokSources")
	private boolean includeDeLombokSources = true;

	@Parameter(property = "delombokSourcePath")
	private String defaultDeLombokPath = String.format("%starget%sgenerated-sources%sdelombok", File.separator, File.separator, File.separator);

	@Parameter(property = "reactorProjects", readonly = true)
	private List<MavenProject> subModules;

	@Parameter(property = "appendTo")
	private String appendTo;

	@Parameter(property = "frontendArtifact")
	private FrontendArtifact frontendArtifact;

	@Override
	public void doExecute() throws MojoExecutionException {
		if(includeDeLombokSources) {
			getLog().info("Property includeDeLombokSources is true, will try to use de-lombok-ed sources");
		}

		if(frontendArtifact != null) {
			locateAndExtractFrontend();
		}

		super.doExecute();

		List<Artifact> artifacts = project.getAttachedArtifacts();
		for(Artifact artifact : artifacts) {
			if(getClassifier().equals(artifact.getClassifier()) && project.getArtifact().getVersion().equals(artifact.getVersion())) {
				addAttachedArtifact(artifact); // We found the Frank!Doc artifact
			}
		}
	}

	private void locateAndExtractFrontend() throws MojoExecutionException {
		try {
			Artifact frontend = resolveDependency(frontendArtifact);
			File frontendLocation = frontend.getFile();
			getLog().info("Found Frank!Doc frontend artifact [" + frontendLocation + "]");

			File destDirectory = new File(getOutputDirectory());
			getLog().info("Frank!Doc frontend will be unarchived at [" + destDirectory + "]");
			destDirectory.mkdirs();
			unarchive(frontendLocation, destDirectory);

		} catch(MavenReportException e) {
			throw new MojoExecutionException("Unable to locate Frank!Doc frontend artifact.", e);
		}
	}

	private void unarchive(File artifactLocation, File toDirectory) throws MavenReportException {
		UnArchiver unArchiver;
		try {
			unArchiver = archiverManager.getUnArchiver("jar");
		} catch(NoSuchArchiverException e) {
			throw new MavenReportException("Unable to extract resources artifact. No archiver for 'jar' available.", e);
		}

		unArchiver.setSourceFile(artifactLocation);
		if(toDirectory == null || !toDirectory.exists() || !toDirectory.isDirectory()) {
			throw new MavenReportException("Frank!Doc [outputDirectory] does not exist!");
		}
		unArchiver.setDestDirectory(toDirectory);
		// Remove the META-INF directory from resource artifact
		IncludeExcludeFileSelector[] selectors = new IncludeExcludeFileSelector[]{new IncludeExcludeFileSelector()};
		selectors[0].setExcludes(new String[]{"META-INF/**"});
		unArchiver.setFileSelectors(selectors);

		getLog().info("Extracting contents of resources artifact: " + artifactLocation);
		try {
			unArchiver.extract();
		} catch(ArchiverException e) {
			throw new MavenReportException("Extraction of archive resources failed.", e);
		}
	}

	private void addAttachedArtifact(Artifact artifact) {
		if(appendTo == null) {
			return;
		}

		for(MavenProject reactorProject : this.subModules) {
			if(appendTo.equals(reactorProject.getArtifactId())) {
				File frankdoc = artifact.getFile();
				getLog().info("Found Frank!Doc artifact [" + frankdoc + "]");

				reactorProject.addResource(createFrontendResources());
				reactorProject.addResource(createCompatibilityResource());
				reactorProject.addAttachedArtifact(artifact);

				break;
			}
		}
	}

	private Resource createCompatibilityResource() {
		Resource resource = new Resource();
		resource.setDirectory(getOutputDirectory());
		resource.addInclude(FRANK_CONFIG_COMPATIBILITY);
		resource.addInclude(FRANK_CONFIG_STRICT);
		resource.addExclude(FRANK_CONFIG_SUMMARY);
		resource.setFiltering(false);
		return resource;
	}

	private Resource createFrontendResources() {
		Resource resource = new Resource();
		resource.setDirectory(getOutputDirectory());
		resource.addExclude(FRANK_CONFIG_COMPATIBILITY);
		resource.addExclude(FRANK_CONFIG_SUMMARY);
		resource.setTargetPath("META-INF/resources/iaf/frankdoc");
		resource.setFiltering(false);
		return resource;
	}

	@Override
	protected List<String> getProjectSourceRoots(MavenProject p) {
		if("pom".equalsIgnoreCase(p.getPackaging()) || p.getCompileSourceRoots() == null) {
			return Collections.emptyList();
		}

		if(includeDeLombokSources) {
			String sourcePathTest = DEFAULT_SOURCE_PATH;
			List<String> sources = p.getCompileSourceRoots();
			List<String> sourcesToUse = new LinkedList<>();
			for(String sourcePath : sources) {
				if(sourcePath.endsWith(sourcePathTest)) {
					String delombokPath = sourcePath.replace(sourcePathTest, defaultDeLombokPath);
					sourcesToUse.add(delombokPath); // We can't add the sources twice, so don't add the default path
					getLog().info("Added [" + delombokPath + "]");
				} else {
					sourcesToUse.add(sourcePath);
					getLog().info("Added [" + sourcePath + "]");
				}
			}

			return sourcesToUse;
		}

		return new LinkedList<>(p.getCompileSourceRoots());
	}

	@Override
	public Artifact resolveDependency(Dependency dependency) throws MavenReportException {
		for(MavenProject reactorProject : this.subModules) {
			if(dependency.getArtifactId().equals(reactorProject.getArtifactId()) && dependency.getGroupId().equals(reactorProject.getGroupId())) {
				return reactorProject.getArtifact();
			}
		}

		return super.resolveDependency(dependency);
	}

	@Override
	protected List<String> getExecutionProjectSourceRoots(MavenProject p) {
		return Collections.emptyList();
	}

	@Override
	protected String getClassifier() {
		return "frankdoc";
	}
}
