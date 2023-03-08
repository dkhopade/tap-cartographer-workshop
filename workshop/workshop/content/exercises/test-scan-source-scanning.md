#### ClusterSourceTemplate - Source Scanning

We have a requirement to scan our source code for any coding or dependency level CVEs. Let's add those pieces together to get it done.
To achieve this, we will need to create a `ClusterSourceTemplate` that uses `SourceScan`, and then add the reference of it to the Supply Chain. 
Lets understand what this section is. We are using a scan policy `ScanPolicy` named `scan-policy` and the scanning template `ScanTemplate` named `blob-source-scan-template` via `scanning.apps.tanzu.vmware.com/v1beta1` that was already deployed on this workshop & the TAP cluster by OOTB supply chain. We can change the policies and templates with our custom ones. We will explain this more during the image scanning section when we add it to the supply chain.

Create a template now:
```editor:append-lines-to-file
file: custom-supply-chain/custom-source-scanner-template.yaml
text: |2
  apiVersion: carto.run/v1alpha1
  kind: ClusterSourceTemplate
  metadata:
    name: custom-source-scanner-template-{{ session_namespace }}
  spec:
    healthRule:
      singleConditionType: Succeeded
    revisionPath: .status.compliantArtifact.blob.revision
    urlPath: .status.compliantArtifact.blob.url
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
      apiVersion: scanning.apps.tanzu.vmware.com/v1beta1
      kind: SourceScan
      metadata:
        name: #@ data.values.workload.metadata.name
        labels: #@ merge_labels({ "app.kubernetes.io/component": "source-scan" })
      spec:
        blob:
          url: #@ data.values.source.url
          revision: #@ data.values.source.revision
        scanTemplate: #@ data.values.params.scanning_source_template
        scanPolicy: #@ data.values.params.scanning_source_policy
```
Lets add a reference to the Supply Chain.
```editor:select-matching-text
file: custom-supply-chain/supply-chain.yaml
text: "  #source-scanner-TBC"
```

```editor:replace-text-selection
file: custom-supply-chain/supply-chain.yaml
text: |2

    - name: source-scanner
      params:
      - default: scan-policy
        name: scanning_source_policy
      - default: blob-source-scan-template
        name: scanning_source_template
      sources:
      - name: source
        resource: source-tester
      templateRef:
        kind: ClusterSourceTemplate
        name: custom-source-scanner-template-{{ session_namespace }}
```

As with our custom supply chain, the **next step** is responsible for the building of a container image out of the provided source code by the first step. 
