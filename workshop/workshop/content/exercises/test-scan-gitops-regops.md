##### Handling both GitOps and RegistryOps Scenarios
Let's replace our previously customized `ClusterTemplate` with the one that comes from OOTB templates. This will be used to create a `deliverable` which will in turn create a `Runnable` task to generate the deployment manifest and push it to either GitOps Repository or designated Container Registry.

```editor:select-matching-text
file: custom-supply-chain/custom-config-writer-template.yaml
text: "apiVersion: carto.run/v1alpha1"
before: 0
after: 26
```

```editor:replace-text-selection
file: custom-supply-chain/custom-config-writer-template.yaml
text: |2
    apiVersion: carto.run/v1alpha1
    kind: ClusterTemplate
    metadata:
      name: custom-config-writer-template-{{ session_namespace }}
    spec:
      healthRule:
        singleConditionType: Ready
      params:
      - default: default
        name: serviceAccount
      - default: {}
        name: registry
      ytt: |
        #@ load("@ytt:data", "data")
        #@ load("@ytt:json", "json")
        #@ load("@ytt:base64", "base64")
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

        #@ def ca_cert_data():
        #@   if "ca_cert_data" not in param("registry"):
        #@     return ""
        #@   end
        #@
        #@   return param("registry")["ca_cert_data"]
        #@ end

        ---
        apiVersion: carto.run/v1alpha1
        kind: Runnable
        metadata:
          name: #@ data.values.workload.metadata.name + "-config-writer"
          labels: #@ merge_labels({ "app.kubernetes.io/component": "config-writer" })
        spec:
          #@ if/end hasattr(data.values.workload.spec, "serviceAccountName"):
          serviceAccountName: #@ data.values.workload.spec.serviceAccountName

          runTemplateRef:
            name: tekton-taskrun

          inputs:
            serviceAccount: #@ data.values.params.serviceAccount
            taskRef:
              kind: ClusterTask
              name: #@ "git-writer" if is_gitops() else "image-writer"
            params:
              #@ if is_gitops():
              - name: git_repository
                value: #@ git_repository()
              - name: git_branch
                value: #@ param("gitops_branch")
              - name: git_user_name
                value: #@ param("gitops_user_name")
              - name: git_user_email
                value: #@ param("gitops_user_email")
              - name: git_commit_message
                value: #@ param("gitops_commit_message")
              - name: git_files
                value: #@ base64.encode(json.encode(data.values.config))
              #@ if/end is_monorepo_approach():
              - name: sub_path
                value: #@ "config/" + data.values.workload.metadata.namespace + "/" + data.values.workload.metadata.name
              #@ else:
              - name: files
                value: #@ base64.encode(json.encode(data.values.config))
              - name: bundle
                value: #@ image()
              - name: ca_cert_data
                value: #@ ca_cert_data()
              #@ end
```
Let's update the supply chain next...

```editor:select-matching-text
file: custom-supply-chain/supply-chain.yaml
text: "  - name: config-writer"
after: 9
```
```editor:replace-text-selection
file: custom-supply-chain/supply-chain.yaml
text: |2

    - name: config-writer
      configs:
      - name: config
        resource: app-config
      params:
      - name: serviceAccount
        value: default
      - name: registry
        value:
          ca_cert_data: ""
          repository: {{ ENV_CONTAINER_REGISTRY_REPOSITORY }}
          server: {{ ENV_CONTAINER_REGISTRY_HOSTNAME }}
      templateRef:
        kind: ClusterTemplate
        name: custom-config-writer-template-{{ session_namespace }}
```
