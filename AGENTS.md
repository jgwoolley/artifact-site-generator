# Agents 

- the [./test.sh](./test.sh) should be run to validate whether anything has broken or not. 
- [./README.md](./README.md) should be kept up to date with relevant information. It also contains information on project milestones.
- If you update a java file, make sure it has good javadocs.
- Shared runtime libraries used by `artifact-site-cli` should be `provided` in plugin modules; the CLI supplies them at runtime.
- A plugin-only dependency (not shared with the CLI) must be shaded into that plugin's own jar via `maven-shade-plugin`, never added to `artifact-site-cli`'s `pom.xml` as a workaround — pf4j's plugin classloader only forwards `com.nf3t.artifactsite.api.*` and `org.pf4j.*` from the host. All plugin modules (`artifact-site-plugin-maven`, `artifact-site-plugin-vsix`) and the `artifact-site-plugin-archetype` template default to `maven-shade-plugin` for this reason — don't downgrade a plugin module back to plain `maven-jar-plugin`.
- Keep documentation aligned with implemented behavior; remove completed work from milestone/TODO lists.
- When changing catalog identity or replacement behavior, add a regression test proving duplicate handling and preserving distinct versions.