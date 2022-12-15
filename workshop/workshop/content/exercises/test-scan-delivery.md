##### Deliverable

**Finally**, lets add a deliverable before we apply the supply chain to the cluster.

Let's replace the previously used `ClusterTemplate` which was only able to habdle GitOps with the OOTB temaplte that can handle the **RegistryOps** use cases

```editor:select-matching-text
file: custom-supply-chain/custom-deliverable-template.yaml
text: "apiVersion: carto.run/v1alpha1"
before: 0
after: 20
```
##### Cluster Delivery ClusterTempalte

Now let's add the deployment spec as the last step.

```editor:replace-text-selection
file: custom-supply-chain/custom-deliverable-template.yaml
text: |2
    apiVersion: carto.run/v1alpha1
    kind: ClusterTemplate
    metadata:
      labels:
        app.tanzu.vmware.com/deliverable-type: web
      name: custom-deliverable-template-{{ session_namespace }}
    spec:
      params:
      - default: {}
        name: registry
      ytt: |
        #@ load("@ytt:data", "data")
        #@ load("@ytt:assert", "assert")

        #@ def merge_labels(fixed_values):
        #@   labels = {}
        #@   if hasattr(data.values.workload.metadata, "labels"):
        #@     labels.update(data.values.workload.metadata.labels)
        #@   end
        #@   labels.update(fixed_values)
        #@   return labels
        #@ end

        #@ def is_monorepo_approach():
        #@   if 'gitops_server_address' in data.values.params and 'gitops_repository_owner' in data.values.params and 'gitops_repository_name' in data.values.params:
        #@     return True
        #@   end
        #@   if 'gitops_server_address' in data.values.params or 'gitops_repository_owner' in data.values.params or 'gitops_repository_name' in data.values.params:
        #@     'gitops_server_address' in data.values.params or assert.fail("missing param: gitops_server_address")
        #@     'gitops_repository_owner' in data.values.params or assert.fail("missing param: gitops_repository_owner")
        #@     'gitops_repository_name' in data.values.params or assert.fail("missing param: gitops_repository_name")
        #@   end
        #@   return False
        #@ end

        #@ def has_git_params():
        #@   if 'gitops_repository_prefix' in data.values.params:
        #@     return True
        #@   end
        #@
        #@   if 'gitops_repository' in data.values.params:
        #@     return True
        #@   end
        #@
        #@   return False
        #@ end

        #@ def is_gitops():
        #@   return is_monorepo_approach() or has_git_params()
        #@ end

        #@ def param(key):
        #@   if not key in data.values.params:
        #@     return None
        #@   end
        #@   return data.values.params[key]
        #@ end

        #@ def strip_trailing_slash(some_string):
        #@   if some_string[-1] == "/":
        #@     return some_string[:-1]
        #@   end
        #@   return some_string
        #@ end

        #@ def mono_repository():
        #@   strip_trailing_slash(data.values.params.gitops_server_address)
        #@   return "/".join([
        #@     strip_trailing_slash(data.values.params.gitops_server_address),
        #@     strip_trailing_slash(data.values.params.gitops_repository_owner),
        #@     data.values.params.gitops_repository_name,
        #@   ]) + ".git"
        #@ end

        #@ def git_repository():
        #@   if is_monorepo_approach():
        #@     return mono_repository()
        #@   end
        #@
        #@   if 'gitops_repository' in data.values.params:
        #@     return param("gitops_repository")
        #@   end
        #@
        #@   prefix = param("gitops_repository_prefix")
        #@   return prefix + data.values.workload.metadata.name + ".git"
        #@ end

        #@ def image():
        #@   return "/".join([
        #@    data.values.params.registry.server,
        #@    data.values.params.registry.repository,
        #@    "-".join([
        #@      data.values.workload.metadata.name,
        #@      data.values.workload.metadata.namespace,
        #@      "bundle",
        #@    ])
        #@   ]) + ":" + data.values.workload.metadata.uid
        #@ end
        ---
        apiVersion: carto.run/v1alpha1
        kind: Deliverable
        metadata:
          name: #@ data.values.workload.metadata.name
          labels: #@ merge_labels({ "app.kubernetes.io/component": "deliverable", "app.tanzu.vmware.com/deliverable-type": "web" })
        spec:
          #@ if/end hasattr(data.values.workload.spec, "serviceAccountName"):
          serviceAccountName: #@ data.values.workload.spec.serviceAccountName

          #@ if/end is_gitops():
          params:
            - name: "gitops_ssh_secret"
              value: #@ param("gitops_ssh_secret")
            #@ if/end is_monorepo_approach():
            - name: gitops_sub_path
              value: #@ "config/" + data.values.workload.metadata.namespace + "/" + data.values.workload.metadata.name

          source:
            #@ if/end is_gitops():
            git:
              url: #@ git_repository()
              ref:
                branch: #@ param("gitops_branch")

            #@ if/end not is_gitops():
            image: #@ image()
```
Let's update our supply chain and replace the existing `Deliverable` that only handles GitOps with the OOTB one that also habdles RegistryOps

```editor:select-matching-text
file: custom-supply-chain/supply-chain.yaml
text: "- name: deliverable"
before: 0
after: 6
```

```editor:replace-text-selection
file: custom-supply-chain/supply-chain.yaml
text: |2

    - name: deliverable
      params:
      - name: registry
        value:
          ca_cert_data: ""
          repository: {{ ENV_CONTAINER_REGISTRY_REPOSITORY }}
          server: {{ ENV_CONTAINER_REGISTRY_HOSTNAME }}
      templateRef:
        kind: ClusterTemplate
        name: custom-deliverable-template-{{ session_namespace }}
```

At this point, we need to tweak our `ClusterDelievery` template to use **RegistryOps** as oppose to what we used earlier as **GitOps**.
For this, lets replace the `resources:` section. For `ClusterSourceTemplate`, we will re-use `custom-source-provider-template--{{ session_namespace }}` where we have both `GitRepository` & `ImageRepository` resources to choose from depending on workload `spec.source`.

However, for deployment of this extended supply chain, we cannot re-use the already created `ClusterDeploymentTemplate`, so lets go ahead and create a new one that reconciles the label selectors that are used for this testing-scanning supply chain.

```editor:append-lines-to-file
file: custom-supply-chain/custom-deployment-template-complex.yaml
text: |2
    apiVersion: carto.run/v1alpha1
    kind: ClusterDeploymentTemplate
    metadata:
      name: tanzu-java-web-app-deploy-complex-{{ session_namespace }}
    spec:
      healthRule:
        multiMatch:
          healthy:
            matchConditions:
            - status: "True"
              type: ReconcileSucceeded
          unhealthy:
            matchFields:
            - key: status.conditions[?(@.type=="ReconcileFailed")].status
              messagePath: .status.usefulErrorMessage
              operator: In
              values:
              - "True"
      observedCompletion:
        failed:
          key: .status.conditions[?(@.type=="ReconcileSucceeded")].status
          value: "False"
        succeeded:
          key: .status.conditions[?(@.type=="ReconcileSucceeded")].status
          value: "True"
      params:
      - default: default
        name: serviceAccount
      ytt: |
        #@ load("@ytt:data", "data")
        #@ load("@ytt:assert", "assert")

        #@ def merge_labels(fixed_values):
        #@   labels = {}
        #@   if hasattr(data.values.deliverable.metadata, "labels"):
        #@     labels.update(data.values.deliverable.metadata.labels)
        #@   end
        #@   labels.update(fixed_values)
        #@   return labels
        #@ end

        ---
        apiVersion: kappctrl.k14s.io/v1alpha1
        kind: App
        metadata:
          name: #@ data.values.deliverable.metadata.name
          labels: #@ merge_labels({ "app.kubernetes.io/component": "deploy" })
        spec:
          syncPeriod: 10m0s
          serviceAccountName: #@ data.values.params.serviceAccount
          fetch:
            - http:
                url: #@ data.values.deployment.url or assert.fail("missing required deployment config url")
                #@ if/end hasattr(data.values.params, "gitops_sub_path"):
                subPath: #@ data.values.params.gitops_sub_path

            - inline:
                paths:
                  config.yml: |
                    ---
                    apiVersion: kapp.k14s.io/v1alpha1
                    kind: Config
                    rebaseRules:
                      - path: [metadata, annotations, serving.knative.dev/creator]
                        type: copy
                        sources: [new, existing]
                        resourceMatchers: &matchers
                          - apiVersionKindMatcher: {apiVersion: serving.knative.dev/v1, kind: Service}
                      - path: [metadata, annotations, serving.knative.dev/lastModifier]
                        type: copy
                        sources: [new, existing]
                        resourceMatchers: *matchers
          template:
            - ytt: {}
            - kbld: {}
          deploy:
            - kapp: {}

```

And now lets replace the `resources:` section to use the newly created `ClusterDeploymentTemplate` and existing `ClusterSourceTemplate`.
```editor:select-matching-text
file: custom-supply-chain/custom-delivery.yaml
text: "  resources:"
after: 10
```
```editor:replace-text-selection
file: custom-supply-chain/custom-delivery.yaml
text: |2
    resources:
    - name: source-provider
      params:
      - name: serviceAccount
        value: default
      - name: gitImplementation
        value: go-git
      templateRef:
        kind: ClusterSourceTemplate
        name: custom-source-provider-template-{{ session_namespace }}
    - deployment:
        resource: source-provider
      name: deployer
      params:
      - name: serviceAccount
        value: default
      templateRef:
        kind: ClusterDeploymentTemplate
        name: tanzu-java-web-app-deploy-complex-{{ session_namespace }}
```
Next is to add a label selector thats applicable for this complex custom supply chain that is needed for `Delieverable` to be picked by `ClusterDelievery`.

```editor:select-matching-text
file: custom-supply-chain/custom-delivery.yaml
text: "  #app.tanzu.vmware.com/deliverable-type: web #uncomment this for complex flow"
```
```editor:replace-text-selection
file: custom-supply-chain/custom-delivery.yaml
text: |2
    app.tanzu.vmware.com/deliverable-type: web
```

We are now able to apply our custom supply chain to the cluster.
```terminal:execute
command: kapp deploy -a custom-supply-chain -f custom-supply-chain -y --dangerous-scope-to-fallback-allowed-namespaces
clear: true
```
