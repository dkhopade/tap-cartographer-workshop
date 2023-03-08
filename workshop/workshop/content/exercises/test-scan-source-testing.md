#### ClusterSourceTemplate - Source Testing

Since we want to enforce the source testing, we need to consider creating another `ClusterSourceTemplate` and add its reference as `source-tester` to the supply chain.
Lets add the template `ClusterSourceTemplate`.

Create a template now:
```editor:append-lines-to-file
file: custom-supply-chain/custom-source-tester-template.yaml
text: |2
  apiVersion: carto.run/v1alpha1
  kind: ClusterSourceTemplate
  metadata:
    name: custom-source-tester-template-{{ session_namespace }}
  spec:
    healthRule:
      singleConditionType: Ready
    revisionPath: .status.outputs.revision
    urlPath: .status.outputs.url
    ytt: |
      #@ load("@ytt:data", "data")

      #@ def merge_labels(fixed_values):
      #@   labels = {}
      #@   if hasattr(data.values.workload.metadata, "labels"):
      #@     labels.update(data.values.workload.metadata.labels)
      #@   end
      #@   labels.update(fixed_values)
      #@   return labels
      #@ end

      ---
      apiVersion: carto.run/v1alpha1
      kind: Runnable
      metadata:
        name: #@ data.values.workload.metadata.name
        labels: #@ merge_labels({ "app.kubernetes.io/component": "test" })
      spec:
        #@ if/end hasattr(data.values.workload.spec, "serviceAccountName"):
        serviceAccountName: #@ data.values.workload.spec.serviceAccountName

        runTemplateRef:
          name: tekton-source-pipelinerun
          kind: ClusterRunTemplate

        selector:
          resource:
            apiVersion: tekton.dev/v1beta1
            kind: Pipeline
          matchingLabels:
            apps.tanzu.vmware.com/pipeline: test

        inputs:
          source-url: #@ data.values.source.url
          source-revision: #@ data.values.source.revision
```
Add reference to Supply Chain.

```editor:select-matching-text
file: custom-supply-chain/supply-chain.yaml
text: "  #source-tester-TBC"
```

```editor:replace-text-selection
file: custom-supply-chain/supply-chain.yaml
text: |2

    - name: source-tester
      sources:
      - name: source
        resource: source-provider
      templateRef:
        kind: ClusterSourceTemplate
        name: custom-source-tester-template-{{ session_namespace }}

```
