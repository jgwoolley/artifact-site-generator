<#import "layout.ftl" as layout>
<@layout.page title=title rootPath=rootPath pageHeading=pageHeading pageDescription=pageDescription>
  <#if parserSummaries?? && parserSummaries?size gt 0>
    <section>
      <h2>Parsers</h2>
      <div class="grid parser-grid">
        <#list parserSummaries as parser>
          <a class="card parser-card" href="${rootPath}${parser.url?remove_beginning('/')}">
            <strong>${parser.parserName?html}</strong>
            <span>${parser.artifactCount} artifact(s)</span>
          </a>
        </#list>
      </div>
    </section>
  </#if>

  <#if artifactCards?? && artifactCards?size gt 0>
    <section>
      <h2>Latest Artifacts</h2>
      <div class="grid artifact-grid" data-search-target="cards">
        <#list artifactCards as artifact>
          <article class="card artifact-card" data-entry-url="${artifact.url}">
            <h3><a href="${rootPath}${artifact.url?remove_beginning('/')}">${artifact.name?html}</a></h3>
            <p class="meta">${artifact.groupId?html}.${artifact.artifactId?html} • ${artifact.version?html} • ${artifact.parserType?html}</p>
            <p>${artifact.description?html}</p>
            <div class="tags">
              <#list artifact.tags as tag><span class="tag">${tag?html}</span></#list>
            </div>
          </article>
        </#list>
      </div>
    </section>
  </#if>

  <#if versions?? && versions?size gt 0>
    <section>
      <h2>Versions</h2>
      <table>
        <thead>
          <tr><th>Version</th><th>Description</th><th>Details</th><th>Download</th></tr>
        </thead>
        <tbody>
          <#list versions as row>
            <tr>
              <td>${row.version?html}</td>
              <td>${row.description?html}</td>
              <td><a href="${rootPath}${row.detailUrl?remove_beginning('/')}">View</a></td>
              <td><a href="${row.downloadUrl?html}">Download</a></td>
            </tr>
          </#list>
        </tbody>
      </table>
    </section>
  </#if>
</@layout.page>
