#### ClusterSourceTemplate - Image Scanning

We also have a requirement to scan the container image that was produced in a previous build step. We need to create `ClusterImageTemplate` and add its reference as `image-scanner` to the supply chain.
Lets understand what this section is. We are using a scan policy (`ScanPolicy`) named `lax-scan-policy` that was created as a custom policy thats different than what we have used on Source Scan earlier. Also, the scanning template (`ScanTemplate`) named `private-image-scan-template` is specific template to scan container images for container level CVEs via `scanning.apps.tanzu.vmware.com/v1beta1` that was already deployed on this workshop & the TAP cluster by OOTB supply chain. We can change the policies and templates with our custom ones. We will explain this more during the image scanning section when we add it to the supply chain.

Create a template now:
```editor:append-lines-to-file
file: custom-supply-chain/custom-image-scanner-template.yaml
text: |2
  apiVersion: carto.run/v1alpha1
  kind: ClusterImageTemplate
  metadata:
    name: custom-image-scanner-template-{{ session_namespace }}
  spec:
    healthRule:
      singleConditionType: Succeeded
    imagePath: .status.compliantArtifact.registry.image
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
      kind: ImageScan
      metadata:
        name: #@ data.values.workload.metadata.name
        labels: #@ merge_labels({ "app.kubernetes.io/component": "image-scan" })
      spec:
        registry:
          image: #@ data.values.image
        scanTemplate: #@ data.values.params.scanning_image_template
        scanPolicy: #@ data.values.params.scanning_image_policy
```
Lets add a reference to the Supply Chain.
```editor:select-matching-text
file: custom-supply-chain/supply-chain.yaml
text: "  #image-scanner-TBC"
```
```editor:replace-text-selection
file: custom-supply-chain/supply-chain.yaml
text: |2

    - name: image-scanner
      images:
      - name: image
        resource: image-builder
      params:
      - default: lax-scan-policy
        name: scanning_image_policy
      - default: private-image-scan-template
        name: scanning_image_template
      templateRef:
        kind: ClusterImageTemplate
        name: custom-image-scanner-template-{{ session_namespace }}

```
