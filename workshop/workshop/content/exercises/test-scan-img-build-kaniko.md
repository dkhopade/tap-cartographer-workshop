#### ClusterImageTemplate - Kaniko Image Builder

In addition to `kpack`, we want our custom supply chain to provide a solution that builds a container image based on a `Dockerfile`. 

**[kaniko](https://github.com/GoogleContainerTools/kaniko)** is the solution we'll use for it. It's a tool to build container images from a Dockerfile, inside a container or Kubernetes cluster. 

Because there is **no official Kubernetes CRD** for it available, we will use **Tekton** to run it in a container.

Let's first create the skeleton for our new `ClusterImageTemplate`. As you can see we also added an additional ytt function that generates the context sub-path out of the Git url and revision which we need for our custom implementation.

```editor:append-lines-to-file
file: custom-supply-chain/custom-image-kaniko-template.yaml
text: |2
  apiVersion: carto.run/v1alpha1
  kind: ClusterImageTemplate
  metadata:
    name: custom-image-kaniko-template-{{ session_namespace }}
  spec:
    healthRule:
      singleConditionType: Ready
    imagePath: ""
    params:
    - default: default
      name: serviceAccount
    - default: {}
      name: registry
    - default: ./Dockerfile
      name: dockerfile
    - default: ./
      name: docker_build_context
    - default: []
      name: docker_build_extra_args
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

      #@ def param(key):
      #@   if not key in data.values.params:
      #@     return None
      #@   end
      #@   return data.values.params[key]
      #@ end

      #@ def image():
      #@   return "/".join([
      #@    data.values.params.registry.server,
      #@    data.values.params.registry.repository,
      #@    "-".join([
      #@      data.values.workload.metadata.name,
      #@      data.values.workload.metadata.namespace,
      #@    ])
      #@   ])
      #@ end

      #@ def ca_cert_data():
      #@   if "ca_cert_data" not in param("registry"):
      #@     return ""
      #@   end
      #@
      #@   return param("registry")["ca_cert_data"]
      #@ end
```
##### Create Kaniko ClusterImageTemplate to the Supply Chain

We want to implement in a way that both **kpack** and **kaniko** methods can be used in a single supply chain. These will be switched based on a `selector` - in our case, the selector will test to see if `dockerfile` parameter in the `Workload` is set or not.

We will use this one `kaniko-template` that is provided as part of OOTB templates package.

```editor:select-matching-text
file: custom-supply-chain/supply-chain.yaml
text: "  - name: image-builder"
after: 11
```

```editor:replace-text-selection
file: custom-supply-chain/supply-chain.yaml
text: |2
    - name: image-builder
      templateRef:
        kind: ClusterImageTemplate
        options:
        - name: kpack-template 
          selector:
            matchFields:
              - key: spec.params[?(@.name=="dockerfile")]
                operator: DoesNotExist
        - name: custom-image-kaniko-template-{{ session_namespace }} 
          selector:
            matchFields:
              - key: spec.params[?(@.name=="dockerfile")]
                operator: Exists
      sources:
      - name: source
        resource: source-scanner
      params:
      - name: registry
        value:
          server: {{ ENV_CONTAINER_REGISTRY_HOSTNAME }}
          repository: {{ ENV_CONTAINER_REGISTRY_REPOSITORY }}
      - name: dockerfile
        default: ""
```

This is possible via the `spec.resources[*].templateRef.options`. The documentation is available here:
```dashboard:reload-dashboard
name: Cartographer Docs
url: https://cartographer.sh/docs/v0.4.0/reference/workload/#clustersupplychain
```

Due to the complexity, here is the **ClusterRunTemplate which you have to reference from a Runnable** that you have to define with its inputs **in the ClusterImageTemplate**. 

You can for sure also try to implement it yourself but please with the name **"custom-kaniko-run-template-{{ session_namespace }}"**.
```editor:append-lines-to-file
file: custom-supply-chain/custom-kaniko-run-template.yaml
text: |2
    apiVersion: carto.run/v1alpha1
    kind: ClusterRunTemplate
    metadata:
      name: custom-kaniko-run-template-{{ session_namespace }}
    spec:
      healthRule:
        singleConditionType: Ready
      outputs:
        image-ref: .status.taskResults[?(@.name=="image-ref")].value
      template:
        apiVersion: tekton.dev/v1beta1
        kind: TaskRun
        metadata:
          generateName: $(runnable.metadata.name)$-
        spec:
          taskSpec:
            results:
            - name: image-ref
            steps:
            - name: download-and-unpack-tarball
              image: alpine
              script: |-
                set -xv
                cd `mktemp -d`
                wget -O- $(runnable.spec.inputs.source-url)$ | tar xvz -m -C /source
              volumeMounts:
              - name: source-dir
                mountPath: /source
            - image: gcr.io/kaniko-project/executor:latest
              name: build-container-and-push
              args:
              - --dockerfile=$(runnable.spec.inputs.dockerfile)$
              - --context=dir:///source
              - --destination=$(runnable.spec.inputs.image)$
              - --digest-file=/tekton/results/digest-file
              securityContext:
                runAsUser: 0
              volumeMounts:
                - name: source-dir
                  mountPath: /source
                - name: kaniko-secret
                  mountPath: /kaniko/.docker
            - name: write-image-ref
              image: alpine
              script: |
                image=$(runnable.spec.inputs.image)$
                digest_path=/tekton/results/digest-file
                digest="$(cat ${digest_path})"

                echo -n "${image}@${digest}" | tee /tekton/results/image-ref
            volumes:
              - name: source-dir
                emptyDir: {}
              - name: kaniko-secret
                secret:
                  secretName: registry-credentials
                  items:
                    - key: .dockerconfigjson
                      path: config.json
```

Let's now add a `Runnable` task to our **Kaniko** `ClusterImageTemaplate` that will be used to build the image from `Dockerfile`

```editor:append-lines-to-file
file: custom-supply-chain/custom-image-kaniko-template.yaml
text: |2
      ---
      apiVersion: carto.run/v1alpha1
      kind: Runnable
      metadata:
        name: #@ data.values.workload.metadata.name + "-kaniko"
        labels:
          app.kubernetes.io/component: image
          app.kubernetes.io/part-of: #@ data.values.workload.metadata.name
      spec:
        runTemplateRef:
          name: custom-kaniko-run-template-{{ session_namespace }}
        healthRule:
          singleConditionType: Ready

        inputs:
          image: #@ image()
          dockerfile: #@ data.values.params.dockerfile
          source-url: #@ data.values.sources.source.url
          source-revision: #@ data.values.sources.source.revision
```

Let's get the reference details of the image that we just built so that we can get the name of the image.

```editor:select-matching-text
file: custom-supply-chain/custom-image-kaniko-template.yaml
text: "  imagePath: \"\""
```
```editor:replace-text-selection
file: custom-supply-chain/custom-image-kaniko-template.yaml
text: |2
    imagePath: .status.outputs.image-ref
```
