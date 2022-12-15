#### ClusterConfigTemplate - Conventions Service

This session uses the RegistryOps model. Our first session we used a GitOps model. To proceed with using the RegistryOps model let's add `ClusterConfigTemplate` that will create a new `PodIntent` object that will be consumed by TAP's **Conventions Service**

Step1: In this step we will add `ClusterConfigTemplate` for `PodIntent` and its reference to the Supply Chain.
Lets add a template:
```editor:append-lines-to-file
file: custom-supply-chain/custom-convention-template.yaml
text: |2
    apiVersion: carto.run/v1alpha1
    kind: ClusterConfigTemplate
    metadata:
      name: custom-convention-template-{{ session_namespace }}
    spec:
      configPath: .status.template
      healthRule:
        singleConditionType: Ready
      params:
      - default: default
        name: serviceAccount
      ytt: |
        #@ load("@ytt:data", "data")

        #@ def param(key):
        #@   if not key in data.values.params:
        #@     return None
        #@   end
        #@   return data.values.params[key]
        #@ end

        #@ def merge_labels(fixed_values):
        #@   labels = {}
        #@   if hasattr(data.values.workload.metadata, "labels"):
        #@     labels.update(data.values.workload.metadata.labels)
        #@   end
        #@   labels.update(fixed_values)
        #@   return labels
        #@ end

        #@ def build_fixed_annotations():
        #@   fixed_annotations = { "developer.conventions/target-containers": "workload" }
        #@   if param("debug"):
        #@     fixed_annotations["apps.tanzu.vmware.com/debug"] = param("debug")
        #@   end
        #@   if param("live-update"):
        #@     fixed_annotations["apps.tanzu.vmware.com/live-update"] = param("live-update")
        #@   end
        #@   return fixed_annotations
        #@ end

        #@ def merge_annotations(fixed_values):
        #@   annotations = {}
        #@   if hasattr(data.values.workload.metadata, "annotations"):
        #@     # DEPRECATED: remove in a future release
        #@     annotations.update(data.values.workload.metadata.annotations)
        #@   end
        #@   if type(param("annotations")) == "dict" or type(param("annotations")) == "struct":
        #@     annotations.update(param("annotations"))
        #@   end
        #@   annotations.update(fixed_values)
        #@   return annotations
        #@ end
        ---
        apiVersion: conventions.apps.tanzu.vmware.com/v1alpha1
        kind: PodIntent
        metadata:
          name: #@ data.values.workload.metadata.name
          labels: #@ merge_labels({ "app.kubernetes.io/component": "intent" })
        spec:
          serviceAccountName: #@ data.values.params.serviceAccount
          template:
            metadata:
              annotations: #@ merge_annotations(build_fixed_annotations())
              labels: #@ merge_labels({ "app.kubernetes.io/component": "run", "carto.run/workload-name": data.values.workload.metadata.name })
            spec:
              serviceAccountName: #@ data.values.params.serviceAccount
              containers:
                - name: workload
                  image: #@ data.values.image
                  securityContext:
                    runAsUser: 1000
                  #@ if hasattr(data.values.workload.spec, "env"):
                  env:
                    #@ for var in data.values.workload.spec.env:
                    - name: #@ var.name
                      #@ if/end hasattr(var, "value"):
                      value: #@ var.value
                      #@ if/end hasattr(var, "valueFrom"):
                      valueFrom: #@ var.valueFrom
                    #@ end
                  #@ end
                  #@ if/end hasattr(data.values.workload.spec, "resources"):
                  resources: #@ data.values.workload.spec["resources"]
```
Now add the reference to the supply chain:
```editor:select-matching-text
file: custom-supply-chain/supply-chain.yaml
text: "  #config-provider-TBC"
```
```editor:replace-text-selection
file: custom-supply-chain/supply-chain.yaml
text: |2

    - name: config-provider
      images:
      - name: image
        resource: image-scanner
      params:
      - name: serviceAccount
        value: default
      templateRef:
        kind: ClusterConfigTemplate
        name: custom-convention-template-{{ session_namespace }}
```


Step2: In this step we will add `ClusterConfigTemplate` and the `ClusterTemplate` templates and the reference to the Supply Chain for App-Configs.
Lets add a template:
```editor:append-lines-to-file
file: custom-supply-chain/custom-app-config-template.yaml
text: |2
    apiVersion: carto.run/v1alpha1
    kind: ClusterConfigTemplate
    metadata:
      name: custom-app-config-template-{{ session_namespace }}
    spec:
      configPath: .data
      healthRule:
        alwaysHealthy: {}
      ytt: |
        #@ load("@ytt:data", "data")
        #@ load("@ytt:yaml", "yaml")
        #@ load("@ytt:json", "json")

        #@ def get_claims_extension():
        #@   claims_extension_key = "serviceclaims.supplychain.apps.x-tanzu.vmware.com/extensions"
        #@   if not hasattr(data.values.workload.metadata, "annotations") or not hasattr(data.values.workload.metadata.annotations, claims_extension_key):
        #@     return None
        #@   end
        #@
        #@   extension = json.decode(data.values.workload.metadata.annotations[claims_extension_key])
        #@
        #@   spec_extension = extension.get('spec')
        #@   if spec_extension == None:
        #@     return None
        #@   end
        #@
        #@   return spec_extension.get('serviceClaims')
        #@ end

        #@ def merge_claims_extension(claim, claims_extension):
        #@   if claims_extension == None:
        #@     return claim.ref
        #@   end
        #@   extension = claims_extension.get(claim.name)
        #@   if extension == None:
        #@      return claim.ref
        #@   end
        #@   extension.update(claim.ref)
        #@   return extension
        #@ end

        #@ def param(key):
        #@   if not key in data.values.params:
        #@     return None
        #@   end
        #@   return data.values.params[key]
        #@ end

        #@ def merge_labels(fixed_values):
        #@   labels = {}
        #@   if hasattr(data.values.workload.metadata, "labels"):
        #@     labels.update(data.values.workload.metadata.labels)
        #@   end
        #@   labels.update(fixed_values)
        #@   return labels
        #@ end

        #@ def merge_annotations(fixed_values):
        #@   annotations = {}
        #@   if hasattr(data.values.workload.metadata, "annotations"):
        #@     # DEPRECATED: remove in a future release
        #@     annotations.update(data.values.workload.metadata.annotations)
        #@   end
        #@   if type(param("annotations")) == "dict" or type(param("annotations")) == "struct":
        #@     annotations.update(param("annotations"))
        #@   end
        #@   annotations.update(fixed_values)
        #@   return annotations
        #@ end

        #@ def delivery():
        apiVersion: serving.knative.dev/v1
        kind: Service
        metadata:
          name: #@ data.values.workload.metadata.name
          #! annotations NOT merged because knative annotations would be invalid here
          labels: #@ merge_labels({ "app.kubernetes.io/component": "run", "carto.run/workload-name": data.values.workload.metadata.name })
        spec:
          template: #@ data.values.config
        #@ end

        #@ def claims():
        #@ claims_extension = get_claims_extension()
        #@ for s in data.values.workload.spec.serviceClaims:
        #@ if claims_extension == None or claims_extension.get(s.name) == None:
        ---
        apiVersion: servicebinding.io/v1alpha3
        kind: ServiceBinding
        metadata:
          name: #@ data.values.workload.metadata.name + '-' + s.name
          annotations: #@ merge_annotations({})
          labels: #@ merge_labels({ "app.kubernetes.io/component": "run", "carto.run/workload-name": data.values.workload.metadata.name })
        spec:
          name: #@ s.name
          service: #@ s.ref
          workload:
            apiVersion: serving.knative.dev/v1
            kind: Service
            name: #@ data.values.workload.metadata.name
        #@ else:
        ---
        apiVersion: services.apps.tanzu.vmware.com/v1alpha1
        kind: ResourceClaim
        metadata:
          name: #@ data.values.workload.metadata.name + '-' + s.name
          annotations: #@ merge_annotations({})
          labels: #@ merge_labels({ "app.kubernetes.io/component": "run", "carto.run/workload-name": data.values.workload.metadata.name })
        spec:
          ref: #@ merge_claims_extension(s, claims_extension)
        ---
        apiVersion: servicebinding.io/v1alpha3
        kind: ServiceBinding
        metadata:
          name: #@ data.values.workload.metadata.name + '-' + s.name
          annotations: #@ merge_annotations({})
          labels: #@ merge_labels({ "app.kubernetes.io/component": "run", "carto.run/workload-name": data.values.workload.metadata.name })
        spec:
          name: #@ s.name
          service:
            apiVersion: services.apps.tanzu.vmware.com/v1alpha1
            kind: ResourceClaim
            name: #@ data.values.workload.metadata.name + '-' + s.name
          workload:
            apiVersion: serving.knative.dev/v1
            kind: Service
            name: #@ data.values.workload.metadata.name
        #@ end
        #@ end
        #@ end

        ---
        apiVersion: v1
        kind: ConfigMap
        metadata:
          name: #@ data.values.workload.metadata.name
          labels: #@ merge_labels({ "app.kubernetes.io/component": "config" })
        data:
          delivery.yml: #@ yaml.encode(delivery())
          #@ if hasattr(data.values.workload.spec, "serviceClaims") and len(data.values.workload.spec.serviceClaims):
          serviceclaims.yml: #@ yaml.encode(claims())
          #@ end
```

Now let's update the `App-Config` section of the supply chain so that it reads from `config-provider`

```editor:select-matching-text
file: custom-supply-chain/supply-chain.yaml
text: "  - name: app-config"
after: 6
```
```editor:replace-text-selection
file: custom-supply-chain/supply-chain.yaml
text: |2
    - name: app-config
      configs:
      - name: config
        resource: config-provider
      templateRef:
        kind: ClusterConfigTemplate
        name: custom-app-config-template-{{ session_namespace }}
```
