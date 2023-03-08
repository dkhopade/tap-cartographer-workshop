Let us take a look at what it takes to add the capabilites of Testing and Scanning to our supply chain.

The easiest way to get started with building a custom supply chain is to copy one of the out-of-the-box supply chains from the cluster, change the `metadata.name`, and add a unique selector by e.g. adding a label to the `spec.selector` configuration.

For this part of the exercise, we will reuse some of the OOTB templates and discover the ways of providing and modifying a template.

We will leverage a Kubernetes native CI/CD solution like Tekton to do the job, which is part of TAP distribution.

Let's start by adding the necessary selectors within supply chain so that it can pickup the workloads based on it. We will begin by adding the following mandatory selectors

```editor:select-matching-text
file: custom-supply-chain/supply-chain.yaml
text: "  selector:"
```
Add `selector` to Supply Chain.
```editor:replace-text-selection
file: custom-supply-chain/supply-chain.yaml
text: |2
    selector:
      apps.tanzu.vmware.com/has-tests: "true"ß
      apps.tanzu.vmware.com/workload-type: web
```

Next we need to add the additional parameters 

```editor:select-matching-text
file: custom-supply-chain/supply-chain.yaml
text: "  params: []"
```
Add `selector` to Supply Chain.
```editor:replace-text-selection
file: custom-supply-chain/supply-chain.yaml
text: |2

    params:
    - name: maven_repository_url
      value: https://repo.maven.apache.org/maven2
    - default: main
      name: gitops_branch
    - default: supplychain
      name: gitops_user_name
    - default: supplychain
      name: gitops_user_email
    - default: supplychain@cluster.local
      name: gitops_commit_message
    - default: ""
      name: gitops_ssh_secret  

```

As with the basic supply chains we just created, the **first task** for our custom supply chain is also to **provide the latest version of a source code in a Git repository for subsequent steps**.
In the simple and ootb supply chains we used the [Flux](https://fluxcd.io) Source Controller for it. 

The application then has to be packaged in a container, deployed to Kubernetes, and exposed to be reachable by the GitHub Webhook of a Git repository that also has to be configured - which is already done for you.

### ClusterSourceTemplate - Workload GitRepo

Based on the provided information we will reuse our existing `ClusterSourceTemplate` that we created a few minutes ago to get the workload app source code from Git repository.

```editor:open-file
file: custom-supply-chain/custom-source-provider-template.yaml
```

