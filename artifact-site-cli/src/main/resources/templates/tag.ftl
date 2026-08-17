<#import "layout.ftl" as layout>
<@layout.page title=title rootPath=rootPath pageHeading=pageHeading pageDescription=pageDescription>
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
</@layout.page>
