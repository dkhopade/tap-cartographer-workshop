###### Out of the Box Templates Overview

In Cartographer, a supply chain is defined as a directed acyclic graph of resources choreographed by the Cartographer controllers, with the definition of the shape of such resources defined by templates. TAP installation ships with a package that contains a series of reusable templates used by:

- Out of the Box Supply Chain Basic
- Out of the Box Supply Chain with Testing
- Out of the Box Supply Chain with Testing and Scanning

As a prerequisite of the Out of the Box Supply Chains, you must install this package to have Workloads delivered properly.

You can read more about OOTB Templates
```dashboard:open-url
url: https://docs.vmware.com/en/VMware-Tanzu-Application-Platform/1.2/tap/GUID-scc-ootb-templates.html
```
..and Cartographer Tempalting capabilities

```dashboard:open-url
https://cartographer.sh/docs/v0.4.0/templating/
```
### Supply Chain Implementation 

We will begin implementing the first of our series of resources that are responsible for bringing the application to a deliverable state.

Those resources are specified via **Templates**. Each template acts as a wrapper for existing Kubernetes resources and allows them to be used with Cartographer. This way, **Cartographer doesn’t care what tools are used under the hood**.

There are currently four different types of templates that can be use in a Cartographer supply chain: **ClusterSourceTemplate**, **ClusterImageTemplate**, **ClusterConfigTemplate**, and the generic **ClusterTemplate**.


A **ClusterConfigTemplate** instructs the supply chain on how to instantiate a Kubernetes object like a `ConfigMap` that knows how to make Kubernetes configurations available to further resources in the chain.

For the deployment of our application, we will use Knative, which is a serverless application runtime for Kubernetes with e.g. auto-scaling capabilities to save costs.
```editor:append-lines-to-file
file: custom-supply-chain/custom-config-provider-template.yaml
text: |2
  apiVersion: carto.run/v1alpha1
  kind: ClusterConfigTemplate
  metadata:
    name: custom-config-provider-template-{{ session_namespace }}
  spec:
    configPath: .data.delivery
    ytt: |
      #@ load("@ytt:data", "data")
      #@ load("@ytt:yaml", "yaml")

      #@ def delivery():
      apiVersion: serving.knative.dev/v1
      kind: Service
      metadata:
        name: #@ data.values.workload.metadata.name
      spec:
        template: 
          spec:
            containers:
            - image: #@ data.values.image
              name: workload
              ports:
              - containerPort: 8080
      #@ end

      ---
      apiVersion: v1
      kind: ConfigMap
      metadata:
        name: #@ data.values.workload.metadata.name
      data:
        delivery: #@ yaml.encode(delivery())
```
The ClusterConfigTemplate requires definition of a `spec.configPath` and it will update its status to emit a config value, which is a reflection of the value at the path on the created object. 

For our simple example, we use it to provide the deployment configuration of our application to the last step of our Supply Chain. We therefore have to consume the outputs of our `ClusterImageTemplate` resource by referencing it via the `spec.resources[*].images` field of our Supply Chain definition.
Let's add this as a resource/reference to our supply chain:
```editor:append-lines-to-file
file: custom-supply-chain/supply-chain.yaml
text: |2

    - name: app-config
      templateRef:
        kind: ClusterConfigTemplate
        name: custom-config-provider-template-{{ session_namespace }}
      images:
      - resource: image-builder
        name: image

```

The detailed specifications of the ClusterConfigTemplate can be found here: 
```dashboard:reload-dashboard
name: Cartographer Docs
url: https://cartographer.sh/docs/v0.4.0/reference/template/#clusterconfigtemplate
```
