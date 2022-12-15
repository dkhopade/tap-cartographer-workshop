##### Deploy Workload

To test the supply chain with `Dockerfile` capability let's create a new `Workload` from previously created `tanzu-java-web-app` with a few more details.

```editor:append-lines-to-file
file: ~/workloads/workload-docker.yaml
text: |2
  apiVersion: carto.run/v1alpha1
  kind: Workload
  metadata:
    labels:
      app.kubernetes.io/part-of: tanzu-java-web-app-docker
      apps.tanzu.vmware.com/workload-type: web
      apps.tanzu.vmware.com/has-tests: "true"
      end2end.link/workshop-session: {{ session_namespace }}
      end2end.link/is-custom: "true" 
    name: tanzu-java-web-app-docker
  spec:
    params:
    - name: dockerfile
      value: Dockerfile
    source:
      git:
        ref:
          branch: dockerfile
        url: https://github.com/sample-accelerators/tanzu-java-web-app.git
```

Here we made a few changes `Workload`. Since we want to deploy using `Dockerfile` we need to specify that in `spec.params` 

```editor:select-matching-text
file: ~/workloads/workload-docker.yaml
text: "spec:"
before: 0
after: 3
```

We also update the git source to a branch of the **tanzu-java-web-app** where we added a simple `Dockerfile`

```editor:select-matching-text
file: ~/workloads/workload-docker.yaml
text: "branch: dockerfile"
```

Next, let's apply the workload
```terminal:execute
command: |
  kubectl apply -f ~/workloads/workload-docker.yaml
clear: true
```
... and then we are able to see via the commercial Supply Chain Choreographer UI plugin and the following commands whether everything works as expected.

```dashboard:reload-dashboard
name: TAP Gui for Supply Chain
url: http://tap-gui.{{ ENV_TAP_INGRESS }}/supply-chain/host/{{ session_namespace }}/tanzu-java-web-app-docker
```
```terminal:execute
command: kubectl describe clustersupplychain custom-supplychain-{{ session_namespace }}
clear: true
```
```terminal:execute
command: kubectl tree workload tanzu-java-web-app-docker
clear: true
```
```terminal:execute
command: tanzu apps workload get tanzu-java-web-app-docker
clear: true
```
```execute-2
tanzu apps workload tail tanzu-java-web-app-docker
```
Now that we deployed our workload, let's confirm by going to the following URL
```dashboard:create-dashboard
name: tanzu-java-web-app-docker
url: http://tanzu-java-web-app-docker.{{ session_namespace }}.{{ ENV_TAP_INGRESS }}
```

That's it! You have built your first custom supply chain, and hopefully, many more will follow.
Let's delete the resources that we applied to the cluster.
```terminal:execute
command: |
  kubectl delete -f ~/workload-docker.yaml
  kapp delete -a custom-supply-chain -y
clear: true
```