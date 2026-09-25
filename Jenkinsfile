#!/usr/bin/env groovy

/* `buildPlugin` step provided by: https://github.com/jenkins-infra/pipeline-library */
buildPlugin(useContainerAgent: true, configurations: [
  [platform: 'linux', jdk: 25],
  [platform: 'windows', jdk: 21],
  // Jenkins 2.579 and later no longer ship Commons Lang 2 in core.
  [platform: 'linux', jdk: 21, jenkins: '2.582'],
])
