package com.example;

import com.example.model.GitHubEvent;
import com.example.model.V1GitHubRepository;
import com.example.model.V1GitHubRepositoryList;
import io.kubernetes.client.apimachinery.GroupVersion;
import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public class GitHubSourceReconciler implements Reconciler {

    private static final Logger log = LoggerFactory.getLogger(GitHubSourceReconciler.class);

    private final SharedIndexInformer<V1GitHubRepository> informer;
    private final  GenericKubernetesApi<V1GitHubRepository, V1GitHubRepositoryList> api;
    private final GitHubSourceApplicationService service;

    public GitHubSourceReconciler(SharedIndexInformer<V1GitHubRepository> informer, GenericKubernetesApi<V1GitHubRepository, V1GitHubRepositoryList> api, GitHubSourceApplicationService service) {
        this.informer = informer;
        this.api = api;
        this.service = service;
    }

    @Override
    public Result reconcile(Request request) {
        Lister<V1GitHubRepository> lister = new Lister<>(informer.getIndexer(), request.getNamespace());
        V1GitHubRepository resource = lister.get(request.getName());

        log.info("triggered reconciling " + resource.getMetadata().getNamespace() + "/" + resource.getMetadata().getName());

        if (resource == null || resource.getMetadata().getDeletionTimestamp() != null) {
            return new Result(false);
        }

        GitHubEvent latestGithubEvent = service.getLatestGithubEvent(resource.getSpec().getUrl(), resource.getSpec().getBranch());
        if (latestGithubEvent == null) {
            log.info("No GitHub Event for " + resource.getMetadata().getNamespace() + "/" + resource.getMetadata().getName() + " found");
        } else {
            resource.updateStatus(latestGithubEvent.getTarballUrl(), latestGithubEvent.getRevision());
            log.info("Trying to update status for " + resource.getMetadata().getNamespace() + "/" + resource.getMetadata().getName() + " with " + latestGithubEvent.getTarballUrl());
            KubernetesApiResponse<V1GitHubRepository> update = api.updateStatus(resource, V1GitHubRepository::getStatus);
            if (!update.isSuccess()) {
                log.warn("Cannot update GithubRepository " + resource.getMetadata().getNamespace() + "/" + resource.getMetadata().getName() + " Status code " + update.getHttpStatusCode());
            }
        }
        return new Result(true, Duration.ofSeconds(30));
    }
}
