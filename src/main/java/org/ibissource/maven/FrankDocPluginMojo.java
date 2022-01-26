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

import org.apache.maven.ProjectDependenciesResolver;
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

@Mojo(name = "aggregate-jar", defaultPhase = LifecyclePhase.PROCESS_SOURCES, aggregator = true, requiresDependencyResolution = ResolutionScope.COMPILE)
public class FrankDocPluginMojo extends AggregatorJavadocJar {
	public static final String DEFAULT_SOURCE_PATH = String.format("%ssrc%smain%sjava", File.separator, File.separator, File.separator);

	@Component
	private ProjectDependenciesResolver projectDependenciesResolver;

	@Parameter(property = "includeDeLombokSources")
	private boolean includeDeLombokSources = true;

	@Parameter(property = "delombokSourcePath")
	private String defaultDeLombokPath = String.format("%starget%sgenerated-sources%sdelombok", File.separator, File.separator, File.separator);

	@Parameter(property = "reactorProjects", readonly = true)
	private List<MavenProject> subModules;

	@Parameter(property = "appendTo")
	private String appendTo;

	@Override
	public void doExecute() throws MojoExecutionException {
		if(includeDeLombokSources) {
			getLog().info("Property includeDeLombokSources is true, will try to use de-lombok-ed sources");
		}

		super.doExecute();

		List<Artifact> artifacts = project.getAttachedArtifacts();
		for(Artifact artifact : artifacts) {
			if(getClassifier().equals(artifact.getClassifier()) && project.getArtifact().getVersion().equals(artifact.getVersion())) {
				addAttachedArtifact(artifact); //We found the Frank!Doc artifact
			}
		}
	}

	private void addAttachedArtifact(Artifact artifact) {
		if(appendTo == null) {
			return;
		}

		for (MavenProject reactorProject : this.subModules ) {
			if(appendTo.equals(reactorProject.getArtifactId())) {
				File frankdoc = artifact.getFile();
				getLog().info( "Found frankdoc artifact ["+frankdoc+"]" );
				Resource resource = new Resource();
				resource.setDirectory(getOutputDirectory());
				reactorProject.addResource(resource);
				reactorProject.addAttachedArtifact(artifact);
				break;
			}
		}
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
					getLog().info(String.format("Added [%s]", delombokPath));
				} else {
					sourcesToUse.add(sourcePath);
					getLog().info(String.format("Added [%s]", sourcePath));
				}
			}

			return sourcesToUse;
		}

		return new LinkedList<>(p.getCompileSourceRoots());
	}

	@Override
	public Artifact resolveDependency(Dependency dependency) throws MavenReportException {
		for (MavenProject reactorProject : this.subModules ) {
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