# Agents 

- the [./test.sh](./test.sh) should be run to validate whether anything has broken or not. 
- [./README.md](./README.md) should be kept up to date with relevant information. It also contains information on project milestones.
- If you update a java file, make sure it has good javadocs.
- Shared runtime libraries used by `artifact-site-cli` should be `provided` in plugin modules; the CLI supplies them at runtime.
- Keep documentation aligned with implemented behavior; remove completed work from milestone/TODO lists.
- When changing catalog identity or replacement behavior, add a regression test proving duplicate handling and preserving distinct versions.