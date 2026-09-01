<#import "layout.ftl" as layout>
<@layout.page title=title rootPath=rootPath pageHeading=pageHeading pageDescription=pageDescription>
  <#if !hasCiInfo>
    <p class="lead">This site was not generated from a recognized CI/CD pipeline, so source and run links are unavailable.</p>
  </#if>

  <div class="grid">
    <section class="sidebar-card">
      <h2>Source</h2>
      <dl class="detail-list">
        <#if provider?has_content><dt>CI/CD Provider</dt><dd>${provider?html}</dd></#if>
        <#if repositoryName?has_content>
          <dt>Repository</dt>
          <dd>
            <#if repositoryUrl?has_content><a href="${repositoryUrl?html}">${repositoryName?html}</a><#else>${repositoryName?html}</#if>
          </dd>
        </#if>
        <#if ref?has_content><dt>Branch / Tag</dt><dd>${ref?html}</dd></#if>
        <#if commitSha?has_content>
          <dt>Commit</dt>
          <dd class="hash">
            <#if commitUrl?has_content><a href="${commitUrl?html}">${commitShortSha?html}</a><#else>${commitShortSha?html}</#if>
          </dd>
        </#if>
      </dl>
    </section>

    <section class="sidebar-card">
      <h2>CI/CD Run</h2>
      <dl class="detail-list">
        <#if workflowName?has_content><dt>Workflow / Pipeline</dt><dd>${workflowName?html}</dd></#if>
        <#if runLabel?has_content>
          <dt>Run</dt>
          <dd><#if runUrl?has_content><a href="${runUrl?html}">${runLabel?html}</a><#else>${runLabel?html}</#if></dd>
        </#if>
        <#if eventName?has_content><dt>Trigger</dt><dd>${eventName?html}</dd></#if>
        <#if triggeredBy?has_content><dt>Triggered By</dt><dd>${triggeredBy?html}</dd></#if>
      </dl>
    </section>

    <section class="sidebar-card">
      <h2>Generation</h2>
      <dl class="detail-list">
        <dt>Generated At</dt><dd>${generatedAt?html}</dd>
      </dl>
    </section>
  </div>
</@layout.page>
