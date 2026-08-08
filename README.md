# Artifact Site Generator

A Java CLI for generating release websites for artifacts deployed within CI/CD.

## Projects

### artifact-site-plugins-api

Contains the PF4J api for defining custom plugins. These PF4J Plugins can register IArtifactParser which are given an InputStream, file metadata, and a context object that can write metadata associated with the given artifact.

Each artifact should have the ability to write a artifact name (ala project human readable name), artifact id (ala Maven artifact id), group id, authors, version, and tags. The version / human readable name / project id must be provided. This project has the goal of supporting Apache NiFi NAR files, and VSCode VSIX files.

### artifact-site-cli

A picocli CLI that can be configured with Jars that implement artifact-site-plugins-api to register artifact parsers. These plugin Jars should be stored based on the XGD standard in a sensible location, and loaded in on startup.

There should be a CLI option for copying a JAR to the correct location for it to be loaded automatically, but also because it implements XGD standard users will be able to update where this location is.

```sh
artifact-site-generator add-plugin jar-site-generator.jar
```

A local file can be provided:

```sh
artifact-site-generator parse jar example.jar
```

Or a remote HTTP Server (Apache HTTP Client should be used):

```sh
artifact-site-generator parse jar https://example.com/jars/example.jar
```

Both of these commands will write out the metadata of the files. This will default to a sensible location based on XGD standard, or when provided with a CLI option, will write out to a specified location.

After the user runs the parse command as many times as they would like then a site will be generated at the local ./public folder (or overridden by a CLI option).

```sh
artifact-site-generator generate --output=./dist
```

This project will be an uberjar so it contains everything it needs to run.

### Plugins

* there will be a jar-site-generator Maven project that implements artifact-site-plugins-api
