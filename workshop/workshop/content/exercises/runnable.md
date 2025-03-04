A **Runnable** object declares the intention of having immutable objects submitted to Kubernetes according to a template ( via ClusterRunTemplate) whenever any of the inputs passed to it changes. i.e., it allows us to provide a mutable spec that drives the creation of immutable objects whenever that spec changes.

A **ClusterRunTemplate** differs from supply chain templates in many aspects (e.g. cannot be referenced directly by a ClusterSupplyChain, **outputs** provide a free-form way of exposing any form of results). It defines how an immutable object should be stamped out based on data provided by a **Runnable**.

Sounds like we've found a way to stamp out our immutable **TaskRuns** and **PipelineRuns**.
```editor:select-matching-text
file: custom-supply-chain/custom-config-writer-template.yaml
text: "  ytt: \"\""
```
```editor:replace-text-selection
file: custom-supply-chain/custom-config-writer-template.yaml
text: |2
    ytt: |
      #@ load("@ytt:data", "data")
      #@ load("@ytt:json", "json")
      #@ load("@ytt:base64", "base64")
      ---
      apiVersion: carto.run/v1alpha1
      kind: Runnable
      metadata:
        name: #@ data.values.workload.metadata.name + "-config-writer"
        labels:
          app.kubernetes.io/component: runnable
          app.kubernetes.io/part-of: #@ data.values.workload.metadata.name
      spec:
        runTemplateRef:
          name: custom-run-template-{{ session_namespace }}

        inputs:
          git_repository: #@ data.values.params.git_repository
          git_files: #@ data.values.config
```

```editor:append-lines-to-file
file: custom-supply-chain/custom-run-template.yaml
text: |2
  apiVersion: carto.run/v1alpha1
  kind: ClusterRunTemplate
  metadata:
    name: custom-run-template-{{ session_namespace }}
  spec:
    outputs: {}
    template: {}
```
`spec.outputs` provides a free-form way of exposing any form of results from what has been run to the status of the Runnable object (as opposed to typed “source”, “image”, and “config” from supply chains). Because we don't have the need to expose any outputs to subsequent steps and therefore, use a ClusterTemplate, we don't have to specify it.

We'll now configure a TaskRun to push the deployment configuration to a Git repository.
```editor:select-matching-text
file: custom-supply-chain/custom-run-template.yaml
text: "  outputs: {}"
after: 1
```
```editor:replace-text-selection
file: custom-supply-chain/custom-run-template.yaml
text: |2
    template:
      apiVersion: tekton.dev/v1beta1
      kind: TaskRun
      metadata:
        generateName: $(runnable.metadata.name)$-
      spec:
        taskRef:
          name: git-cli
        workspaces:
        - name: basic-auth
          secret:
            secretName: tekton-basic-access-auth
        - name: source
          emptyDir: {}
        params:
          - name: GIT_USER_NAME
            value: {{ session_namespace }}
          - name: GIT_USER_EMAIL
            value: {{ session_namespace }}@vmware.com
          - name: GIT_SCRIPT
            value: |
              if git clone --depth 1 -b main "$(runnable.spec.inputs.git_repository)$" ./repo; then
                cd ./repo
              else
                mkdir ./repo && cd ./repo
                git init 
                git remote add origin "$(runnable.spec.inputs.git_repository)$"                
                git checkout -b main
              fi

              mkdir -p config && rm -rf config/*
              cd config

              echo "$(runnable.spec.inputs.git_files)$" > delivery.yml

              git add delivery.yml
              git commit -m "Update deployment configuration"
              git push origin main
```

The detailed specifications of the Runnable and ClusterRunTemplate can be found here: 
```dashboard:reload-dashboard
name: Cartographer Docs
url: https://cartographer.sh/docs/v0.4.0/reference/runnable/
```

Let's now apply our resources to the cluster as a group of resources via the kapp CLI and see via the commercial Supply Chain Choreographer UI plugin and the following commands whether everything works as expected.
```terminal:execute
command: |
  kapp deploy -a custom-supply-chain -f custom-supply-chain -y --dangerous-scope-to-fallback-allowed-namespaces
  kubectl apply -f workloads/workload.yaml
clear: true
```
That should now kick off the build on Tanzu Build Server. We'll use `kp list` command from the Carvel toolchain
```terminal:execute
command: watch kp build list -n {{ session_namespace}}
clear: true
session: 2
```
Let's see what the build looks like.
```terminal:execute
command: kp build logs tanzu-java-web-app
clear: true
session: 1
```
Now let's see what it looks like in TAP GUI
```dashboard:reload-dashboard
name: Cartographer Docs
url: http://tap-gui.{{ ENV_TAP_INGRESS }}/supply-chain/host/{{ session_namespace }}/tanzu-java-web-app
```
```terminal:execute
command: kubectl describe clustersupplychain custom-supplychain-{{ session_namespace }}
clear: true
```
```terminal:execute
command: kubectl tree workload tanzu-java-web-app
clear: true
```
```terminal:execute
command: kubectl describe workload tanzu-java-web-app
clear: true
```
```terminal:execute
command: tanzu apps workload get tanzu-java-web-app
clear: true
```
Let's tail the `Workload` deployment to see the progress.
**NOTE:** Press _CTRL+C_ before and after you click on the following link

```terminal:execute
command: tanzu apps workload tail tanzu-java-web-app
clear: true
session: 2
```

The following diagram (which is available in the documentation) of a similar ClusterSupplyChain shows the relationship between all those different resources.
![](../images/supplychain.png)