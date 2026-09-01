<#import "layout.ftl" as layout>
<@layout.page title=title rootPath=rootPath pageHeading=pageHeading pageDescription=pageDescription>
  <div class="grid artifact-grid">
    <#list artifactCards as artifact>
      <a class="card artifact-card" href="${rootPath}${artifact.url?remove_beginning('/')}">
        <#if artifact.icon?has_content><img class="card-icon" src="${rootPath}${artifact.icon?remove_beginning('/')}" alt="" width="56" height="56"></#if>
        <div class="card-body">
          <h3>${artifact.name?html}</h3>
          <p class="meta">${artifact.groupId?html}.${artifact.artifactId?html} • ${artifact.version?html} • ${artifact.parserType?html}</p>
          <p class="card-description">${artifact.description?html}</p>
          <div class="tags">
            <#list artifact.tags as tag><span class="tag">${tag?html}</span></#list>
          </div>
        </div>
      </a>
    </#list>
  </div>
</@layout.page>
